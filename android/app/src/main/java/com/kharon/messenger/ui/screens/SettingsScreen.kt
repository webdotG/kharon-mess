package com.kharon.messenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kharon.messenger.ui.theme.*

@Composable
fun SettingsScreen(
    currentThemeId: ThemeId,
    onThemeSelect:  (ThemeId) -> Unit,
    onBack:         () -> Unit,
) {
    val theme  = KharonUI.current
    val colors = theme.colors

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
    ) {
        // ── Title bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.titleBar)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text       = "<",
                color      = colors.titleBarText,
                fontSize   = theme.typography.titleSize,
                fontFamily = theme.typography.fontFamily,
                modifier   = Modifier.clickable { onBack() }.padding(end = 12.dp),
            )
            Text(
                text       = "> SETTINGS",
                color      = colors.titleBarText,
                fontSize   = theme.typography.titleSize,
                fontFamily = theme.typography.fontFamily,
                fontWeight = FontWeight.Bold,
            )
        }

        // ── Темы ─────────────────────────────────────────────────────────────
        Column(modifier = Modifier.padding(12.dp)) {

            Text(
                text       = "// DISPLAY THEME",
                color      = colors.subtle,
                fontSize   = theme.typography.captionSize,
                fontFamily = theme.typography.fontFamily,
                modifier   = Modifier.padding(bottom = 8.dp),
            )

            Text(
                text       = "─".repeat(60),
                color      = colors.divider,
                fontSize   = theme.typography.captionSize,
                fontFamily = theme.typography.fontFamily,
                modifier   = Modifier.padding(bottom = 8.dp),
            )

            AllThemes.forEachIndexed { idx, t ->
                val selected = t.id == currentThemeId

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (selected) colors.primary.copy(alpha = 0.1f)
                            else colors.background
                        )
                        .clickable { onThemeSelect(t.id) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Text(
                            text       = "[${idx + 1}]",
                            color      = colors.subtle,
                            fontSize   = theme.typography.bodySize,
                            fontFamily = theme.typography.fontFamily,
                        )
                        Column {
                            Text(
                                text       = t.displayName,
                                color      = if (selected) colors.primary else colors.onBackground,
                                fontSize   = theme.typography.bodySize,
                                fontFamily = theme.typography.fontFamily,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text = when (t.id) {
                                    ThemeId.MODERN         -> "// clean dark interface"
                                    ThemeId.WIN95          -> "// classic windows 95"
                                    ThemeId.ICQ            -> "// icq messenger style"
                                    ThemeId.TERMINAL_DARK  -> "// green on black, matrix style"
                                    ThemeId.TERMINAL_LIGHT -> "// dark green on white, dec terminal"
                                },
                                color      = colors.subtle,
                                fontSize   = theme.typography.captionSize,
                                fontFamily = theme.typography.fontFamily,
                            )
                        }
                    }

                    if (selected) {
                        Text(
                            text       = "[*]",
                            color      = colors.primary,
                            fontSize   = theme.typography.bodySize,
                            fontFamily = theme.typography.fontFamily,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Text(
                    text       = "─".repeat(60),
                    color      = colors.divider,
                    fontSize   = theme.typography.captionSize,
                    fontFamily = theme.typography.fontFamily,
                )
            }
        }
    }
}
