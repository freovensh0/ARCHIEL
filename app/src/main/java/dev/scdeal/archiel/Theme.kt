// Tokens come from design/: palette.py --from-hex "#e0a458" --mood crisp --scheme mono, audited by
// contrast.py. The structure is borrowed from lunanoir21/tally-omarchy: a ground, a text colour, one
// accent, and two mixed from them. Dark only until the app earns a light variant.
package dev.scdeal.archiel

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val Noir = darkColorScheme(
    primary = Color(0xFFE0A458),
    onPrimary = Color(0xFF14110C),
    primaryContainer = Color(0xFF2A2013),
    onPrimaryContainer = Color(0xFFE0A458),
    background = Color(0xFF0F0F10),
    onBackground = Color(0xFFECECEC),
    surface = Color(0xFF17171A),
    onSurface = Color(0xFFECECEC),
    surfaceVariant = Color(0xFF1C1C1F),
    onSurfaceVariant = Color(0xFFA8A8AD),
    outline = Color(0xFF26262A),
    outlineVariant = Color(0xFF26262A),
    error = Color(0xFFDC615A),
    onError = Color(0xFF1B0B0A),
)

/** Amber as a fill rather than as text: the same value in every surface, with ink on top of it. */
val AccentFill = Color(0xFFE0A458)
val OnAccent = Color(0xFF14110C)

private val Instrument = FontFamily(
    Font(R.font.instrument_sans, FontWeight.Normal),
    Font(R.font.instrument_sans, FontWeight.Medium),
    Font(R.font.instrument_sans, FontWeight.SemiBold),
)

private val Syne = FontFamily(Font(R.font.syne, FontWeight.Bold))

val Mono = FontFamily(Font(R.font.dm_mono, FontWeight.Normal))

private val ArchielType = Typography(
    displaySmall = TextStyle(fontFamily = Syne, fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em),
    headlineSmall = TextStyle(fontFamily = Syne, fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(fontFamily = Instrument, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontFamily = Instrument, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = Instrument, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Instrument, fontSize = 13.5.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Instrument, fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(fontFamily = Mono, fontSize = 11.sp, lineHeight = 16.sp, color = Color(0xFFA8A8AD)),
)

private val ArchielShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.chip),
    small = RoundedCornerShape(Radius.control),
    medium = RoundedCornerShape(Radius.panel),
    large = RoundedCornerShape(Radius.panel),
    extraLarge = RoundedCornerShape(Radius.panel),
)

/** The 4 px ladder: 4 / 8 / 12 / 16 / 24 / 32 */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object Radius {
    val chip = 5.dp
    val control = 10.dp
    val panel = 16.dp
    val pill = 999.dp
}

object Size {
    val bar = 52.dp
    val hit = 44.dp
    val hitSmall = 40.dp
    val dot = 6.dp
    val row = 64.dp
    val caretWidth = 8.dp
    val caretHeight = 16.dp
    val hairline = 1.dp
}

@Composable
fun ArchielTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Noir,
        typography = ArchielType,
        shapes = ArchielShapes,
        content = content,
    )
}
