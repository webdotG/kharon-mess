package com.kharon.messenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kharon.messenger.ui.theme.*

@Composable
fun SettingsScreen(
    currentThemeId: ThemeId,
    currentFontSize: FontSize,
    onThemeSelect:  (ThemeId) -> Unit,
    onFontSelect:   (FontSize) -> Unit,
    onBack:         () -> Unit,
) {
    val theme  = KharonUI.current
    val colors = theme.colors
    val isTerminal = theme.id == ThemeId.TERMINAL_DARK || theme.id == ThemeId.TERMINAL_LIGHT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Title bar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.titleBar)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text       = if (isTerminal) "< " else "← ",
                color      = colors.titleBarText,
                fontSize   = theme.typography.titleSize,
                fontFamily = theme.typography.fontFamily,
                modifier   = Modifier.clickable { onBack() },
            )
            Text(
                text       = if (isTerminal) "> SETTINGS" else "Settings",
                color      = colors.titleBarText,
                fontSize   = theme.typography.titleSize,
                fontFamily = theme.typography.fontFamily,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

            // ── Размер шрифта ─────────────────────────────────────────────────
            SectionLabel("// FONT SIZE", isTerminal, theme)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FontSize.entries.forEach { fs ->
                    val selected = fs == currentFontSize
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(theme.shapes.buttonRadius))
                            .background(if (selected) colors.primary else colors.surfaceVariant)
                            .clickable { onFontSelect(fs) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text       = fs.icon,
                                color      = if (selected) colors.onPrimary else colors.onSurface,
                                fontSize   = fs.body,
                                fontFamily = theme.typography.fontFamily,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text       = fs.label,
                                color      = if (selected) colors.onPrimary else colors.subtle,
                                fontSize   = theme.typography.captionSize,
                                fontFamily = theme.typography.fontFamily,
                            )
                        }
                    }
                }
            }

            // ── Тема ──────────────────────────────────────────────────────────
            SectionLabel("// THEME", isTerminal, theme)

            AllThemes.forEach { t ->
                val selected = t.id == currentThemeId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(theme.shapes.cardRadius))
                        .background(
                            if (selected) colors.primary.copy(alpha = 0.15f)
                            else colors.surface
                        )
                        .clickable { onThemeSelect(t.id) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        // Иконка темы
                        Text(
                            text       = t.id.icon,
                            color      = if (selected) colors.primary else colors.subtle,
                            fontSize   = theme.typography.titleSize,
                            fontFamily = theme.typography.fontFamily,
                            fontWeight = FontWeight.Bold,
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
                                    ThemeId.DEFAULT        -> "clean dark interface"
                                    ThemeId.TERMINAL_DARK  -> ">_ green on black"
                                    ThemeId.TERMINAL_LIGHT -> "░▒ dark green on white"
                                    ThemeId.PRINCESS       -> "✿ soft & sweet"
                                },
                                color      = colors.subtle,
                                fontSize   = theme.typography.captionSize,
                                fontFamily = theme.typography.fontFamily,
                            )
                        }
                    }
                    if (selected) {
                        Text(
                            text       = if (isTerminal) "[*]" else "✓",
                            color      = colors.primary,
                            fontSize   = theme.typography.bodySize,
                            fontFamily = theme.typography.fontFamily,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, isTerminal: Boolean, theme: KharonTheme) {
    Text(
        text       = text,
        color      = theme.colors.subtle,
        fontSize   = theme.typography.captionSize,
        fontFamily = theme.typography.fontFamily,
    )
    if (isTerminal) {
        Text(
            text       = theme.dimensions.dividerChar.repeat(40),
            color      = theme.colors.divider,
            fontSize   = theme.typography.captionSize,
            fontFamily = theme.typography.fontFamily,
        )
    }
}
