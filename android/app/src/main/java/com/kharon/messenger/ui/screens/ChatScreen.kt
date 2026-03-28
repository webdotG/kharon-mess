package com.kharon.messenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kharon.messenger.model.ChatMessage
import com.kharon.messenger.model.MessageStatus
import com.kharon.messenger.network.ConnectionState
import com.kharon.messenger.ui.theme.KharonTheme
import com.kharon.messenger.ui.theme.KharonUI
import com.kharon.messenger.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ChatScreen(
    contactName: String,
    contactPubKey: String = "",
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state     by viewModel.uiState.collectAsState()
    val theme      = KharonUI.current
    val colors     = theme.colors
    val listState  = rememberLazyListState()



    LaunchedEffect(contactPubKey) {
        viewModel.init(contactPubKey)
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    // imePadding на корневом Column — весь контент двигается над клавиатурой
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()   // отступ от челки и навбара
            .imePadding()          // двигаемся над клавиатурой
    ) {
        // ── Title bar ─────────────────────────────────────────────────────────
        TitleBar(
            title      = contactName,
            connection = state.connection,
            theme      = theme,
        )

        // ── Сообщения — занимают всё свободное место ──────────────────────────
        LazyColumn(
            state          = listState,
            modifier       = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(state.messages, key = { it.id }) { msg ->
                MessageBubble(msg = msg, theme = theme)
            }
        }

        // ── Input bar — всегда прижат к низу над клавиатурой ─────────────────
        InputBar(
            text     = state.inputText,
            onChange = viewModel::onInputChange,
            onSend   = viewModel::sendMessage,
            theme    = theme,
        )
    }
}

@Composable
private fun TitleBar(
    title:      String,
    connection: ConnectionState,
    theme:      KharonTheme,
) {
    val colors = theme.colors

    val (statusText, statusColor) = when (connection) {
        is ConnectionState.Connected    -> "[ONLINE]"  to colors.online
        is ConnectionState.Connecting   -> "[...]"     to colors.subtle
        is ConnectionState.Disconnected -> "[OFFLINE]" to colors.offline
        is ConnectionState.Error        -> "[ERROR]"   to colors.offline
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.titleBar)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text       = "> $title",
            color      = colors.titleBarText,
            fontSize   = theme.typography.titleSize,
            fontFamily = theme.typography.fontFamily,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text       = statusText,
            color      = statusColor,
            fontSize   = theme.typography.captionSize,
            fontFamily = theme.typography.fontFamily,
        )
    }
}

@Composable
private fun MessageBubble(
    msg:   ChatMessage,
    theme: KharonTheme,
) {
    val colors     = theme.colors
    val isOut      = msg.isOutgoing
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            // Префикс в терминальном стиле
            Text(
                text = if (isOut) "you > " else "${theme.typography.fontFamily}< ",
                color      = colors.subtle,
                fontSize   = theme.typography.captionSize,
                fontFamily = theme.typography.fontFamily,
            )

            Box(
                modifier = Modifier
                    .background(if (isOut) colors.msgOut else colors.msgIn)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Column {
                    Text(
                        text       = msg.text,
                        color      = if (isOut) colors.msgOutText else colors.msgInText,
                        fontSize   = theme.typography.bodySize,
                        fontFamily = theme.typography.fontFamily,
                    )
                    Row(
                        modifier              = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            text       = timeFormat.format(Date(msg.timestamp)),
                            color      = colors.subtle,
                            fontSize   = theme.typography.captionSize,
                            fontFamily = theme.typography.fontFamily,
                        )
                        if (isOut) {
                            Text(
                                text = when (msg.status) {
                                    MessageStatus.SENDING   -> "~"
                                    MessageStatus.SENT      -> "+"
                                    MessageStatus.DELIVERED -> "++"
                                    MessageStatus.FAILED    -> "!"
                                },
                                color      = colors.subtle,
                                fontSize   = theme.typography.captionSize,
                                fontFamily = theme.typography.fontFamily,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InputBar(
    text:     String,
    onChange: (String) -> Unit,
    onSend:   () -> Unit,
    theme:    KharonTheme,
) {
    val colors = theme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text       = "> ",
            color      = colors.primary,
            fontSize   = theme.typography.bodySize,
            fontFamily = theme.typography.fontFamily,
            fontWeight = FontWeight.Bold,
        )

        BasicTextField(
            value         = text,
            onValueChange = { if (it.length <= 500) onChange(it) },
            modifier      = Modifier
                .weight(1f)
                .background(colors.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            textStyle = TextStyle(
                color      = colors.onSurface,
                fontSize   = theme.typography.bodySize,
                fontFamily = theme.typography.fontFamily,
            ),
            cursorBrush = SolidColor(colors.primary),
            maxLines    = 4,
            decorationBox = { inner ->
                if (text.isEmpty()) {
                    Text(
                        "type message...",
                        color      = colors.subtle,
                        fontSize   = theme.typography.bodySize,
                        fontFamily = theme.typography.fontFamily,
                    )
                }
                inner()
            }
        )

        Text(
            text       = if (text.isNotBlank()) " [>]" else " [ ]",
            color      = if (text.isNotBlank()) colors.primary else colors.subtle,
            fontSize   = theme.typography.bodySize,
            fontFamily = theme.typography.fontFamily,
            fontWeight = FontWeight.Bold,
            modifier   = if (text.isNotBlank())
                    Modifier.padding(start = 4.dp).clickable { onSend() }
                else
                    Modifier.padding(start = 4.dp)
        )
    }
}

