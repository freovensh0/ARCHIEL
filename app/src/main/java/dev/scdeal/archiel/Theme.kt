// Concept: "Bir mühendis panosu: kömür grisi zemin, tek amber sinyal, düz yüzeyler, ölçülü tipografi."
// Token'lar spec-mockup-design/scripts/palette.py (--hue 75 --mood crisp --scheme mono)
// ve scales.py (--target mobile --density normal) çıktılarından birebir alındı.
// Yazı tipleri referans projenin kendi fontları: Instrument Sans (gövde), Syne (display), DM Mono (kod).
package dev.scdeal.archiel

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

private val Ink = darkColorScheme(
    primary = Color(0xFFDB9E3D),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF312007),
    onPrimaryContainer = Color(0xFFDB9E3D),
    background = Color(0xFF100F0E),
    onBackground = Color(0xFFF0EEEC),
    surface = Color(0xFF161513),
    onSurface = Color(0xFFF0EEEC),
    surfaceVariant = Color(0xFF1B1A19),
    onSurfaceVariant = Color(0xFFA09E9B),
    surfaceContainer = Color(0xFF1B1A19),
    surfaceContainerHigh = Color(0xFF1B1A19),
    outline = Color(0xFF2A2928),
    outlineVariant = Color(0xFF2A2928),
    error = Color(0xFFF98478),
    onError = Color(0xFF2A0906),
    inverseSurface = Color(0xFFF0EEEC),
    inverseOnSurface = Color(0xFF100F0E),
)

private val Paper = lightColorScheme(
    primary = Color(0xFF7E5403),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFBE4C6),
    onPrimaryContainer = Color(0xFF7E5403),
    background = Color(0xFFF5F3F1),
    onBackground = Color(0xFF131110),
    surface = Color(0xFFFBF9F7),
    onSurface = Color(0xFF131110),
    surfaceVariant = Color(0xFFFEFDFC),
    onSurfaceVariant = Color(0xFF6B6866),
    surfaceContainer = Color(0xFFFEFDFC),
    surfaceContainerHigh = Color(0xFFFEFDFC),
    outline = Color(0xFFD9D7D5),
    outlineVariant = Color(0xFFD9D7D5),
    error = Color(0xFF9F322D),
    onError = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF131110),
    inverseOnSurface = Color(0xFFF5F3F1),
)

private val Instrument = FontFamily(
    Font(R.font.instrument_sans, FontWeight.Normal),
    Font(R.font.instrument_sans, FontWeight.Medium),
    Font(R.font.instrument_sans, FontWeight.SemiBold),
    Font(R.font.instrument_sans, FontWeight.Bold),
)

private val Syne = FontFamily(Font(R.font.syne, FontWeight.Bold))

val Mono = FontFamily(Font(R.font.dm_mono, FontWeight.Normal))

private val ArchielType = Typography(
    displaySmall = TextStyle(Syne, fontSize = 33.sp, lineHeight = 38.sp, letterSpacing = (-0.01).em),
    headlineSmall = TextStyle(Syne, fontSize = 23.sp, lineHeight = 28.sp, letterSpacing = (-0.01).em),
    titleLarge = TextStyle(Instrument, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(Instrument, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(Instrument, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(Instrument, fontSize = 13.5.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(Instrument, fontSize = 13.5.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelSmall = TextStyle(Instrument, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.04.em),
)

private val ArchielShapes = Shapes(
    extraSmall = RoundedCornerShape(5.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(16.dp),
)

/** 4 px tabanlı merdiven: 4 / 8 / 12 / 16 / 24 / 32 */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

@Composable
fun ArchielTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) Ink else Paper,
        typography = ArchielType,
        shapes = ArchielShapes,
        content = content,
    )
}
