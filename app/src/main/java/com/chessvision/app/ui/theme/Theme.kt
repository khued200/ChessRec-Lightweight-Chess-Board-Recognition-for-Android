package com.chessvision.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.chessvision.app.R

// ---- palette ----
val Felt = Color(0xFF132019)
val FeltDeep = Color(0xFF0D1712)
val Panel = Color(0xFF1A2921)
val PanelLine = Color(0x1AEDE6D6)
val Ivory = Color(0xFFEDE6D6)
val Walnut = Color(0xFF8A6644)
val WalnutDark = Color(0xFF4A3320)
val Brass = Color(0xFFC9A227)
val BrassHi = Color(0xFFE2C25A)
val TextMuted = Color(0xFF9FAC9C)
val Danger = Color(0xFFC1553B)

// Lichess' default "brown" board colors — used for the actual board/piece
// rendering in ResultScreen (kept separate from the felt/walnut app chrome).
val BoardLight = Color(0xFFF0D9B5)
val BoardDark = Color(0xFFB58863)

// Falls back to the system serif/monospace so the project builds with zero
// bundled font files. Drop real .ttf files into res/font and swap these
// FontFamily.Default calls for FontFamily(Font(R.font.lora_medium)) etc.
// to match the web version exactly (Lora + IBM Plex Mono).
val DisplayFont = FontFamily.Serif
val MonoFont = FontFamily.Monospace

private val ChessVisionColors = darkColorScheme(
    background = Felt,
    surface = Panel,
    primary = Brass,
    onPrimary = Color(0xFF241A05),
    onBackground = Ivory,
    onSurface = Ivory,
    secondary = TextMuted,
    error = Danger,
)

@Composable
fun ChessVisionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChessVisionColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
