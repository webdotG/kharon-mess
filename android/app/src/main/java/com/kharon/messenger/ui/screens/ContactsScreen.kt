package com.kharon.messenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kharon.messenger.model.Contact
import com.kharon.messenger.network.ConnectionState
import com.kharon.messenger.ui.theme.KharonTheme
import com.kharon.messenger.ui.theme.KharonUI
import com.kharon.messenger.ui.theme.ThemeId
import com.kharon.messenger.viewmodel.ContactsViewModel

private val LOGO = """
 ██╗  ██╗██╗  ██╗ █████╗ ██████╗  ██████╗ ███╗  ██╗
 ██║ ██╔╝██║  ██║██╔══██╗██╔══██╗██╔═══██╗████╗ ██║
 █████╔╝ ███████║███████║██████╔╝██║   ██║██╔██╗██║
 ██╔═██╗ ██╔══██║██╔══██║██╔══██╗██║   ██║██║╚████║
 ██║  ██╗██║  ██║██║  ██║██║  ██║╚██████╔╝██║ ╚███║
 ╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝╚═╝  ╚═╝ ╚═════╝ ╚═╝  ╚══╝
 ──────────────────── MESSENGER v1.0 ─────────────────
""".trimIndent()

@Composable
fun ContactsScreen(
    onContactClick:  (Contact) -> Unit,
    onAddContact:    () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val state  by viewModel.uiState.collectAsState()
    val theme   = KharonUI.current
    val colors  = theme.colors
    val isTerminal = theme.id == ThemeId.TERMINAL_DARK || theme.id == ThemeId.TERMINAL_LIGHT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
    ) {
        // ── Лого ─────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.titleBar)
                .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            Text(
                text       = LOGO,
                color      = colors.primary,
                fontSize   = 6.sp,
                fontFamily = theme.typography.fontFamily,
                lineHeight = 7.sp,
            )
        }

        // ── Статусная строка ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            val statusText = when (state.connection) {
                is ConnectionState.Connected    -> if (isTerminal) "[*] CONNECTED" else "● online"
                is ConnectionState.Connecting   -> if (isTerminal) "[~] CONNECTING..." else "○ connecting"
                is ConnectionState.Disconnected -> if (isTerminal) "[!] OFFLINE" else "○ offline"
                is ConnectionState.Error        -> if (isTerminal) "[!] ERROR" else "✗ error"
            }
            val statusColor = when (state.connection) {
                is ConnectionState.Connected    -> colors.online
                is ConnectionState.Connecting   -> colors.subtle
                is ConnectionState.Disconnected -> colors.offline
                is ConnectionState.Error        -> colors.offline
            }
            Text(
                text       = statusText,
                color      = statusColor,
                fontSize   = theme.typography.captionSize,
                fontFamily = theme.typography.fontFamily,
            )

            Text(
                    text       = "[+]",
                    color      = colors.primary,
                    fontSize   = theme.typography.captionSize,
                    fontFamily = theme.typography.fontFamily,
                    fontWeight = FontWeight.Bold,
                    modifier   = Modifier.clickable { onAddContact() },
                )
        }

        // ── Заголовок таблицы ─────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text       = if (isTerminal) "> CONTACTS (${state.contacts.size})" else "Contacts (${state.contacts.size})",
                color      = colors.subtle,
                fontSize   = theme.typography.captionSize,
                fontFamily = theme.typography.fontFamily,
            )
        }

        Text(
            text       = "─".repeat(60),
            color      = colors.divider,
            fontSize   = theme.typography.captionSize,
            fontFamily = theme.typography.fontFamily,
            modifier   = Modifier.padding(horizontal = 12.dp),
        )

        // ── Список ────────────────────────────────────────────────────────────
        if (state.contacts.isEmpty()) {
            Box(
                modifier         = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = "[!] No contacts.\n    Press [+] to add a colleague.",
                    color      = colors.subtle,
                    fontSize   = theme.typography.bodySize,
                    fontFamily = theme.typography.fontFamily,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.contacts.withIndex().toList(), key = { it.value.pubKey }) { (idx, contact) ->
                    ContactRow(
                        index    = idx + 1,
                        contact  = contact,
                        theme    = theme,
                        onClick  = { onContactClick(contact) },
                        onDelete = { viewModel.deleteContact(contact.pubKey) },
                    )
                }
            }
        }

        // ── Нижняя строка ─────────────────────────────────────────────────────
        Text(
            text       = "─".repeat(60),
            color      = colors.divider,
            fontSize   = theme.typography.captionSize,
            fontFamily = theme.typography.fontFamily,
            modifier   = Modifier.padding(horizontal = 12.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text("[+] Add", color = colors.primary, fontSize = theme.typography.captionSize,
                fontFamily = theme.typography.fontFamily,
                modifier = Modifier.clickable { onAddContact() })
            Text("[S] Settings", color = colors.subtle, fontSize = theme.typography.captionSize,
                fontFamily = theme.typography.fontFamily,
                modifier = Modifier.clickable { onSettingsClick() })
        }
    }
}

@Composable
private fun ContactRow(
    index:    Int,
    contact:  Contact,
    theme:    KharonTheme,
    onClick:  () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = theme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.weight(1f),
        ) {
            Text(
                text       = "[$index]",
                color      = colors.subtle,
                fontSize   = theme.typography.bodySize,
                fontFamily = theme.typography.fontFamily,
            )
            Text(
                text       = contact.name.padEnd(16),
                color      = colors.onBackground,
                fontSize   = theme.typography.bodySize,
                fontFamily = theme.typography.fontFamily,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text       = contact.pubKey.take(8) + "..",
                color      = colors.subtle,
                fontSize   = theme.typography.captionSize,
                fontFamily = theme.typography.fontFamily,
            )
        }

        Text(
            text       = "[x]",
            color      = colors.offline,
            fontSize   = theme.typography.captionSize,
            fontFamily = theme.typography.fontFamily,
            modifier   = Modifier.clickable { onDelete() },
        )
    }
}
