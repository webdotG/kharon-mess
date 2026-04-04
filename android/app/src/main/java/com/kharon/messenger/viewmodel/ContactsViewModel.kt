package com.kharon.messenger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharon.messenger.crypto.CryptoManager
import com.kharon.messenger.model.Contact
import com.kharon.messenger.model.ReceptionMode
import com.kharon.messenger.network.ConnectionState
import com.kharon.messenger.network.KharonSocket
import com.kharon.messenger.network.SocketEvent
import com.kharon.messenger.storage.ContactDao
import com.kharon.messenger.storage.ContactEntity
import com.kharon.messenger.util.KLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactsUiState(
    val contacts:   List<Contact>   = emptyList(),
    val myPubKey:   String          = "",
    val connection: ConnectionState = ConnectionState.Disconnected,
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val contactDao: ContactDao,
    private val crypto:     CryptoManager,
    private val socket:     KharonSocket,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState

    init {
        val keyPair = crypto.getOrCreateKeyPair()
        _uiState.update { it.copy(myPubKey = keyPair.publicKey) }

        // Слушаем контакты из БД
        viewModelScope.launch {
            contactDao.getAllFlow()
                .map { list ->
                    list.map { entity ->
                        Contact(
                            pubKey        = entity.pubKey,
                            name          = entity.name,
                            receptionMode = runCatching {
                                ReceptionMode.valueOf(entity.receptionMode)
                            }.getOrDefault(ReceptionMode.LIVE),
                            // сохраняем unreadCount из текущего state
                            unreadCount   = socket.getUnreadCount(entity.pubKey)
                        )
                    }
                }
                .collect { contacts ->
                    _uiState.update { it.copy(contacts = contacts) }
                }
        }

        // Слушаем события сокета — обновляем режим
        viewModelScope.launch {
            socket.events.collect { event ->
                if (event is SocketEvent.ModeUpdate) {
                    KLog.vm("ModeUpdate from=${event.fromKey.take(8)}... mode=${event.mode}")
                    contactDao.updateReceptionMode(event.fromKey, event.mode)
                }
            }
        }

        // Обновляем счётчики через unreadTotal
                viewModelScope.launch {
                    socket.unreadTotal.collect {
                        _uiState.update { state ->
                            state.copy(
                                contacts = state.contacts.map { contact ->
                                    contact.copy(unreadCount = socket.getUnreadCount(contact.pubKey))
                                }
                            )
                        }
                    }
        }

        // Слушаем состояние соединения
        viewModelScope.launch {
            socket.state.collect { state ->
                _uiState.update { it.copy(connection = state) }
            }
        }
    }

    fun addContact(pubKey: String, name: String) {
        viewModelScope.launch {
            contactDao.insert(ContactEntity(pubKey = pubKey, name = name.trim()))
        }
    }

    fun deleteContact(pubKey: String) {
        viewModelScope.launch {
            contactDao.deleteByPubKey(pubKey)
        }
    }
}