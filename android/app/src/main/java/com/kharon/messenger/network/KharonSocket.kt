package com.kharon.messenger.network

import com.kharon.messenger.crypto.CryptoManager
import com.kharon.messenger.crypto.DecryptResult
import com.kharon.messenger.model.IncomingMessage
import com.kharon.messenger.model.ReceptionMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import com.kharon.messenger.util.KLog

// ─── Connection state ─────────────────────────────────────────────────────────

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting   : ConnectionState()
    object Connected    : ConnectionState()
    data class Error(val reason: String) : ConnectionState()
}

// ─── Единый поток событий ─────────────────────────────────────────────────────

sealed class SocketEvent {
    data class Message(val msg: IncomingMessage)  : SocketEvent()
    data class ReadReceipt(val msgId: String)     : SocketEvent()
    data class Cancelled(val msgId: String)       : SocketEvent()
    data class QueueEnd(val mode: ReceptionMode)  : SocketEvent()
    data class Welcome(val peersOnline: Int)      : SocketEvent()
}

// ─── KharonSocket ─────────────────────────────────────────────────────────────

@Singleton
class KharonSocket @Inject constructor(
    private val crypto: CryptoManager,
    private val config: SocketConfig,
) {
    // Thread-safe коллекция для дедупликации
    private val seenIds          = Collections.synchronizedSet(LinkedHashSet<String>())
    private val pendingByContact = java.util.concurrent.ConcurrentHashMap<String, ArrayDeque<SocketEvent.Message>>()
    private val activeChats      = Collections.synchronizedSet(mutableSetOf<String>())

    // Scope живёт пока Singleton — отменяется только при terminateAll
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // AtomicReference для thread-safe доступа к сокету
    private val socketRef = AtomicReference<WebSocket?>(null)

    // AtomicBoolean для флагов
    private val isShutdown  = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)

    private var myPubKey    = ""
    private var currentMode = ReceptionMode.LIVE

    @Volatile private var backoffMs = 1_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0,  TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .certificatePinner(
            CertificatePinner.Builder()
                .add("kharon-messenger.duckdns.org",
                    "sha256/mrOaIg2JbLnqpoEdbcazxo7RXaR8k6gWoYynbTcxZ8U=")
                .build()
        )
        .build()

    // ─── Public flows ──────────────────────────────────────────────────────────

    private val _state  = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

    // ─── Connect ───────────────────────────────────────────────────────────────

    fun connect(pubKey: String, mode: ReceptionMode) {
        if (pubKey.isEmpty()) return
        KLog.socket("connect pubKey=${pubKey.take(8)}... mode=$mode")
        myPubKey    = pubKey
        currentMode = mode
        isShutdown.set(false)
        backoffMs = 1_000L
        doConnect()
    }

    private fun doConnect() {
        if (isShutdown.get()) return
        // Защита от двойного подключения
        if (!isConnecting.compareAndSet(false, true)) return

        // Закрываем старый сокет если есть
        socketRef.getAndSet(null)?.close(1000, "reconnecting")

        _state.value = ConnectionState.Connecting

        val request = Request.Builder().url(config.serverUrl).build()
        val ws = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                KLog.socket("onOpen — sending hello")
                socketRef.set(ws)
                isConnecting.set(false)
                backoffMs = 1_000L

                ws.send(JSONObject().apply {
                    put("type", "hello")
                    put("pubKey", myPubKey)
                    put("reception_mode", currentMode.name)
                }.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                KLog.socket("onClosed code=$code reason=$reason")
                isConnecting.set(false)
                _state.value = ConnectionState.Disconnected
                if (!isShutdown.get()) scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                KLog.err("onFailure: ${t.message}")
                isConnecting.set(false)
                _state.value = ConnectionState.Error(t.message ?: "failed")
                if (!isShutdown.get()) scheduleReconnect()
            }
        })
    }

    // ─── Message handling ──────────────────────────────────────────────────────

    private fun handleMessage(text: String) {
        val json = runCatching { JSONObject(text) }.getOrElse { return }
        val type = json.optString("type").ifEmpty { return }

        when (type) {
            "welcome" -> {
                val peers = json.optInt("peersOnline", 0)
                KLog.socket("welcome peers=$peers")
                _state.value = ConnectionState.Connected
                emit(SocketEvent.Welcome(peers))
            }

            "msg" -> {
                val from    = json.optString("from").ifEmpty { return }
                val payload = json.optString("payload").ifEmpty { return }
                val id      = json.optString("id").ifEmpty { return }

                // Replay protection — thread-safe
                if (!seenIds.add(id)) return
                if (seenIds.size > 200) {
                    synchronized(seenIds) {
                        if (seenIds.size > 200) {
                            val oldest = seenIds.take(100)
                            oldest.forEach { seenIds.remove(it) }
                        }
                    }
                }

                // Ключ получаем через CryptoManager — не храним secKey в памяти
                val secKey = crypto.getOrCreateKeyPair().secretKey
                val result = crypto.decrypt(payload, from, secKey)
                if (result is DecryptResult.Success) {
                    KLog.socket("msg decrypted from=${from.take(8)}... active=${activeChats.contains(from)}")
                    val event = SocketEvent.Message(
                        IncomingMessage(id, from, result.plaintext, System.currentTimeMillis())
                    )
                    if (activeChats.contains(from)) {
                        emit(event)
                    } else {
                        val buf = pendingByContact.getOrPut(from) { ArrayDeque() }
                        buf.addLast(event)
                        if (buf.size > 50) buf.removeFirst()
                    }
                }
            }

            "read_receipt" -> {
                val msgId = json.optString("msgId").ifEmpty { return }
                emit(SocketEvent.ReadReceipt(msgId))
            }

            "cancelled" -> {
                val msgId = json.optString("msgId").ifEmpty { return }
                emit(SocketEvent.Cancelled(msgId))
            }

            "queue_end" -> {
                emit(SocketEvent.QueueEnd(currentMode))
                // В PULSE режиме закрываемся после получения очереди
                if (currentMode != ReceptionMode.LIVE) {
                    disconnect()
                }
            }

            "pong" -> { /* keepalive ответ на наш ping */ }

            "error" -> {
                val msg = json.optString("message")
                android.util.Log.w("KharonSocket", "server error: $msg")
            }
        }
    }

    private fun emit(event: SocketEvent) {
        scope.launch { _events.emit(event) }
    }

    // ─── Send ──────────────────────────────────────────────────────────────────

    fun sendMessage(
        recipientPubKey: String,
        plaintext: String,
        messageId: String,
    ): Boolean {
        val ws = socketRef.get() ?: run { KLog.err("sendMessage: socket is null"); return false }
        if (_state.value !is ConnectionState.Connected) { KLog.err("sendMessage: not connected state=${_state.value}"); return false }
        if (recipientPubKey.isEmpty() || plaintext.isEmpty()) { KLog.err("sendMessage: empty params"); return false }

        val secKey    = crypto.getOrCreateKeyPair().secretKey
        KLog.socket("encrypt to=${recipientPubKey.take(8)}... secKey=${secKey.take(8)}...")
        val encrypted = crypto.encrypt(plaintext, recipientPubKey, secKey)
        KLog.socket("encrypt result: ${encrypted::class.simpleName}")

        return if (encrypted is com.kharon.messenger.crypto.EncryptResult.Success) {
            ws.send(JSONObject().apply {
                put("type",    "msg")
                put("to",      recipientPubKey)
                put("payload", encrypted.payload)
                put("id",      messageId)
            }.toString())
            true
        } else false
    }

    fun sendRead(senderPubKey: String, msgId: String) {
        if (senderPubKey.isEmpty() || msgId.isEmpty()) return
        socketRef.get()?.send(JSONObject().apply {
            put("type",  "read")
            put("to",    senderPubKey)
            put("msgId", msgId)
        }.toString())
    }

    fun cancelMessage(recipientPubKey: String, msgId: String) {
        if (recipientPubKey.isEmpty() || msgId.isEmpty()) return
        socketRef.get()?.send(JSONObject().apply {
            put("type",  "cancel")
            put("to",    recipientPubKey)
            put("msgId", msgId)
        }.toString())
    }

    // Вызывается из MainActivity ДО навигации — только помечает чат активным
    fun markChatActive(contactPubKey: String) {
        activeChats.add(contactPubKey)
        KLog.socket("markChatActive ${contactPubKey.take(8)}...")
    }

    // Вызывается из ChatViewModel.init() — забирает буфер
    fun registerChat(contactPubKey: String): List<SocketEvent.Message> {
        activeChats.add(contactPubKey)
        val pending = pendingByContact.remove(contactPubKey)?.toList() ?: emptyList()
        KLog.socket("registerChat ${contactPubKey.take(8)}... pending=${pending.size}")
        return pending
}

    fun unregisterChat(contactPubKey: String) {
        activeChats.remove(contactPubKey)
    }

    fun ping() {
        socketRef.get()?.send(JSONObject().put("type", "ping").toString())
    }

    // ─── Reconnect ─────────────────────────────────────────────────────────────

    private fun scheduleReconnect() {
        if (isShutdown.get() || currentMode != ReceptionMode.LIVE) return
        scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            doConnect()
        }
    }

    // ─── Disconnect ────────────────────────────────────────────────────────────

    fun disconnect() {
        isShutdown.set(true)
        isConnecting.set(false)
        socketRef.getAndSet(null)?.close(1000, "shutdown")
        _state.value = ConnectionState.Disconnected
    }
}

data class SocketConfig(val serverUrl: String)
