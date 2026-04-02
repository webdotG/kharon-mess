package com.kharon.messenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kharon.messenger.model.ChatMessage
import com.kharon.messenger.model.MessageStatus
import com.kharon.messenger.model.ReceptionMode
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
    currentMode: ReceptionMode,
    userCredits: Int,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = KharonUI.current
    val colors = theme.colors
    val listState = rememberLazyListState()

    LaunchedEffect(contactPubKey) {
        viewModel.init(contactPubKey)
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .imePadding()
    ) {
        TitleBar(
            title = contactName,
            connection = state.connection,
            mode = currentMode,
            theme = theme
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.messages, key = { it.id }) { msg ->
                MessageBubble(
                    msg = msg, 
                    theme = theme,
                    onCancel = { /* viewModel.cancelMessage(msg.id) */ }
                )
            }
        }

        InputBar(
            text = state.inputText,
            credits = userCredits,
            onChange = viewModel::onInputChange,
            onSend = viewModel::sendMessage,
            theme = theme
        )
    }
}

@Composable
private fun TitleBar(
    title: String,
    connection: ConnectionState,
    mode: ReceptionMode,
    theme: KharonTheme,
) {
    val colors = theme.colors
    val (statusText, statusColor) = when (connection) {
        is ConnectionState.Connected -> "[ONLINE]" to colors.online
        is ConnectionState.Connecting -> "[...]" to colors.subtle
        is ConnectionState.Disconnected -> "[OFFLINE]" to colors.offline
        is ConnectionState.Error -> "[ERROR]" to colors.offline
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.titleBar)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "> $title",
                color = colors.titleBarText,
                fontSize = 20.sp,
                fontFamily = theme.typography.fontFamily,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = statusText,
                color = statusColor,
                fontSize = 14.sp,
                fontFamily = theme.typography.fontFamily,
            )
        }
        Text(
            text = "MODE: ${mode.label} | SYNC: OK",
            color = colors.subtle,
            fontSize = 12.sp,
            fontFamily = theme.typography.fontFamily,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    theme: KharonTheme,
    onCancel: () -> Unit
) {
    val colors = theme.colors
    val isOut = msg.isOutgoing
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start,
    ) {
        Column(modifier = Modifier.widthIn(max = 320.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isOut) "you > " else "peer < ",
                    color = colors.subtle,
                    fontSize = 14.sp,
                    fontFamily = theme.typography.fontFamily,
                )
                if (isOut && msg.status == MessageStatus.SENDING) {
                    Text(
                        text = " [SENDING] [X]",
                        color = colors.offline,
                        fontSize = 14.sp,
                        fontFamily = theme.typography.fontFamily,
                        modifier = Modifier.clickable { onCancel() }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .background(if (isOut) colors.msgOut else colors.msgIn)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        text = msg.text,
                        color = if (isOut) colors.msgOutText else colors.msgInText,
                        fontSize = 18.sp,
                        fontFamily = theme.typography.fontFamily,
                    )
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = timeFormat.format(Date(msg.timestamp)),
                            color = colors.subtle,
                            fontSize = 12.sp,
                            fontFamily = theme.typography.fontFamily,
                        )
                        if (isOut) {
                            Text(
                                text = when (msg.status) {
                                    MessageStatus.SENDING -> "~"
                                    MessageStatus.SENT -> "+"
                                    MessageStatus.DELIVERED -> "++"
                                    MessageStatus.FAILED -> "!"
                                },
                                color = if(msg.status == MessageStatus.DELIVERED) colors.primary else colors.subtle,
                                fontSize = 14.sp,
                                fontFamily = theme.typography.fontFamily,
                                fontWeight = FontWeight.Bold
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
    text: String,
    credits: Int,
    onChange: (String) -> Unit,
    onSend: () -> Unit,
    theme: KharonTheme,
) {
    val colors = theme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "CREDITS: $credits (-10/msg)",
                color = if (credits < 50) colors.offline else colors.subtle,
                fontSize = 12.sp,
                fontFamily = theme.typography.fontFamily,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= 500) onChange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                textStyle = TextStyle(
                    color = colors.onSurface,
                    fontSize = 18.sp,
                    fontFamily = theme.typography.fontFamily,
                ),
                cursorBrush = SolidColor(colors.primary),
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            "enter command...",
                            color = colors.subtle,
                            fontSize = 18.sp,
                            fontFamily = theme.typography.fontFamily,
                        )
                    }
                    inner()
                }
            )
        }

        Text(
            text = " [SEND]",
            color = if (text.isNotBlank() && credits >= 10) colors.primary else colors.subtle,
            fontSize = 18.sp,
            fontFamily = theme.typography.fontFamily,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(start = 8.dp)
                .clickable(enabled = text.isNotBlank() && credits >= 10) { onSend() }
        )
    }
}