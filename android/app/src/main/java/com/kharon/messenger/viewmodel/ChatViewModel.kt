package com.kharon.messenger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharon.messenger.crypto.CryptoManager
import com.kharon.messenger.model.ChatMessage
import com.kharon.messenger.model.MessageStatus
import com.kharon.messenger.network.ConnectionState
import com.kharon.messenger.network.KharonSocket
import com.kharon.messenger.network.SocketEvent
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
    val messages:   List<ChatMessage> = emptyList(),
    val inputText:  String            = "",
    val connection: ConnectionState   = ConnectionState.Disconnected,
    val myPubKey:   String            = "",
    val credits:    Int               = 10,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val socket: KharonSocket,
    private val crypto: CryptoManager,
) : ViewModel() {

    private var contactPubKey = ""
    private var eventsJob: Job? = null
    private var ttlJob: Job? = null

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    fun init(pubKey: String) {
        if (pubKey.isEmpty() || contactPubKey == pubKey) return
        contactPubKey = pubKey

        _uiState.update { it.copy(myPubKey = crypto.getOrCreateKeyPair().publicKey) }

        // Один collect для всех событий
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
                        scheduleTtlCleanup(event.msg.id)
                    }

                    is SocketEvent.ReadReceipt -> {
                        _uiState.update { state ->
                            state.copy(
                                messages = state.messages.map { m ->
                                    if (m.id == event.msgId) m.copy(status = MessageStatus.READ)
                                    else m
                                },
                                // Восстанавливаем кредиты когда получатель прочитал
                                credits = 10
                            )
                        }
                    }

                    is SocketEvent.Cancelled -> {
                        _uiState.update { state ->
                            state.copy(messages = state.messages.filter { it.id != event.msgId })
                        }
                    }

                    is SocketEvent.Welcome -> { /* handled by connection state */ }
                    is SocketEvent.QueueEnd -> { /* handled by KharonSocket */ }
                }
            }
        }

        // Слушаем состояние соединения отдельно
        viewModelScope.launch {
            socket.state.collect { connState ->
                _uiState.update { it.copy(connection = connState) }
            }
        }

        // TTL sweep каждые 30 сек — удаляем сообщения старше 2 минут из UI
        ttlJob?.cancel()
        ttlJob = viewModelScope.launch {
            while (true) {
                delay(30_000)
                val cutoff = System.currentTimeMillis() - 120_000
                _uiState.update { state ->
                    state.copy(messages = state.messages.filter { it.timestamp > cutoff })
                }
            }
        }
    }

    // Помечаем сообщение как READ когда пользователь проскроллил его
    fun onMessageVisible(msgId: String, fromKey: String) {
        val msg = _uiState.value.messages.find { it.id == msgId } ?: return
        if (msg.isOutgoing || msg.status == MessageStatus.READ) return

        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { m ->
                    if (m.id == msgId) m.copy(status = MessageStatus.READ) else m
                }
            )
        }
        // Уведомляем отправителя
        socket.sendRead(fromKey, msgId)
    }

    fun cancelMessage(msgId: String) {
        if (contactPubKey.isEmpty()) return
        socket.cancelMessage(contactPubKey, msgId)
        // Оптимистично убираем из UI
        _uiState.update { state ->
            state.copy(messages = state.messages.filter { it.id != msgId })
        }
        // Возвращаем кредит
        _uiState.update { it.copy(credits = (it.credits + 1).coerceAtMost(10)) }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty() || contactPubKey.isEmpty()) return

        val isOnline = _uiState.value.connection is ConnectionState.Connected
        // Кредиты тратятся только если получатель офлайн
        if (!isOnline && _uiState.value.credits <= 0) return

        val msgId = UUID.randomUUID().toString()
        val msg = ChatMessage(
            id         = msgId,
            text       = text,
            isOutgoing = true,
            timestamp  = System.currentTimeMillis(),
            status     = MessageStatus.SENDING,
        )

        _uiState.update { state ->
            state.copy(
                messages  = state.messages + msg,
                inputText = "",
                credits   = if (!isOnline) (state.credits - 1).coerceAtLeast(0) else state.credits
            )
        }

        scheduleTtlCleanup(msgId)

        viewModelScope.launch {
            val ok = socket.sendMessage(
                recipientPubKey = contactPubKey,
                plaintext       = text,
                messageId       = msgId,
            )
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

    // Планируем удаление конкретного сообщения через 2 минуты
    private fun scheduleTtlCleanup(msgId: String) {
        viewModelScope.launch {
            delay(120_000)
            _uiState.update { state ->
                state.copy(messages = state.messages.filter { it.id != msgId })
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        eventsJob?.cancel()
        ttlJob?.cancel()
    }
}
