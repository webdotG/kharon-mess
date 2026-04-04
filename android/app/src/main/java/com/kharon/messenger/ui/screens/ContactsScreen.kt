package com.kharon.messenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kharon.messenger.model.Contact
import com.kharon.messenger.model.ReceptionMode
import com.kharon.messenger.network.ConnectionState
import com.kharon.messenger.ui.theme.KharonTheme
import com.kharon.messenger.ui.theme.KharonUI
import com.kharon.messenger.viewmodel.ContactsViewModel

@Composable
fun ContactsScreen(
    onContactClick: (Contact) -> Unit,
    onAddContact: () -> Unit,
    onSettingsClick: () -> Unit,
    currentMode: ReceptionMode,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val theme = KharonUI.current
    val colors = theme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
    ) {
        // --- Header ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.titleBar)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "KHARON_OS v2.0",
                color = colors.primary,
                fontSize = 18.sp,
                fontFamily = theme.typography.fontFamily,
                fontWeight = FontWeight.Bold
            )

        }

        // --- Network Status Bar ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val statusColor = when (state.connection) {
                is ConnectionState.Connected -> colors.online
                else -> colors.offline
            }
            Text(
                text = "NET: ${if (state.connection is ConnectionState.Connected) "ONLINE" else "OFFLINE"}",
                color = statusColor,
                fontSize = 14.sp,
                fontFamily = theme.typography.fontFamily
            )
            
            // ИСПРАВЛЕНО: Теперь выводит "ОКНО СВЯЗИ: 15 МИН" вместо "PULSE_15"
            Text(
                text = "MODE: ${currentMode.label}",
                color = colors.subtle,
                fontSize = 14.sp,
                fontFamily = theme.typography.fontFamily
            )
        }

        Text(
            text = " ".repeat(4) + "> CONTACT_LIST",
            color = colors.subtle,
            fontSize = 14.sp,
            fontFamily = theme.typography.fontFamily,
            modifier = Modifier.padding(12.dp)
        )

        // --- Contact List ---
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.contacts.withIndex().toList(), key = { it.value.pubKey }) { (idx, contact) ->
                ContactRow(
                    index = idx + 1,
                    contact = contact,
                    theme = theme,
                    onClick = { onContactClick(contact) },
                    onDelete = { viewModel.deleteContact(contact.pubKey) }
                )
            }
        }

        // --- Bottom Navigation / Actions ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                "[+] ADD_NODE", 
                color = colors.primary, 
                fontSize = 16.sp,
                fontFamily = theme.typography.fontFamily,
                modifier = Modifier.clickable { onAddContact() }
            )
            Text(
                "[*] CONFIG", 
                color = colors.subtle, 
                fontSize = 18.sp,
                fontFamily = theme.typography.fontFamily,
                modifier = Modifier.clickable { onSettingsClick() }
            )
        }
    }
}

@Composable
private fun ContactRow(
    index: Int,
    contact: Contact,
    theme: KharonTheme,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = theme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "[$index]",
                color = colors.subtle,
                fontSize = 18.sp,
                fontFamily = theme.typography.fontFamily,
            )
            Column {
                Text(
                    text = contact.name,
                    color = colors.onBackground,
                    fontSize = 18.sp,
                    fontFamily = theme.typography.fontFamily,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "ID: ${contact.pubKey.take(12)}...",
                        color = colors.subtle,
                        fontSize = 12.sp,
                        fontFamily = theme.typography.fontFamily,
                    )
                    val modeText = when {
                        contact.receptionMode == ReceptionMode.LIVE   -> "всегда онлайн"
                        contact.receptionMode == ReceptionMode.SILENT -> "тишина"
                        contact.receptionMode.minutes < 60            -> "окно ${contact.receptionMode.minutes}м"
                        else -> "окно ${contact.receptionMode.minutes / 60}ч"
                    }
                    Text(
                        text = modeText,
                        color = colors.subtle,
                        fontSize = 11.sp,
                        fontFamily = theme.typography.fontFamily,
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (contact.unreadCount > 0) {
                Text(
                    text = "[${contact.unreadCount}]",
                    color = colors.online,
                    fontSize = 14.sp,
                    fontFamily = theme.typography.fontFamily,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = "[удалить]",
                color = colors.offline,
                fontSize = 12.sp,
                fontFamily = theme.typography.fontFamily,
                modifier = Modifier.clickable { onDelete() },
            )
        }
    }
}