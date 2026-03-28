package com.kharon.messenger.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kharon.messenger.crypto.CryptoManager
import com.kharon.messenger.model.Contact
import com.kharon.messenger.network.ConnectionState
import com.kharon.messenger.network.KharonSocket
import com.kharon.messenger.storage.ContactDao
import com.kharon.messenger.storage.ContactEntity
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
                .map { list -> list.map { Contact(pubKey = it.pubKey, name = it.name) } }
                .collect { contacts ->
                    _uiState.update { it.copy(contacts = contacts) }
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
