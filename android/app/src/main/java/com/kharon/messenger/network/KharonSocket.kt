package com.kharon.messenger.network

import com.kharon.messenger.crypto.CryptoManager
import com.kharon.messenger.crypto.DecryptResult
import com.kharon.messenger.model.IncomingMessage
import com.kharon.messenger.model.ReceptionMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed class ConnectionState {
    object Disconnected  : ConnectionState()
    object Connecting    : ConnectionState()
    object Connected     : ConnectionState()
    data class Error(val reason: String) : ConnectionState()
}

@Singleton
class KharonSocket @Inject constructor(
    private val crypto: CryptoManager,
    private val config: SocketConfig,
) {
    private val seenIds = LinkedHashSet<String>()
    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: WebSocket? = null
    private var myPubKey: String   = ""
    private var mySecKey: String   = ""
    private var currentMode: ReceptionMode = ReceptionMode.LIVE

    private var backoffMs   = 1_000L
    private var isShutdown  = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .certificatePinner(
            CertificatePinner.Builder()
                .add("kharon-messenger.duckdns.org", "sha256/mrOaIg2JbLnqpoEdbcazxo7RXaR8k6gWoYynbTcxZ8U=")
                .build()
        )
        .build()

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    private val _messages = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<IncomingMessage> = _messages

    fun connect(pubKey: String, secKey: String, mode: ReceptionMode) {
        myPubKey  = pubKey
        mySecKey  = secKey
        currentMode = mode
        isShutdown = false
        doConnect()
    }

    private fun doConnect() {
        if (isShutdown) return
        _state.value = ConnectionState.Connecting

        val request = Request.Builder().url(config.serverUrl).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                backoffMs = 1_000L
                val hello = JSONObject().apply {
                    put("type", "hello")
                    put("pubKey", myPubKey)
                    put("reception_mode", currentMode.name)
                }
                ws.send(hello.toString())
            }

            override fun onMessage(ws: WebSocket, text: String) {
                val json = runCatching { JSONObject(text) }.getOrNull() ?: return
                
                // Если сервер сказал "все", а мы в пульсе - закрываемся
                if (json.optString("type") == "queue_end" && currentMode != ReceptionMode.LIVE) {
                    disconnect()
                    return
                }
                handleMessage(text)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _state.value = ConnectionState.Disconnected
                if (!isShutdown) scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _state.value = ConnectionState.Error(t.message ?: "failed")
                if (!isShutdown) scheduleReconnect()
            }
        })
    }

    private fun handleMessage(text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (json.optString("type")) {
            "welcome" -> _state.value = ConnectionState.Connected
            "msg" -> {
                val from = json.optString("from")
                val payload = json.optString("payload")
                val id = json.optString("id")
                if (from.isEmpty() || payload.isEmpty() || isDuplicate(id)) return

                val result = crypto.decrypt(payload, from, mySecKey)
                if (result is DecryptResult.Success) {
                    scope.launch {
                        _messages.emit(IncomingMessage(id, from, result.plaintext, System.currentTimeMillis()))
                    }
                }
            }
            "pong" -> { /* keepalive */ }
        }
    }

    fun sendMessage(recipientPubKey: String, plaintext: String, mySecretKey: String, messageId: String): Boolean {
        val ws = socket ?: return false
        if (_state.value !is ConnectionState.Connected) return false

        val encrypted = crypto.encrypt(plaintext, recipientPubKey, mySecretKey)
        return if (encrypted is com.kharon.messenger.crypto.EncryptResult.Success) {
            ws.send(JSONObject().apply {
                put("type", "msg")
                put("to", recipientPubKey)
                put("payload", encrypted.payload)
                put("id", messageId)
            }.toString())
            true
        } else false
    }

    fun ping() { socket?.send(JSONObject().put("type", "ping").toString()) }

    private fun scheduleReconnect() {
        if (isShutdown || currentMode != ReceptionMode.LIVE) return
        scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(30_000L)
            doConnect()
        }
    }

    fun disconnect() {
        isShutdown = true
        socket?.close(1000, "shutdown")
        socket = null
        _state.value = ConnectionState.Disconnected
    }

    private fun isDuplicate(id: String): Boolean {
        if (id.isEmpty()) return false
        if (seenIds.contains(id)) return true
        seenIds.add(id)
        if (seenIds.size > 100) seenIds.remove(seenIds.first())
        return false
    }
}

data class SocketConfig(val serverUrl: String)