package ar.correlativas.mecanica.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.EstadoNodo

/** Paleta del juego: fondo oscuro y colores vivos por estado. */
object C {
    val fondo = Color(0xFF0A0E1F)
    val fondoAlto = Color(0xFF10162E)
    val tarjeta = Color(0xFF161D3A)
    val tarjetaAlta = Color(0xFF1F2850)
    val borde = Color(0xFF2B3566)
    val texto = Color(0xFFEEF1FF)
    val texto2 = Color(0xFFA3ACD9)
    val apagado = Color(0xFF69729E)

    val bloqueada = Color(0xFF2E3558)
    val bloqueadaTexto = Color(0xFF7D86B3)
    val disponible = Color(0xFF22E1F0)
    val regular = Color(0xFFFF8A3D)
    val rendir = Color(0xFF7CFF9B)
    val oro = Color(0xFFFFCB3D)
    val oroOscuro = Color(0xFFE39A00)
    val violeta = Color(0xFF8B5CF6)
    val rosa = Color(0xFFFF4D9D)
    val peligro = Color(0xFFFF6B6B)

    val degradeJugador = Brush.linearGradient(listOf(Color(0xFF3B1E8A), Color(0xFF14246B), Color(0xFF0B4B63)))
    val degradeOro = Brush.linearGradient(listOf(Color(0xFFFFE27A), oro, oroOscuro))
}

fun colorEstado(e: EstadoNodo): Color = when (e) {
    EstadoNodo.BLOQUEADA -> C.bloqueadaTexto
    EstadoNodo.DISPONIBLE -> C.disponible
    EstadoNodo.REGULARIZADA -> C.regular
    EstadoNodo.PARA_RENDIR -> C.rendir
    EstadoNodo.APROBADA -> C.oro
}

fun textoEstado(e: EstadoNodo, pps: Boolean = false): String = when (e) {
    EstadoNodo.BLOQUEADA -> "Bloqueada"
    EstadoNodo.DISPONIBLE -> if (pps) "Podés empezarla" else "Disponible"
    EstadoNodo.REGULARIZADA -> "Regularizada"
    EstadoNodo.PARA_RENDIR -> "Lista para rendir"
    EstadoNodo.APROBADA -> if (pps) "Acreditada" else "Aprobada"
}

fun iconoEstado(e: EstadoNodo): Int = when (e) {
    EstadoNodo.BLOQUEADA -> R.drawable.ic_candado
    EstadoNodo.DISPONIBLE -> R.drawable.ic_rayo
    EstadoNodo.REGULARIZADA -> R.drawable.ic_mitad
    EstadoNodo.PARA_RENDIR -> R.drawable.ic_llama
    EstadoNodo.APROBADA -> R.drawable.ic_estrella
}

val Chakra = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

@Composable
fun TemaJuego(content: @Composable () -> Unit) {
    val esquema = darkColorScheme(
        primary = C.disponible,
        onPrimary = C.fondo,
        primaryContainer = C.tarjetaAlta,
        onPrimaryContainer = C.texto,
        secondary = C.violeta,
        secondaryContainer = C.tarjetaAlta,
        onSecondaryContainer = C.texto,
        background = C.fondo,
        onBackground = C.texto,
        surface = C.fondo,
        onSurface = C.texto,
        surfaceVariant = C.tarjeta,
        onSurfaceVariant = C.texto2,
        surfaceContainerLowest = C.fondo,
        surfaceContainerLow = C.fondoAlto,
        surfaceContainer = C.fondoAlto,
        surfaceContainerHigh = C.tarjeta,
        surfaceContainerHighest = C.tarjetaAlta,
        outline = C.borde,
        outlineVariant = C.borde,
        error = C.peligro,
    )
    val base = Typography()
    fun TextStyle.ch() = copy(fontFamily = Chakra)
    val tipografia = Typography(
        displayLarge = base.displayLarge.ch().copy(fontWeight = FontWeight.Bold),
        displayMedium = base.displayMedium.ch().copy(fontWeight = FontWeight.Bold),
        displaySmall = base.displaySmall.ch().copy(fontWeight = FontWeight.Bold),
        headlineLarge = base.headlineLarge.ch().copy(fontWeight = FontWeight.Bold),
        headlineMedium = base.headlineMedium.ch().copy(fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.ch().copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.ch().copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.ch().copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        titleSmall = base.titleSmall.ch().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.ch(),
        bodyMedium = base.bodyMedium.ch().copy(fontSize = 15.sp),
        bodySmall = base.bodySmall.ch().copy(fontSize = 13.sp),
        labelLarge = base.labelLarge.ch().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.ch().copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.ch().copy(fontWeight = FontWeight.SemiBold),
    )
    MaterialTheme(colorScheme = esquema, typography = tipografia, content = content)
}
