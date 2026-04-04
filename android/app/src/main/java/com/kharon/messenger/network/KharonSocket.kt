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

sealed class ConnectionState {
    object Disconnected : ConnectionState()
    object Connecting   : ConnectionState()
    object Connected    : ConnectionState()
    data class Error(val reason: String) : ConnectionState()
}

sealed class SocketEvent {
    data class Message(val msg: IncomingMessage)          : SocketEvent()
    data class ReadReceipt(val msgId: String)             : SocketEvent()
    data class Cancelled(val msgId: String)               : SocketEvent()
    data class QueueEnd(val mode: ReceptionMode)          : SocketEvent()
    data class Welcome(val peersOnline: Int)              : SocketEvent()
    data class ModeUpdate(val fromKey: String, val mode: String) : SocketEvent()
}

@Singleton
class KharonSocket @Inject constructor(
    private val crypto: CryptoManager,
    private val config: SocketConfig,
) {
    private val seenIds          = Collections.synchronizedSet(LinkedHashSet<String>())
    private val pendingByContact = java.util.concurrent.ConcurrentHashMap<String, ArrayDeque<SocketEvent.Message>>()
    private val activeChats      = Collections.synchronizedSet(mutableSetOf<String>())

    private val scope            = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socketRef        = AtomicReference<WebSocket?>(null)
    private val isShutdown       = AtomicBoolean(false)
    private val isConnecting     = AtomicBoolean(false)

    private var myPubKey    = ""
    private var currentMode = ReceptionMode.LIVE

    @Volatile private var backoffMs = 1_000L

    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()
    private val _unreadByContact = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val _unreadTotal = MutableStateFlow(0)
    val unreadTotal: StateFlow<Int> = _unreadTotal.asStateFlow()
    fun getUnreadCount(pubKey: String): Int = _unreadByContact[pubKey] ?: 0
    fun clearUnread(pubKey: String) {
        val was = _unreadByContact.remove(pubKey) ?: 0
        _unreadTotal.update { (it - was).coerceAtLeast(0) }
    }

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



    private val _state  = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 128)
    val events: SharedFlow<SocketEvent> = _events.asSharedFlow()

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
        if (!isConnecting.compareAndSet(false, true)) return
        socketRef.getAndSet(null)?.close(1000, "reconnecting")
        _state.value = ConnectionState.Connecting

        val request = Request.Builder().url(config.serverUrl).build()
        client.newWebSocket(request, object : WebSocketListener() {

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

            override fun onMessage(ws: WebSocket, text: String) = handleMessage(text)

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

    private fun handleMessage(text: String) {
        val json = runCatching { JSONObject(text) }.getOrElse { return }
        val type = json.optString("type").ifEmpty { return }

        when (type) {
            "welcome" -> {
                val peers = json.optInt("peersOnline", 0)
                KLog.socket("welcome peers=$peers")
                _state.value = ConnectionState.Connected
                emit(SocketEvent.Welcome(peers))
                // Сообщаем всем свой режим
                sendModeUpdate(currentMode)
            }

            "msg" -> {
                val from    = json.optString("from").ifEmpty { return }
                val payload = json.optString("payload").ifEmpty { return }
                val id      = json.optString("id").ifEmpty { return }

                if (!seenIds.add(id)) return
                if (seenIds.size > 200) {
                    synchronized(seenIds) {
                        if (seenIds.size > 200) {
                            seenIds.take(100).forEach { seenIds.remove(it) }
                        }
                    }
                }

                val secKey = crypto.getOrCreateKeyPair().secretKey
                val result = crypto.decrypt(payload, from, secKey)
                if (result is DecryptResult.Success) {
                    KLog.socket("msg decrypted from=${from.take(8)}... active=${activeChats.contains(from)}")
                    val event = SocketEvent.Message(
                        IncomingMessage(id, from, result.plaintext, System.currentTimeMillis())
                    )
                    // Считаем непрочитанные ВСЕГДА — и когда чат открыт и когда закрыт
                    _unreadByContact[from] = (_unreadByContact[from] ?: 0) + 1
                    _unreadTotal.update { it + 1 }

                    if (activeChats.contains(from)) {
                        emit(event)
                    } else {
                        val buf = pendingByContact.getOrPut(from) { ArrayDeque() }
                        buf.addLast(event)
                        _pendingCount.update { it + 1 }
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
                if (currentMode != ReceptionMode.LIVE) disconnect()
            }

            "mode_update" -> {
                val fromKey = json.optString("from").ifEmpty { return }
                val mode    = json.optString("mode").ifEmpty { return }
                KLog.socket("mode_update from=${fromKey.take(8)}... mode=$mode")
                emit(SocketEvent.ModeUpdate(fromKey, mode))
            }

            "pong" -> { }

            "error" -> {
                android.util.Log.w("KharonSocket", "server error: ${json.optString("message")}")
            }
        }
    }

    private fun emit(event: SocketEvent) {
        scope.launch { _events.emit(event) }
    }

    fun ensureConnected(pubKey: String) {
        if (_state.value is ConnectionState.Connected ||
            _state.value is ConnectionState.Connecting) return
        KLog.socket("ensureConnected — reconnecting for chat")
        connect(pubKey, ReceptionMode.LIVE)
    }

    fun resetPendingCount() {
        _pendingCount.value = 0
    }

    fun getTotalPendingCount(): Int {
        return pendingByContact.values.sumOf { it.size }
    }

    fun clearPendingForContact(pubKey: String) {
        val count = pendingByContact.remove(pubKey)?.size ?: 0
        _pendingCount.update { (it - count).coerceAtLeast(0) }
        KLog.socket("clearPending for ${pubKey.take(8)}... removed=$count")
    }

    fun getPendingCountForContact(pubKey: String): Int {
        return pendingByContact[pubKey]?.size ?: 0
    }

    fun sendMessage(recipientPubKey: String, plaintext: String, messageId: String): Boolean {
        val ws = socketRef.get() ?: run { KLog.err("sendMessage: socket is null"); return false }
        if (_state.value !is ConnectionState.Connected) { KLog.err("sendMessage: not connected"); return false }
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

    fun sendModeUpdate(mode: ReceptionMode) {
        socketRef.get()?.send(JSONObject().apply {
            put("type", "mode_update")
            put("mode", mode.name)
        }.toString())
        KLog.socket("sendModeUpdate mode=$mode")
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

    fun markChatActive(contactPubKey: String) {
        activeChats.add(contactPubKey)
        KLog.socket("markChatActive ${contactPubKey.take(8)}...")
    }

    fun registerChat(contactPubKey: String): List<SocketEvent.Message> {
        activeChats.add(contactPubKey)
        val pending = pendingByContact.remove(contactPubKey)?.toList() ?: emptyList()
        clearUnread(contactPubKey) // сбрасываем счётчик когда открываем чат
        KLog.socket("registerChat ${contactPubKey.take(8)}... pending=${pending.size}")
        return pending
    }

    fun unregisterChat(contactPubKey: String) {
        activeChats.remove(contactPubKey)
    }

    fun ping() {
        socketRef.get()?.send(JSONObject().put("type", "ping").toString())
    }

    private fun scheduleReconnect() {
        if (isShutdown.get() || currentMode != ReceptionMode.LIVE) return
        scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            doConnect()
        }
    }

    fun disconnect() {
        isShutdown.set(true)
        isConnecting.set(false)
        socketRef.getAndSet(null)?.close(1000, "shutdown")
        _state.value = ConnectionState.Disconnected
    }
}

data class SocketConfig(val serverUrl: String)