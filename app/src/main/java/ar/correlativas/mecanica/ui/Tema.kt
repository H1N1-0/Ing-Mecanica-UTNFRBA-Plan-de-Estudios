package ar.correlativas.mecanica.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.EstadoNodo
import ar.correlativas.mecanica.data.ModoTema

/** Colores de un tema del juego. */
data class Paleta(
    val oscuro: Boolean,
    val fondo: Color,
    val fondoAlto: Color,
    val tarjeta: Color,
    val tarjetaAlta: Color,
    val borde: Color,
    val texto: Color,
    val texto2: Color,
    val apagado: Color,
    val bloqueada: Color,
    val bloqueadaTexto: Color,
    val disponible: Color,
    val regular: Color,
    val rendir: Color,
    val oro: Color,
    val oroOscuro: Color,
    val violeta: Color,
    val rosa: Color,
    val peligro: Color,
    /** Pista de barras y anillos fuera de la tarjeta de jugador. */
    val pista: Color,
    /** Bandas alternadas del mapa. */
    val banda: Color,
    val degradeJugador: List<Color>,
)

val PaletaOscura = Paleta(
    oscuro = true,
    fondo = Color(0xFF0A0E1F),
    fondoAlto = Color(0xFF10162E),
    tarjeta = Color(0xFF161D3A),
    tarjetaAlta = Color(0xFF1F2850),
    borde = Color(0xFF2B3566),
    texto = Color(0xFFEEF1FF),
    texto2 = Color(0xFFA3ACD9),
    apagado = Color(0xFF69729E),
    bloqueada = Color(0xFF2E3558),
    bloqueadaTexto = Color(0xFF7D86B3),
    disponible = Color(0xFF22E1F0),
    regular = Color(0xFFFF8A3D),
    rendir = Color(0xFF7CFF9B),
    oro = Color(0xFFFFCB3D),
    oroOscuro = Color(0xFFE39A00),
    violeta = Color(0xFF8B5CF6),
    rosa = Color(0xFFFF4D9D),
    peligro = Color(0xFFFF6B6B),
    pista = Color.White.copy(alpha = 0.10f),
    banda = Color.White.copy(alpha = 0.025f),
    degradeJugador = listOf(Color(0xFF3B1E8A), Color(0xFF14246B), Color(0xFF0B4B63)),
)

/**
 * Tema claro pensado aparte: fondo lavanda muy suave, tarjetas blancas con borde frío,
 * colores de estado más saturados para que contrasten sobre blanco y el mismo
 * degradé vivo en la tarjeta de jugador.
 */
val PaletaClara = Paleta(
    oscuro = false,
    fondo = Color(0xFFF1F3FC),
    fondoAlto = Color(0xFFFFFFFF),
    tarjeta = Color(0xFFFFFFFF),
    tarjetaAlta = Color(0xFFEAEEFE),
    borde = Color(0xFFD5DBF2),
    texto = Color(0xFF141A38),
    texto2 = Color(0xFF515B84),
    apagado = Color(0xFF8790B5),
    bloqueada = Color(0xFFE4E8F6),
    bloqueadaTexto = Color(0xFF8A93B8),
    disponible = Color(0xFF0791B0),
    regular = Color(0xFFE2600C),
    rendir = Color(0xFF138A43),
    oro = Color(0xFFC98600),
    oroOscuro = Color(0xFFB06F00),
    violeta = Color(0xFF6A3BE4),
    rosa = Color(0xFFD1206F),
    peligro = Color(0xFFD32F2F),
    pista = Color(0xFF141A38).copy(alpha = 0.08f),
    banda = Color(0xFF6A3BE4).copy(alpha = 0.035f),
    degradeJugador = listOf(Color(0xFF6A3BE4), Color(0xFF3060F0), Color(0xFF0AA9C4)),
)

/**
 * Paleta activa. Es estado observable: las pantallas y los dibujos que la leen se
 * actualizan solos al cambiar de tema.
 */
object C {
    var paleta by mutableStateOf(PaletaOscura)

    val oscuro get() = paleta.oscuro
    val fondo get() = paleta.fondo
    val fondoAlto get() = paleta.fondoAlto
    val tarjeta get() = paleta.tarjeta
    val tarjetaAlta get() = paleta.tarjetaAlta
    val borde get() = paleta.borde
    val texto get() = paleta.texto
    val texto2 get() = paleta.texto2
    val apagado get() = paleta.apagado
    val bloqueada get() = paleta.bloqueada
    val bloqueadaTexto get() = paleta.bloqueadaTexto
    val disponible get() = paleta.disponible
    val regular get() = paleta.regular
    val rendir get() = paleta.rendir
    val oro get() = paleta.oro
    val oroOscuro get() = paleta.oroOscuro
    val violeta get() = paleta.violeta
    val rosa get() = paleta.rosa
    val peligro get() = paleta.peligro
    val pista get() = paleta.pista
    val banda get() = paleta.banda

    val degradeJugador get() = Brush.linearGradient(paleta.degradeJugador)
    val degradeOro get() = Brush.linearGradient(listOf(Color(0xFFFFE27A), Color(0xFFFFCB3D), Color(0xFFE39A00)))

    /** Texto sobre la tarjeta de jugador (degradé vivo en los dos temas). */
    val heroeTexto = Color.White
    val heroeTexto2 = Color(0xFFDCE3FF)
    val heroeAcento = Color(0xFF7FF3FF)
    val heroeOro = Color(0xFFFFD45C)

    /** Texto oscuro sobre fondos dorados. */
    val sobreOro = Color(0xFF3A2500)
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
fun esOscuro(modo: ModoTema): Boolean = when (modo) {
    ModoTema.SISTEMA -> isSystemInDarkTheme()
    ModoTema.CLARO -> false
    ModoTema.OSCURO -> true
}

@Composable
fun TemaJuego(modo: ModoTema = ModoTema.SISTEMA, content: @Composable () -> Unit) {
    val oscuro = esOscuro(modo)
    val p = if (oscuro) PaletaOscura else PaletaClara
    // Se actualiza la paleta global antes de dibujar este cuadro.
    remember(p) { C.paleta = p; p }

    val base = if (oscuro) darkColorScheme() else lightColorScheme()
    val esquema = base.copy(
        primary = p.disponible,
        onPrimary = if (oscuro) p.fondo else Color.White,
        primaryContainer = p.tarjetaAlta,
        onPrimaryContainer = p.texto,
        secondary = p.violeta,
        secondaryContainer = p.tarjetaAlta,
        onSecondaryContainer = p.texto,
        background = p.fondo,
        onBackground = p.texto,
        surface = p.fondo,
        onSurface = p.texto,
        surfaceVariant = p.tarjeta,
        onSurfaceVariant = p.texto2,
        surfaceContainerLowest = p.fondo,
        surfaceContainerLow = p.fondoAlto,
        surfaceContainer = p.fondoAlto,
        surfaceContainerHigh = p.tarjeta,
        surfaceContainerHighest = p.tarjetaAlta,
        outline = p.borde,
        outlineVariant = p.borde,
        error = p.peligro,
    )
    val t = Typography()
    fun TextStyle.ch() = copy(fontFamily = Chakra)
    val tipografia = Typography(
        displayLarge = t.displayLarge.ch().copy(fontWeight = FontWeight.Bold),
        displayMedium = t.displayMedium.ch().copy(fontWeight = FontWeight.Bold),
        displaySmall = t.displaySmall.ch().copy(fontWeight = FontWeight.Bold),
        headlineLarge = t.headlineLarge.ch().copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.ch().copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.ch().copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.ch().copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.ch().copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
        titleSmall = t.titleSmall.ch().copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.ch(),
        bodyMedium = t.bodyMedium.ch().copy(fontSize = 15.sp),
        bodySmall = t.bodySmall.ch().copy(fontSize = 13.sp),
        labelLarge = t.labelLarge.ch().copy(fontWeight = FontWeight.SemiBold),
        labelMedium = t.labelMedium.ch().copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.ch().copy(fontWeight = FontWeight.SemiBold),
    )
    MaterialTheme(colorScheme = esquema, typography = tipografia, content = content)
}
