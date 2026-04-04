package com.kharon.messenger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharon.messenger.crypto.CryptoManager
import com.kharon.messenger.model.ChatMessage
import com.kharon.messenger.model.MessageStatus
import com.kharon.messenger.model.ReceptionMode
import com.kharon.messenger.network.ConnectionState
import com.kharon.messenger.network.KharonSocket
import com.kharon.messenger.network.SocketEvent
import com.kharon.messenger.storage.ContactDao
import com.kharon.messenger.util.KLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages:      List<ChatMessage> = emptyList(),
    val inputText:     String            = "",
    val connection:    ConnectionState   = ConnectionState.Disconnected,
    val myPubKey:      String            = "",
    val credits:       Int               = 10,
    val nextCleanupMs: Long              = 0L,
    val contactMode:   ReceptionMode     = ReceptionMode.LIVE,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val socket:     KharonSocket,
    private val crypto:     CryptoManager,
    private val contactDao: ContactDao,
) : ViewModel() {

    private var contactPubKey = ""
    private var eventsJob: Job? = null
    private var ttlJob: Job? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    fun init(pubKey: String) {
        KLog.vm("init pubKey=${pubKey.take(8)}... current=${contactPubKey.take(8)}...")
        if (pubKey.isEmpty() || contactPubKey == pubKey) return
        contactPubKey = pubKey

        _uiState.update { it.copy(myPubKey = crypto.getOrCreateKeyPair().publicKey) }

        val myPubKey = crypto.getOrCreateKeyPair().publicKey
        socket.ensureConnected(myPubKey)

        viewModelScope.launch {
            val entity = contactDao.getByPubKey(pubKey)
            val mode = entity?.receptionMode?.let {
                runCatching { ReceptionMode.valueOf(it) }.getOrDefault(ReceptionMode.LIVE)
            } ?: ReceptionMode.LIVE
            _uiState.update { it.copy(contactMode = mode) }
        }

        val buffered = socket.registerChat(pubKey)
        KLog.vm("init: buffered=${buffered.size} messages")
        if (buffered.isNotEmpty()) {
            val bufferedMsgs = buffered.map { event ->
                ChatMessage(
                    id         = event.msg.id,
                    text       = event.msg.plaintext,
                    isOutgoing = false,
                    timestamp  = event.msg.timestamp,
                    status     = MessageStatus.DELIVERED,
                )
            }
            _uiState.update { it.copy(messages = it.messages + bufferedMsgs) }
        }

        eventsJob?.cancel()
        eventsJob = viewModelScope.launch {
            socket.events.collect { event ->
                when (event) {
                    is SocketEvent.Message -> {
                        if (event.msg.fromKey != contactPubKey) return@collect
                        val msg = ChatMessage(
                            id         = event.msg.id,
                            text       = event.msg.plaintext,
                            isOutgoing = false,
                            timestamp  = event.msg.timestamp,
                            status     = MessageStatus.DELIVERED,
                        )
                        _uiState.update { it.copy(messages = it.messages + msg) }
                    }
                    is SocketEvent.ReadReceipt -> {
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.map { m ->
                                    if (m.id == event.msgId) m.copy(status = MessageStatus.READ)
                                    else m
                                },
                                credits = 10
                            )
                        }
                    }
                    is SocketEvent.Cancelled -> {
                        _uiState.update { state ->
                            state.copy(messages = state.messages.filter { it.id != event.msgId })
                        }
                    }
                    is SocketEvent.ModeUpdate -> {
                        if (event.fromKey == contactPubKey) {
                            val mode = runCatching {
                                ReceptionMode.valueOf(event.mode)
                            }.getOrDefault(ReceptionMode.LIVE)
                            KLog.vm("contactMode updated: $mode")
                            _uiState.update { it.copy(contactMode = mode) }
                            contactDao.updateReceptionMode(event.fromKey, event.mode)
                        }
                    }
                    is SocketEvent.Welcome  -> {}
                    is SocketEvent.QueueEnd -> {}
                }
            }
        }

        viewModelScope.launch {
            socket.state.collect { connState ->
                _uiState.update { it.copy(connection = connState) }
            }
        }

        ttlJob?.cancel()
        ttlJob = viewModelScope.launch {
            while (true) {
                delay(5_000)
                val now    = System.currentTimeMillis()
                val cutoff = now - 120_000
                val oldestRead = _uiState.value.messages
                    .filter { it.status == MessageStatus.READ }
                    .minByOrNull { it.readAt ?: it.timestamp }
                val nextCleanup = if (oldestRead != null)
                    (oldestRead.readAt ?: oldestRead.timestamp) + 120_000
                else 0L
                _uiState.update { state ->
                    val filtered = state.messages.filter { msg ->
                        if (msg.status == MessageStatus.READ) {
                            (msg.readAt ?: msg.timestamp) > cutoff
                        } else true
                    }
                    val sentCount = filtered.count { it.isOutgoing && it.status == MessageStatus.SENT }
                    state.copy(
                        messages      = filtered,
                        nextCleanupMs = nextCleanup,
                        credits       = (10 - sentCount).coerceAtLeast(0),
                    )
                }
            }
        }
    }

    fun onMessageVisible(msgId: String, fromKey: String) {
        val msg = _uiState.value.messages.find { it.id == msgId } ?: return
        if (msg.isOutgoing || msg.status == MessageStatus.READ) return
        val readTime = System.currentTimeMillis()
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { m ->
                    if (m.id == msgId) m.copy(status = MessageStatus.READ, readAt = readTime) else m
                }
            )
        }
        socket.sendRead(fromKey, msgId)
    }

    fun cancelMessage(msgId: String) {
        if (contactPubKey.isEmpty()) return
        socket.cancelMessage(contactPubKey, msgId)
        _uiState.update { state ->
            state.copy(messages = state.messages.filter { it.id != msgId })
        }
        _uiState.update { it.copy(credits = (it.credits + 1).coerceAtMost(10)) }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        KLog.vm("sendMessage: text=${text.take(10)} contact=${contactPubKey.take(8)}... credits=${_uiState.value.credits}")
        if (text.isEmpty() || contactPubKey.isEmpty()) { KLog.err("sendMessage: empty text or contact"); return }
        val sentCount = _uiState.value.messages.count { it.isOutgoing && it.status == MessageStatus.SENT }
        if (sentCount >= 10) return
        val msgId = UUID.randomUUID().toString()
        val msg = ChatMessage(
            id         = msgId,
            text       = text,
            isOutgoing = true,
            timestamp  = System.currentTimeMillis(),
            status     = MessageStatus.SENDING,
        )
        _uiState.update { state ->
            state.copy(messages = state.messages + msg, inputText = "")
        }
        viewModelScope.launch {
            val ok = socket.sendMessage(
                recipientPubKey = contactPubKey,
                plaintext       = text,
                messageId       = msgId,
            )
            KLog.vm("sendMessage result: ok=$ok status=${if (ok) "SENT" else "FAILED"}")
            val newStatus = if (ok) MessageStatus.SENT else MessageStatus.FAILED
            _uiState.update { state ->
                state.copy(
                    messages = state.messages.map { m ->
                        if (m.id == msgId) m.copy(status = newStatus) else m
                    }
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        eventsJob?.cancel()
        ttlJob?.cancel()
        if (contactPubKey.isNotEmpty()) {
            socket.clearPendingForContact(contactPubKey)
            socket.clearUnread(contactPubKey)  // сбрасываем счётчик
            socket.unregisterChat(contactPubKey)
        }
    }
}