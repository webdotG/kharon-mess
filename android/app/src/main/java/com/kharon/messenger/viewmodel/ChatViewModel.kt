package com.kharon.messenger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharon.messenger.crypto.CryptoManager
import com.kharon.messenger.model.ChatMessage
import com.kharon.messenger.model.MessageStatus
import com.kharon.messenger.network.ConnectionState
import com.kharon.messenger.network.KharonSocket
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ChatUiState(
    val messages:   List<ChatMessage>     = emptyList(),
    val inputText:  String                = "",
    val connection: ConnectionState       = ConnectionState.Disconnected,
    val myPubKey:   String                = "",
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val socket:  KharonSocket,
    private val crypto:  CryptoManager,
) : ViewModel() {

    // Устанавливается из ChatScreen через init()
    private var contactPubKey: String = ""

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState

    fun init(pubKey: String) {
        if (pubKey.isEmpty() || contactPubKey == pubKey) return
        contactPubKey = pubKey

        val keyPair = crypto.getOrCreateKeyPair()
        _uiState.update { it.copy(myPubKey = keyPair.publicKey) }

        // Слушаем входящие сообщения
        viewModelScope.launch {
            socket.messages.collect { incoming ->
                if (incoming.fromKey != contactPubKey) return@collect
                val msg = ChatMessage(
                    id         = incoming.id,
                    text       = incoming.plaintext,
                    isOutgoing = false,
                    timestamp  = incoming.timestamp,
                    status     = MessageStatus.DELIVERED,
                )
                _uiState.update { state ->
                    state.copy(messages = state.messages + msg)
                }
            }
        }

        // Слушаем состояние соединения
        viewModelScope.launch {
            socket.state.collect { connState ->
                _uiState.update { it.copy(connection = connState) }
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return
        android.util.Log.d("ChatViewModel", "sendMessage: contactPubKey='$contactPubKey' text='$text'")
        if (contactPubKey.isEmpty()) {
            return
        }

        val keyPair = crypto.getOrCreateKeyPair()
        val msgId   = UUID.randomUUID().toString()

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
                mySecretKey     = keyPair.secretKey,
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
}
