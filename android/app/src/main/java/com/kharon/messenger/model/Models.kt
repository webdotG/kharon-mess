package com.kharon.messenger.model

data class IncomingMessage(
    val id:        String,
    val fromKey:   String,
    val plaintext: String,
    val timestamp: Long,
)

data class ChatMessage(
    val id:         String,
    val text:       String,
    val isOutgoing: Boolean,
    val timestamp:  Long,
    val status:     MessageStatus = MessageStatus.SENT,
)

enum class MessageStatus { SENDING, SENT, DELIVERED, READ, FAILED }

data class Contact(
    val pubKey:   String,
    val name:     String,
    val isOnline: Boolean = false,
    val addedAt:  Long = System.currentTimeMillis(),
)