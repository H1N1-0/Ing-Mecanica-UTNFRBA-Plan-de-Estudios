package ar.correlativas.mecanica.ui

import android.graphics.Typeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Paleta de la versión web ("rótulo" de plano técnico). */
@Immutable
data class Paleta(
    val papel: Color,
    val superficie: Color,
    val tinta: Color,
    val tinta2: Color,
    val linea: Color,
    val lineaFuerte: Color,
    val grilla: Color,
    val acento: Color,
    val acentoFondo: Color,
    val reg: Color,
    val regFondo: Color,
    val sobreReg: Color,
    val apr: Color,
    val aprFondo: Color,
    val sobreApr: Color,
    val apagado: Color,
    val neutroFondo: Color,
    val peligro: Color,
)

val PaletaClara = Paleta(
    papel = Color(0xFFF3F5F8), superficie = Color(0xFFFFFFFF), tinta = Color(0xFF18222F),
    tinta2 = Color(0xFF506074), linea = Color(0xFFC9D2DC), lineaFuerte = Color(0xFF18222F),
    grilla = Color(0x171F5FAD), acento = Color(0xFF1F5FAD), acentoFondo = Color(0xFFE4EDF9),
    reg = Color(0xFF9E5B00), regFondo = Color(0xFFFFF0D3), sobreReg = Color.White,
    apr = Color(0xFF1A7743), aprFondo = Color(0xFFDDF1E4), sobreApr = Color.White,
    apagado = Color(0xFF677386), neutroFondo = Color(0xFFE7ECF1), peligro = Color(0xFFB3261E),
)

val PaletaOscura = Paleta(
    papel = Color(0xFF0C2340), superficie = Color(0xFF112D4E), tinta = Color(0xFFE8F1FB),
    tinta2 = Color(0xFFA7BCD4), linea = Color(0xFF2A4B71), lineaFuerte = Color(0xFFD5E3F3),
    grilla = Color(0x13D5E3F3), acento = Color(0xFF8DCBFF), acentoFondo = Color(0x268DCBFF),
    reg = Color(0xFFFFC063), regFondo = Color(0x24FFC063), sobreReg = Color(0xFF0C2340),
    apr = Color(0xFF7EDFA6), aprFondo = Color(0x247EDFA6), sobreApr = Color(0xFF0C2340),
    apagado = Color(0xFF7F95B2), neutroFondo = Color(0x1AD5E3F3), peligro = Color(0xFFFF9C93),
)

val LocalPaleta = staticCompositionLocalOf { PaletaClara }

val Condensada = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))

@Composable
fun TemaCorrelativas(content: @Composable () -> Unit) {
    val oscuro = isSystemInDarkTheme()
    val p = if (oscuro) PaletaOscura else PaletaClara
    val base = if (oscuro) darkColorScheme() else lightColorScheme()
    val esquema = base.copy(
        primary = p.acento,
        onPrimary = p.superficie,
        primaryContainer = p.acentoFondo,
        onPrimaryContainer = p.tinta,
        secondaryContainer = p.tinta,
        onSecondaryContainer = p.papel,
        background = p.papel,
        onBackground = p.tinta,
        surface = p.papel,
        onSurface = p.tinta,
        surfaceVariant = p.neutroFondo,
        onSurfaceVariant = p.tinta2,
        surfaceContainerLow = p.superficie,
        surfaceContainer = p.superficie,
        surfaceContainerHigh = p.superficie,
        surfaceContainerHighest = p.superficie,
        outline = p.lineaFuerte,
        outlineVariant = p.linea,
        error = p.peligro,
    )
    val t = Typography()
    val tipografia = t.copy(
        displaySmall = t.displaySmall.copy(fontFamily = Condensada, fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontFamily = Condensada, fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontFamily = Condensada, fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontFamily = Condensada, fontWeight = FontWeight.Bold),
        titleMedium = TextStyle(fontFamily = Condensada, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 21.sp),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
    CompositionLocalProvider(LocalPaleta provides p) {
        MaterialTheme(colorScheme = esquema, typography = tipografia, content = content)
    }
}
