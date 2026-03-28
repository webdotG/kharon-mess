package com.kharon.messenger.network

import com.kharon.messenger.crypto.CryptoManager
import com.kharon.messenger.crypto.DecryptResult
import com.kharon.messenger.model.IncomingMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ─── Состояние соединения ─────────────────────────────────────────────────────

sealed class ConnectionState {
    object Disconnected  : ConnectionState()
    object Connecting    : ConnectionState()
    object Connected     : ConnectionState()
    data class Error(val reason: String) : ConnectionState()
}

// ─── KharonSocket ─────────────────────────────────────────────────────────────

@Singleton
class KharonSocket @Inject constructor(
    private val crypto: CryptoManager,
    private val config: SocketConfig,
) {
    // Защита от replay attack — храним последние 100 id
    private val seenIds = LinkedHashSet<String>()

    private fun isDuplicate(id: String): Boolean {
        if (id.isEmpty()) return false
        if (seenIds.contains(id)) return true
        seenIds.add(id)
        if (seenIds.size > 100) seenIds.remove(seenIds.first())
        return false
    }

    private val scope  = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: WebSocket? = null
    private var myPubKey: String   = ""
    private var mySecKey: String   = ""

    // Exponential backoff: 1s, 2s, 4s, 8s, 16s, 30s (max)
    private var backoffMs   = BACKOFF_INITIAL_MS
    private var isShutdown  = false

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)    // 0 = без таймаута для WS
        .writeTimeout(10, TimeUnit.SECONDS)
        // Certificate pinning — отклоняем любой сертификат кроме нашего
        // даже если он подписан доверенным CA
        .certificatePinner(
            CertificatePinner.Builder()
                .add("kharon-messenger.duckdns.org", "sha256/mrOaIg2JbLnqpoEdbcazxo7RXaR8k6gWoYynbTcxZ8U=")
                .build()
        )
        .build()

    // ── Публичные потоки ──────────────────────────────────────────────────────

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val state: StateFlow<ConnectionState> = _state

    // Расшифрованные входящие сообщения
    private val _messages = MutableSharedFlow<IncomingMessage>(extraBufferCapacity = 64)
    val messages: SharedFlow<IncomingMessage> = _messages

    // ── Подключение ───────────────────────────────────────────────────────────

    fun connect(pubKey: String, secKey: String) {
        myPubKey  = pubKey
        mySecKey  = secKey
        isShutdown = false
        doConnect()
    }

    private fun doConnect() {
        if (isShutdown) return

        _state.value = ConnectionState.Connecting

        val request = Request.Builder()
            .url(config.serverUrl)
            .build()

        socket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                backoffMs = BACKOFF_INITIAL_MS  // сбрасываем backoff при успехе
                // Представляемся серверу
                ws.send(json("type" to "hello", "pubKey" to myPubKey))
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _state.value = ConnectionState.Disconnected
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                _state.value = ConnectionState.Error(t.message ?: "connection failed")
                scheduleReconnect()
            }
        })
    }

    // ── Обработка входящих сообщений ──────────────────────────────────────────

    private fun handleMessage(text: String) {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return

        when (json.optString("type")) {

            "welcome" -> {
                _state.value = ConnectionState.Connected
            }

            "msg" -> {
                val from    = json.optString("from").ifEmpty { return }
                val payload = json.optString("payload").ifEmpty { return }
                val id      = json.optString("id")

                // Replay attack protection
                if (isDuplicate(id)) return

                // Расшифровываем — сервер никогда не видел plaintext
                val result = crypto.decrypt(payload, from, mySecKey)

                when (result) {
                    is DecryptResult.Success -> {
                        scope.launch {
                            _messages.emit(
                                IncomingMessage(
                                    id        = id,
                                    fromKey   = from,
                                    plaintext = result.plaintext,
                                    timestamp = System.currentTimeMillis(),
                                )
                            )
                        }
                    }
                    is DecryptResult.Error -> {
                        // Сообщение не расшифровалось — игнорируем
                        // Это нормально: могло прийти от неизвестного ключа
                    }
                }
            }

            "error" -> {
                val msg = json.optString("message")
                // Логируем технически, пользователю не показываем детали
            }

            "pong" -> { /* keepalive ok */ }
        }
    }

    // ── Отправка ──────────────────────────────────────────────────────────────

    fun sendMessage(
        recipientPubKey: String,
        plaintext: String,
        mySecretKey: String,
        messageId: String,
    ): Boolean {
        val ws = socket ?: run {
            return false
        }
        if (_state.value !is ConnectionState.Connected) {
            return false
        }

        val encrypted = crypto.encrypt(plaintext, recipientPubKey, mySecretKey)

        return when (encrypted) {
            is com.kharon.messenger.crypto.EncryptResult.Success -> {
                ws.send(json(
                    "type"    to "msg",
                    "to"      to recipientPubKey,
                    "payload" to encrypted.payload,
                    "id"      to messageId,
                ))
                true
            }
            is com.kharon.messenger.crypto.EncryptResult.Error -> false
        }
    }

    fun ping() {
        socket?.send(json("type" to "ping"))
    }

    // ── Reconnect с exponential backoff ───────────────────────────────────────

    private fun scheduleReconnect() {
        if (isShutdown) return

        scope.launch {
            delay(backoffMs)
            // Увеличиваем задержку вдвое, но не более 30 секунд
            backoffMs = (backoffMs * 2).coerceAtMost(BACKOFF_MAX_MS)
            doConnect()
        }
    }

    // ── Завершение ────────────────────────────────────────────────────────────

    fun disconnect() {
        isShutdown = true
        socket?.close(1000, "shutdown")
        socket = null
        _state.value = ConnectionState.Disconnected
        scope.cancel()
    }

    // ── Утилиты ──────────────────────────────────────────────────────────────

    private fun json(vararg pairs: Pair<String, String>): String {
        val obj = JSONObject()
        pairs.forEach { (k, v) -> obj.put(k, v) }
        return obj.toString()
    }

    companion object {
        private const val BACKOFF_INITIAL_MS = 1_000L
        private const val BACKOFF_MAX_MS     = 30_000L
    }
}

// ─── Конфиг ───────────────────────────────────────────────────────────────────

data class SocketConfig(
    val serverUrl: String   // например "wss://192.168.1.100:443"
)
