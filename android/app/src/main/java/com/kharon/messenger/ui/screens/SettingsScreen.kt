package com.kharon.messenger.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharon.messenger.model.ReceptionMode
import com.kharon.messenger.service.KharonForegroundService
import com.kharon.messenger.ui.theme.*

@Composable
fun SettingsScreen(
    currentThemeId: ThemeId,
    currentFontSize: FontSize,
    currentMode: ReceptionMode,
    onThemeSelect: (ThemeId) -> Unit,
    onFontSelect: (FontSize) -> Unit,
    onModeSelect: (ReceptionMode) -> Unit,
    onBack: () -> Unit,
) {
    val theme = KharonUI.current
    val colors = theme.colors
    val context = LocalContext.current
    val isTerminal = theme.id == ThemeId.TERMINAL_DARK || theme.id == ThemeId.TERMINAL_LIGHT

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.titleBar)
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isTerminal) "< " else "← ",
                color = colors.titleBarText,
                fontSize = 22.sp,
                fontFamily = theme.typography.fontFamily,
                modifier = Modifier.clickable { onBack() },
            )
            Text(
                text = if (isTerminal) "> SETTINGS" else "Settings",
                color = colors.titleBarText,
                fontSize = 20.sp,
                fontFamily = theme.typography.fontFamily,
                fontWeight = FontWeight.Bold,
            )
        }

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
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
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = fs.icon,
                                color = if (selected) colors.onPrimary else colors.onSurface,
                                fontSize = 20.sp,
                                fontFamily = theme.typography.fontFamily,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = fs.label,
                                color = if (selected) colors.onPrimary else colors.subtle,
                                fontSize = 14.sp,
                                fontFamily = theme.typography.fontFamily,
                            )
                        }
                    }
                }
            }

            SectionLabel("// NETWORK (RECEPTION MODE)", isTerminal, theme)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ReceptionMode.entries.forEach { mode ->
                    val selected = mode == currentMode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(theme.shapes.cardRadius))
                            .background(if (selected) colors.primary.copy(alpha = 0.1f) else Color.Transparent)
                            .clickable {
                                onModeSelect(mode)
                                val intent = Intent(context, KharonForegroundService::class.java).apply {
                                    action = KharonForegroundService.ACTION_START
                                    putExtra("EXTRA_MODE", mode.name)
                                }
                                context.startService(intent)
                            }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (selected && isTerminal) "> ${mode.label}" else mode.label,
                            color = if (selected) colors.primary else colors.onBackground,
                            fontSize = 18.sp,
                            fontFamily = theme.typography.fontFamily,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                        if (selected) {
                            Text(
                                text = if (isTerminal) "[*]" else "●",
                                color = colors.primary,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

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
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = t.id.icon,
                            color = if (selected) colors.primary else colors.subtle,
                            fontSize = 24.sp,
                            fontFamily = theme.typography.fontFamily,
                            fontWeight = FontWeight.Bold,
                        )
                        Column {
                            Text(
                                text = t.displayName,
                                color = if (selected) colors.primary else colors.onBackground,
                                fontSize = 18.sp,
                                fontFamily = theme.typography.fontFamily,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text = when (t.id) {
                                    ThemeId.DEFAULT -> "clean dark interface"
                                    ThemeId.TERMINAL_DARK -> ">_ green on black"
                                    ThemeId.TERMINAL_LIGHT -> "░▒ dark green on white"
                                    ThemeId.PRINCESS -> "✿ soft & sweet"
                                },
                                color = colors.subtle,
                                fontSize = 14.sp,
                                fontFamily = theme.typography.fontFamily,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, isTerminal: Boolean, theme: KharonTheme) {
    Text(
        text = text,
        color = theme.colors.subtle,
        fontSize = 14.sp,
        fontFamily = theme.typography.fontFamily,
    )
    if (isTerminal) {
        Text(
            text = theme.dimensions.dividerChar.repeat(40),
            color = theme.colors.divider,
            fontSize = 14.sp,
            fontFamily = theme.typography.fontFamily,
        )
    }
}