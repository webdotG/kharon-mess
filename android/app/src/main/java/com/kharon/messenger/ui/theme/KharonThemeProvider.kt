package com.kharon.messenger.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ─── Провайдер темы ───────────────────────────────────────────────────────────

@Composable
fun KharonThemeProvider(
    theme: KharonTheme,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalKharonTheme provides theme
    ) {
        Box(
            modifier = Modifier.background(theme.colors.background)
        ) {
            content()
        }
    }
}

// ─── Удобный accessor ─────────────────────────────────────────────────────────

object KharonUI {
    val current: KharonTheme
        @Composable get() = LocalKharonUI.current

    val colors: KharonColors
        @Composable get() = LocalKharonUI.current.colors

    val typography: KharonTypography
        @Composable get() = LocalKharonUI.current.typography

    val shapes: KharonShapes
        @Composable get() = LocalKharonUI.current.shapes

    val dims: KharonDimensions
        @Composable get() = LocalKharonUI.current.dimensions
}

// ─── Win95 рельефный border ───────────────────────────────────────────────────
//
// Рельеф Win95 — это два прямоугольника из линий:
// Внешний: верх+лево = белый, низ+право = тёмно-серый
// Внутренний: верх+лево = светло-серый, низ+право = чёрный
//
// Именно это создаёт эффект "выдавленной" или "вдавленной" кнопки.

fun Modifier.win95Border(
    highlight: Color = Color(0xFFFFFFFF),
    shadow: Color    = Color(0xFF808080),
    darkShadow: Color= Color(0xFF000000),
    face: Color      = Color(0xFFC0C0C0),
    width: Dp        = 2.dp,
    pressed: Boolean = false
): Modifier = composed {
    val outer1 = if (pressed) shadow    else highlight
    val outer2 = if (pressed) highlight else shadow
    val inner1 = if (pressed) darkShadow else face
    val inner2 = if (pressed) face      else darkShadow

    this.drawBehind {
        val w = width.toPx()
        val sw = size.width
        val sh = size.height

        // Внешняя рамка — верх и лево
        drawLine(outer1, Offset(0f, 0f), Offset(sw, 0f), w)
        drawLine(outer1, Offset(0f, 0f), Offset(0f, sh), w)
        // Внешняя рамка — низ и право
        drawLine(outer2, Offset(0f, sh), Offset(sw, sh), w)
        drawLine(outer2, Offset(sw, 0f), Offset(sw, sh), w)

        // Внутренняя рамка — верх и лево
        drawLine(inner1, Offset(w, w), Offset(sw - w, w), w)
        drawLine(inner1, Offset(w, w), Offset(w, sh - w), w)
        // Внутренняя рамка — низ и право
        drawLine(inner2, Offset(w, sh - w), Offset(sw - w, sh - w), w)
        drawLine(inner2, Offset(sw - w, w), Offset(sw - w, sh - w), w)
    }
}

// ─── Универсальный surface modifier с учётом темы ────────────────────────────

@Composable
fun Modifier.themedSurface(
    theme: KharonTheme = LocalKharonUI.current
): Modifier = when (theme.shapes.borderStyle) {
    BorderStyle.RAISED  -> this.win95Border(
        highlight  = theme.colors.buttonHighlight,
        shadow     = theme.colors.buttonShadow,
        darkShadow = Color(0xFF000000),
        face       = theme.colors.buttonFace,
    )
    BorderStyle.ROUNDED -> this
    BorderStyle.NONE    -> this
}
