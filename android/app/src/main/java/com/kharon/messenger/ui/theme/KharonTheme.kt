package com.kharon.messenger.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kharon.messenger.R

// ─── Типы ────────────────────────────────────────────────────────────────────

enum class ThemeId { MODERN, WIN95, ICQ, TERMINAL_DARK, TERMINAL_LIGHT }

enum class BorderStyle { NONE, ROUNDED, RAISED }  // RAISED — рельеф Win95

@Immutable
data class KharonColors(
    val background: Color,
    val surface: Color,          // карточки, панели
    val surfaceVariant: Color,   // чуть темнее surface
    val primary: Color,          // акцентный цвет
    val onPrimary: Color,        // текст на primary
    val onBackground: Color,     // основной текст
    val onSurface: Color,
    val subtle: Color,           // второстепенный текст, иконки
    val divider: Color,
    val msgOut: Color,           // пузырь исходящего сообщения
    val msgIn: Color,            // пузырь входящего
    val msgOutText: Color,
    val msgInText: Color,
    val titleBar: Color,         // Win95: синяя полоса заголовка
    val titleBarText: Color,
    val buttonFace: Color,       // Win95: цвет кнопки
    val buttonHighlight: Color,  // Win95: светлая грань рельефа
    val buttonShadow: Color,     // Win95: тёмная грань рельефа
    val online: Color,           // индикатор онлайн
    val offline: Color,
)

@Immutable
data class KharonTypography(
    val fontFamily: FontFamily,
    val titleSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    val bodySize: androidx.compose.ui.unit.TextUnit = 13.sp,
    val captionSize: androidx.compose.ui.unit.TextUnit = 11.sp,
    val fontWeight: FontWeight = FontWeight.Normal,
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
    val screenPadding: Dp = 16.dp,
    val itemSpacing: Dp = 8.dp,
)

@Immutable
data class KharonSounds(
    val messageReceived: Int?,   // R.raw.xxx или null
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

// ─── CompositionLocal ─────────────────────────────────────────────────────────

val LocalKharonTheme = staticCompositionLocalOf<KharonTheme> {
    ModernTheme  // дефолт
}

// ─── Modern тема ─────────────────────────────────────────────────────────────

val ModernTheme = KharonTheme(
    id          = ThemeId.MODERN,
    displayName = "Modern",
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
        fontFamily = FontFamily.Default,
        bodySize   = 15.sp,
        titleSize  = 16.sp,
        captionSize= 12.sp,
    ),
    shapes = KharonShapes(
        borderStyle          = BorderStyle.ROUNDED,
        messageBubbleRadius  = 18.dp,
        buttonRadius         = 10.dp,
        cardRadius           = 12.dp,
    ),
    sounds = KharonSounds(
        messageReceived = null,  // добавить R.raw.modern_receive
        messageSent     = null,
        connected       = null,
    )
)

// ─── Windows 95 тема ─────────────────────────────────────────────────────────
//
// Аутентичный Win95 стиль:
// - Серый фон #C0C0C0
// - Рельефные кнопки (светлая грань сверху-слева, тёмная снизу-справа)
// - Синяя полоса заголовка
// - Пиксельный шрифт

val Win95Theme = KharonTheme(
    id          = ThemeId.WIN95,
    displayName = "Windows 95",
    colors = KharonColors(
        background     = Color(0xFFC0C0C0),  // классический серый Win95
        surface        = Color(0xFFC0C0C0),
        surfaceVariant = Color(0xFFD4D0C8),
        primary        = Color(0xFF000080),  // синий заголовок
        onPrimary      = Color.White,
        onBackground   = Color(0xFF000000),
        onSurface      = Color(0xFF000000),
        subtle         = Color(0xFF808080),
        divider        = Color(0xFF808080),
        msgOut         = Color(0xFFFFFFFF),  // белый пузырь — как окно
        msgIn          = Color(0xFFFFFFFF),
        msgOutText     = Color(0xFF000000),
        msgInText      = Color(0xFF000000),
        titleBar       = Color(0xFF000080),  // тот самый синий
        titleBarText   = Color.White,
        buttonFace     = Color(0xFFC0C0C0),
        buttonHighlight= Color(0xFFFFFFFF),  // светлая грань — верх и лево
        buttonShadow   = Color(0xFF808080),  // тёмная грань — низ и право
        online         = Color(0xFF008000),
        offline        = Color(0xFF808080),
    ),
    typography = KharonTypography(
        fontFamily  = FontFamily.Monospace,  // имитация MS Sans Serif
        bodySize    = 13.sp,
        titleSize   = 13.sp,
        captionSize = 11.sp,
        fontWeight  = FontWeight.Normal,
    ),
    shapes = KharonShapes(
        borderStyle         = BorderStyle.RAISED,  // рельеф!
        messageBubbleRadius = 0.dp,                // квадратные углы
        buttonRadius        = 0.dp,
        cardRadius          = 0.dp,
    ),
    sounds = KharonSounds(
        messageReceived = null,  // добавить R.raw.win95_ding
        messageSent     = null,
        connected       = null,  // добавить R.raw.win95_startup
    )
)

// ─── ICQ тема ─────────────────────────────────────────────────────────────────
//
// Та самая аська:
// - Белый фон
// - Зелёный акцент (#5dbb46 — цветочек)
// - Характерные цвета пузырей

val IcqTheme = KharonTheme(
    id          = ThemeId.ICQ,
    displayName = "ICQ",
    colors = KharonColors(
        background     = Color(0xFFF5F5F5),
        surface        = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFFEEEEEE),
        primary        = Color(0xFF5DBB46),  // зелёный цветочек
        onPrimary      = Color.White,
        onBackground   = Color(0xFF222222),
        onSurface      = Color(0xFF222222),
        subtle         = Color(0xFF999999),
        divider        = Color(0xFFDDDDDD),
        msgOut         = Color(0xFFDCF8C6),  // светло-зелёный
        msgIn          = Color(0xFFFFFFFF),
        msgOutText     = Color(0xFF000000),
        msgInText      = Color(0xFF000000),
        titleBar       = Color(0xFF4CAF50),
        titleBarText   = Color.White,
        buttonFace     = Color(0xFF5DBB46),
        buttonHighlight= Color(0xFF81C784),
        buttonShadow   = Color(0xFF388E3C),
        online         = Color(0xFF5DBB46),  // тот самый зелёный
        offline        = Color(0xFFFF6B6B),  // красный цветочек
    ),
    typography = KharonTypography(
        fontFamily  = FontFamily.SansSerif,
        bodySize    = 13.sp,
        titleSize   = 14.sp,
        captionSize = 11.sp,
    ),
    shapes = KharonShapes(
        borderStyle         = BorderStyle.ROUNDED,
        messageBubbleRadius = 8.dp,   // слегка скруглённые — как оригинал
        buttonRadius        = 4.dp,
        cardRadius          = 4.dp,
    ),
    sounds = KharonSounds(
        messageReceived = null,  // добавить R.raw.icq_uhoh (то самое "uh oh")
        messageSent     = null,  // добавить R.raw.icq_send
        connected       = null,
    )
)

// ─── Реестр тем ──────────────────────────────────────────────────────────────

val AllThemes get() = listOf(ModernTheme, Win95Theme, IcqTheme, TerminalDarkTheme, TerminalLightTheme)

fun themeById(id: ThemeId): KharonTheme = when (id) {
    ThemeId.MODERN -> ModernTheme
    ThemeId.WIN95  -> Win95Theme
    ThemeId.ICQ           -> IcqTheme
    ThemeId.TERMINAL_DARK  -> TerminalDarkTheme
    ThemeId.TERMINAL_LIGHT -> TerminalLightTheme
}

// Алиас для совместимости с KharonUI object
val LocalKharonUI = LocalKharonTheme


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
    typography = KharonTypography(
        fontFamily  = FontFamily.Monospace,
        bodySize    = 13.sp,
        titleSize   = 14.sp,
        captionSize = 11.sp,
    ),
    shapes = KharonShapes(
        borderStyle         = BorderStyle.NONE,
        messageBubbleRadius = 0.dp,
        buttonRadius        = 0.dp,
        cardRadius          = 0.dp,
    ),
    sounds = KharonSounds(null, null, null)
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
    typography = KharonTypography(
        fontFamily  = FontFamily.Monospace,
        bodySize    = 13.sp,
        titleSize   = 14.sp,
        captionSize = 11.sp,
    ),
    shapes = KharonShapes(
        borderStyle         = BorderStyle.NONE,
        messageBubbleRadius = 0.dp,
        buttonRadius        = 0.dp,
        cardRadius          = 0.dp,
    ),
    sounds = KharonSounds(null, null, null)
)
