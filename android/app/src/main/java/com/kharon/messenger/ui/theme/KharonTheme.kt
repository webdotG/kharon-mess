package com.kharon.messenger.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ─── Размер шрифта ────────────────────────────────────────────────────────────

enum class FontSize(val label: String, val icon: String, val body: TextUnit, val title: TextUnit, val caption: TextUnit) {
    SMALL  ("Small",  "A",  13.sp, 14.sp, 11.sp),
    MEDIUM ("Medium", "A+", 15.sp, 16.sp, 12.sp),
    LARGE  ("Large",  "A++",17.sp, 18.sp, 14.sp),
}

// ─── Темы ─────────────────────────────────────────────────────────────────────

enum class ThemeId(val icon: String) {
    DEFAULT       (">_<"),
    TERMINAL_DARK (">_!"),
    TERMINAL_LIGHT("о_0"),
    PRINCESS      ("♡"),
}

enum class BorderStyle { NONE, ROUNDED }

@Immutable
data class KharonColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val onBackground: Color,
    val onSurface: Color,
    val subtle: Color,
    val divider: Color,
    val msgOut: Color,
    val msgIn: Color,
    val msgOutText: Color,
    val msgInText: Color,
    val titleBar: Color,
    val titleBarText: Color,
    val buttonFace: Color,
    val buttonHighlight: Color,
    val buttonShadow: Color,
    val online: Color,
    val offline: Color,
)

@Immutable
data class KharonTypography(
    val fontFamily: FontFamily,
    val bodySize: TextUnit   = 15.sp,
    val titleSize: TextUnit  = 16.sp,
    val captionSize: TextUnit= 12.sp,
)

@Immutable
data class KharonShapes(
    val borderStyle: BorderStyle,
    val messageBubbleRadius: Dp,
    val buttonRadius: Dp,
    val cardRadius: Dp,
)

@Immutable
data class KharonDimensions(
    val messagePaddingH: Dp = 12.dp,
    val messagePaddingV: Dp = 8.dp,
    val screenPadding: Dp   = 16.dp,
    val itemSpacing: Dp     = 8.dp,
    val dividerChar: String = "─",  // тонкая линия
)

@Immutable
data class KharonSounds(
    val messageReceived: Int?,
    val messageSent: Int?,
    val connected: Int?,
)

@Immutable
data class KharonTheme(
    val id: ThemeId,
    val displayName: String,
    val colors: KharonColors,
    val typography: KharonTypography,
    val shapes: KharonShapes,
    val dimensions: KharonDimensions = KharonDimensions(),
    val sounds: KharonSounds,
)

// ─── CompositionLocals ───────────────────────────────────────────────────────

val LocalKharonTheme = staticCompositionLocalOf<KharonTheme> { DefaultTheme }
val LocalKharonUI    = LocalKharonTheme
val LocalFontSize    = staticCompositionLocalOf<FontSize> { FontSize.MEDIUM }

// ─── Default (тёмная) ────────────────────────────────────────────────────────

val DefaultTheme = KharonTheme(
    id          = ThemeId.DEFAULT,
    displayName = "Default",
    colors = KharonColors(
        background     = Color(0xFF0F0F0F),
        surface        = Color(0xFF1C1C1E),
        surfaceVariant = Color(0xFF2C2C2E),
        primary        = Color(0xFF0A84FF),
        onPrimary      = Color.White,
        onBackground   = Color(0xFFFFFFFF),
        onSurface      = Color(0xFFEEEEEE),
        subtle         = Color(0xFF8E8E93),
        divider        = Color(0xFF38383A),
        msgOut         = Color(0xFF0A84FF),
        msgIn          = Color(0xFF2C2C2E),
        msgOutText     = Color.White,
        msgInText      = Color(0xFFFFFFFF),
        titleBar       = Color(0xFF1C1C1E),
        titleBarText   = Color.White,
        buttonFace     = Color(0xFF2C2C2E),
        buttonHighlight= Color(0xFF48484A),
        buttonShadow   = Color(0xFF000000),
        online         = Color(0xFF34C759),
        offline        = Color(0xFF8E8E93),
    ),
    typography = KharonTypography(
        fontFamily  = FontFamily.Default,
        bodySize    = 15.sp,
        titleSize   = 16.sp,
        captionSize = 12.sp,
    ),
    shapes = KharonShapes(
        borderStyle         = BorderStyle.ROUNDED,
        messageBubbleRadius = 18.dp,
        buttonRadius        = 10.dp,
        cardRadius          = 12.dp,
    ),
    sounds = KharonSounds(null, null, null),
)

// ─── Terminal Dark ────────────────────────────────────────────────────────────

val TerminalDarkTheme = KharonTheme(
    id          = ThemeId.TERMINAL_DARK,
    displayName = "Terminal Dark",
    colors = KharonColors(
        background     = Color(0xFF0D0D0D),
        surface        = Color(0xFF111111),
        surfaceVariant = Color(0xFF1A1A1A),
        primary        = Color(0xFF00FF41),
        onPrimary      = Color(0xFF000000),
        onBackground   = Color(0xFF00FF41),
        onSurface      = Color(0xFF00FF41),
        subtle         = Color(0xFF008F11),
        divider        = Color(0xFF003B00),
        msgOut         = Color(0xFF001A00),
        msgIn          = Color(0xFF0D0D0D),
        msgOutText     = Color(0xFF00FF41),
        msgInText      = Color(0xFF00CC33),
        titleBar       = Color(0xFF000000),
        titleBarText   = Color(0xFF00FF41),
        buttonFace     = Color(0xFF001A00),
        buttonHighlight= Color(0xFF00FF41),
        buttonShadow   = Color(0xFF003300),
        online         = Color(0xFF00FF41),
        offline        = Color(0xFFFF3333),
    ),
    typography = KharonTypography(fontFamily = FontFamily.Monospace),
    shapes = KharonShapes(
        borderStyle         = BorderStyle.NONE,
        messageBubbleRadius = 0.dp,
        buttonRadius        = 0.dp,
        cardRadius          = 0.dp,
    ),
    dimensions = KharonDimensions(dividerChar = "─"),
    sounds = KharonSounds(null, null, null),
)

// ─── Terminal Light ───────────────────────────────────────────────────────────

val TerminalLightTheme = KharonTheme(
    id          = ThemeId.TERMINAL_LIGHT,
    displayName = "Terminal Light",
    colors = KharonColors(
        background     = Color(0xFFF5F5F0),
        surface        = Color(0xFFEEEEE8),
        surfaceVariant = Color(0xFFE0E0D8),
        primary        = Color(0xFF006400),
        onPrimary      = Color(0xFFFFFFFF),
        onBackground   = Color(0xFF003300),
        onSurface      = Color(0xFF003300),
        subtle         = Color(0xFF4A7A4A),
        divider        = Color(0xFF90A890),
        msgOut         = Color(0xFFDDEEDD),
        msgIn          = Color(0xFFF5F5F0),
        msgOutText     = Color(0xFF003300),
        msgInText      = Color(0xFF003300),
        titleBar       = Color(0xFF003300),
        titleBarText   = Color(0xFFF5F5F0),
        buttonFace     = Color(0xFFDDEEDD),
        buttonHighlight= Color(0xFF006400),
        buttonShadow   = Color(0xFF90A890),
        online         = Color(0xFF006400),
        offline        = Color(0xFF8B0000),
    ),
    typography = KharonTypography(fontFamily = FontFamily.Monospace),
    shapes = KharonShapes(
        borderStyle         = BorderStyle.NONE,
        messageBubbleRadius = 0.dp,
        buttonRadius        = 0.dp,
        cardRadius          = 0.dp,
    ),
    dimensions = KharonDimensions(dividerChar = "─"),
    sounds = KharonSounds(null, null, null),
)

// ─── Princess ─────────────────────────────────────────────────────────────────
// Нежная тема: розово-фиолетово-голубые тона, котики и смайлики

val PrincessTheme = KharonTheme(
    id          = ThemeId.PRINCESS,
    displayName = "Princess",
    colors = KharonColors(
        background     = Color(0xFFFFF0F5),  // лавандовый румянец
        surface        = Color(0xFFFFE4F0),
        surfaceVariant = Color(0xFFFFD6E8),
        primary        = Color(0xFFD63384),  // малиновый
        onPrimary      = Color.White,
        onBackground   = Color(0xFF4A1040),  // тёмно-фиолетовый
        onSurface      = Color(0xFF4A1040),
        subtle         = Color(0xFFB07090),
        divider        = Color(0xFFFFB3D9),
        msgOut         = Color(0xFFD63384),
        msgIn          = Color(0xFFFFE4F0),
        msgOutText     = Color.White,
        msgInText      = Color(0xFF4A1040),
        titleBar       = Color(0xFFFF69B4),  // горячий розовый
        titleBarText   = Color.White,
        buttonFace     = Color(0xFFFFB3D9),
        buttonHighlight= Color(0xFFFF69B4),
        buttonShadow   = Color(0xFFD63384),
        online         = Color(0xFF9B59B6),  // фиолетовый
        offline        = Color(0xFFCCCCCC),
    ),
    typography = KharonTypography(
        fontFamily  = FontFamily.Default,
        bodySize    = 15.sp,
        titleSize   = 16.sp,
        captionSize = 12.sp,
    ),
    shapes = KharonShapes(
        borderStyle         = BorderStyle.ROUNDED,
        messageBubbleRadius = 20.dp,
        buttonRadius        = 20.dp,
        cardRadius          = 16.dp,
    ),
    sounds = KharonSounds(null, null, null),
)

// ─── Реестр ───────────────────────────────────────────────────────────────────

val AllThemes get() = listOf(DefaultTheme, TerminalDarkTheme, TerminalLightTheme, PrincessTheme)

fun themeById(id: ThemeId): KharonTheme = when (id) {
    ThemeId.DEFAULT        -> DefaultTheme
    ThemeId.TERMINAL_DARK  -> TerminalDarkTheme
    ThemeId.TERMINAL_LIGHT -> TerminalLightTheme
    ThemeId.PRINCESS       -> PrincessTheme
}
