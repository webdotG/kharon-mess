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
    contactName:   String,
    contactPubKey: String = "",
    viewModel:     ChatViewModel = hiltViewModel(),
) {
    val state     by viewModel.uiState.collectAsState()
    val theme      = KharonUI.current
    val colors     = theme.colors
    val listState  = rememberLazyListState()

    LaunchedEffect(contactPubKey) {
        viewModel.init(contactPubKey)
    }

    // Автоскролл к новым сообщениям
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    // READ: помечаем видимые входящие сообщения как прочитанные
    LaunchedEffect(listState.firstVisibleItemIndex, state.messages.size) {
        val visibleInfo = listState.layoutInfo.visibleItemsInfo
        val messages    = state.messages
        visibleInfo.forEach { itemInfo ->
            val msg = messages.getOrNull(itemInfo.index) ?: return@forEach
            if (!msg.isOutgoing && msg.status == MessageStatus.DELIVERED) {
                viewModel.onMessageVisible(msg.id, contactPubKey)
            }
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
            title       = contactName,
            connection  = state.connection,
            contactMode = state.contactMode,  // режим собеседника
            credits     = state.credits,
            theme       = theme,
        )

        LazyColumn(
            state          = listState,
            modifier       = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.messages, key = { it.id }) { msg ->
                MessageBubble(
                    msg          = msg,
                    contactName  = contactName,
                    theme        = theme,
                    onCancel     = {
                        if (msg.status == MessageStatus.SENT) {
                            viewModel.cancelMessage(msg.id)
                        }
                    }
                )
            }
        }

        InputBar(
            text          = state.inputText,
            credits       = state.credits,
            nextCleanupMs = state.nextCleanupMs,
            onChange      = viewModel::onInputChange,
            onSend        = viewModel::sendMessage,
            theme         = theme,
        )
    }
}

@Composable
private fun TitleBar(
    title:       String,
    connection:  ConnectionState,
    contactMode: ReceptionMode,
    credits:     Int,
    theme:       KharonTheme,
) {
    val colors = theme.colors

    val (statusText, statusColor) = when (connection) {
        is ConnectionState.Connected    -> "ONLINE"  to colors.online
        is ConnectionState.Connecting   -> "..."     to colors.subtle
        is ConnectionState.Disconnected -> "OFFLINE" to colors.offline
        is ConnectionState.Error        -> "ERROR"   to colors.offline
    }

    val contactModeText = when {
        contactMode == ReceptionMode.LIVE   -> "всегда на связи"
        contactMode == ReceptionMode.SILENT -> "в тишине"
        contactMode.minutes < 60            -> "окно раз в ${contactMode.minutes} мин"
        contactMode.minutes < 1440          -> "окно раз в ${contactMode.minutes / 60} ч"
        else                                -> "окно раз в 24 ч"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.titleBar)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text       = "> $title",
                color      = colors.titleBarText,
                fontSize   = 18.sp,
                fontFamily = theme.typography.fontFamily,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text       = statusText,
                color      = statusColor,
                fontSize   = 13.sp,
                fontFamily = theme.typography.fontFamily,
                fontWeight = FontWeight.Bold,
            )
        }
        Row(
            modifier              = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text       = "Абонент: $contactModeText",
                color      = colors.subtle,
                fontSize   = 11.sp,
                fontFamily = theme.typography.fontFamily,
            )
            Text(
                text       = "Тебе столько можно отправить: $credits/10",
                color      = if (credits <= 2) colors.offline else colors.subtle,
                fontSize   = 11.sp,
                fontFamily = theme.typography.fontFamily,
            )
        }
    }
}

@Composable
private fun MessageBubble(
    msg:         ChatMessage,
    contactName: String,
    theme:       KharonTheme,
    onCancel:    () -> Unit,
) {
    val colors     = theme.colors
    val isOut      = msg.isOutgoing
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start,
    ) {
        Column(modifier = Modifier.widthIn(max = 300.dp)) {

            // Метка отправителя
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text       = if (isOut) "вы" else contactName,
                    color      = colors.subtle,
                    fontSize   = 11.sp,
                    fontFamily = theme.typography.fontFamily,
                )
                // Кнопка отмены только для SENT (в очереди)
                if (isOut && msg.status == MessageStatus.SENT) {
                    Text(
                        text       = "  [отменить X]",
                        color      = colors.offline,
                        fontSize   = 11.sp,
                        fontFamily = theme.typography.fontFamily,
                        modifier   = Modifier.clickable { onCancel() }
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
                        text       = msg.text,
                        color      = if (isOut) colors.msgOutText else colors.msgInText,
                        fontSize   = 16.sp,
                        fontFamily = theme.typography.fontFamily,
                    )
                    Row(
                        modifier              = Modifier.align(Alignment.End).padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            text       = timeFormat.format(Date(msg.timestamp)),
                            color      = colors.subtle,
                            fontSize   = 11.sp,
                            fontFamily = theme.typography.fontFamily,
                        )
                        if (isOut) {
                            val (icon, color) = when (msg.status) {
                                MessageStatus.SENDING   -> "..."  to colors.subtle
                                MessageStatus.SENT      -> "v"    to colors.subtle
                                MessageStatus.DELIVERED -> "v"    to colors.primary
                                MessageStatus.READ      -> "vv"   to colors.online
                                MessageStatus.FAILED    -> "!"    to colors.offline
                            }
                            Text(
                                text       = icon,
                                color      = color,
                                fontSize   = 12.sp,
                                fontFamily = theme.typography.fontFamily,
                                fontWeight = FontWeight.Bold,
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
    text:          String,
    credits:       Int,
    nextCleanupMs: Long,
    onChange:      (String) -> Unit,
    onSend:        () -> Unit,
    theme:         KharonTheme,
) {
    val colors     = theme.colors
    val canSend    = text.isNotBlank() && credits > 0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Таймер следующей очистки
        if (nextCleanupMs > 0) {
            val remaining = ((nextCleanupMs - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            val mins      = remaining / 60
            val secs      = remaining % 60
            Text(
                text       = "Удаление через: ${if (mins > 0) "${mins}м " else ""}${secs}с",
                color      = if (remaining < 30) colors.offline else colors.subtle,
                fontSize   = 10.sp,
                fontFamily = theme.typography.fontFamily,
                modifier   = Modifier.padding(bottom = 2.dp),
            )
        }

        Row(
            modifier          = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value         = text,
                onValueChange = { if (it.length <= 500) onChange(it) },
                modifier      = Modifier
                    .weight(1f)
                    .background(colors.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                textStyle = TextStyle(
                    color      = colors.onSurface,
                    fontSize   = 16.sp,
                    fontFamily = theme.typography.fontFamily,
                ),
                cursorBrush = SolidColor(colors.primary),
                maxLines    = 4,
                decorationBox = { inner ->
                    if (text.isEmpty()) {
                        Text(
                            text       = "сообщение...",
                            color      = colors.subtle,
                            fontSize   = 16.sp,
                            fontFamily = theme.typography.fontFamily,
                        )
                    }
                    inner()
                }
            )

            Text(
                text       = "  [>]",
                color      = if (canSend) colors.primary else colors.subtle,
                fontSize   = 18.sp,
                fontFamily = theme.typography.fontFamily,
                fontWeight = FontWeight.Bold,
                modifier   = Modifier.clickable(enabled = canSend) { onSend() }
            )
        }
    }
}
