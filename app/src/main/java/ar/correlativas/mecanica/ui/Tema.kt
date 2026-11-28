package ar.correlativas.mecanica.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.Area
import ar.correlativas.mecanica.data.EstadoNodo
import ar.correlativas.mecanica.data.Estilo
import ar.correlativas.mecanica.data.ModoTema

/*
 * Sistema de estilos.
 *
 * Cada estilo (Plano técnico, Gamificado, Material You) define TOKENS: colores ([Paleta]),
 * tipografía, formas ([Formas]), fondo, indicador de progreso y animaciones ([Movimiento]).
 * Las pantallas nunca preguntan "¿qué estilo es?": leen los tokens desde [C] y usan los
 * componentes base (Tarjeta, Nodo, Medidor, Barra, Pildora, Fondo, Medallon), que son los
 * únicos que dibujan distinto según el estilo. Así, una pantalla nueva se adapta sola.
 */

/** Colores de un estilo en modo claro u oscuro. */
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
    /** Camino crítico y cuellos de botella. */
    val critico: Color,
    /** Pista de barras y anillos fuera de la tarjeta de jugador. */
    val pista: Color,
    /** Bandas alternadas del mapa. */
    val banda: Color,
    /** Texto e íconos sobre el color de acento ([disponible]). */
    val sobreAcento: Color,
    /** Texto sobre fondos dorados (materias aprobadas). */
    val sobreOro: Color,
    /** Tinta de los dibujos de fondo, la cuadrícula y las cotas. */
    val tinta: Color,
    /** Tarjeta de jugador: fondo y textos. */
    val degradeJugador: List<Color>,
    val heroeTexto: Color,
    val heroeTexto2: Color,
    val heroeAcento: Color,
    val heroeOro: Color,
)

/** Formas de un estilo: de las tarjetas grandes a las píldoras. */
data class Formas(
    val grande: Shape,
    val media: Shape,
    val chica: Shape,
    val mini: Shape,
    val pildora: Shape,
    /** Grosor de los bordes de tarjetas y controles. */
    val borde: Dp,
)

/** Cómo se festeja un desbloqueo. */
enum class Festejo { CONFETI, ENGRANAJES, SUAVE }

/** Cómo se muestra el avance de la carrera. */
enum class Indicador { ANILLO_DEGRADE, MANOMETRO, ANILLO_TONAL }

/** Duraciones y carácter de las animaciones. */
data class Movimiento(
    val rapida: Int,
    val normal: Int,
    val lenta: Int,
    /** Entradas con rebote (gamificado) o lineales/suaves (plano, material). */
    val rebote: Boolean,
    val festejo: Festejo,
)

/** Todos los tokens del estilo activo. */
data class TemaVisual(
    val estilo: Estilo,
    val paleta: Paleta,
    /** Texto general. */
    val fuente: FontFamily,
    /** Números, cotas y datos (en Plano técnico, monoespaciada). */
    val fuenteDatos: FontFamily,
    val formas: Formas,
    val movimiento: Movimiento,
    val indicador: Indicador,
    /** Sombras de color ("brillo") en las tarjetas destacadas. */
    val brillo: Boolean = false,
)

// ---------------------------------------------------------------- tipografías

val Chakra = FontFamily(
    Font(R.font.chakra_petch_regular, FontWeight.Normal),
    Font(R.font.chakra_petch_medium, FontWeight.Medium),
    Font(R.font.chakra_petch_semibold, FontWeight.SemiBold),
    Font(R.font.chakra_petch_bold, FontWeight.Bold),
)

/** Rotulación técnica: semicondensada, como la letra normalizada de los planos. */
val Barlow = FontFamily(
    Font(R.font.barlow_semi_condensed_regular, FontWeight.Normal),
    Font(R.font.barlow_semi_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_semi_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_semi_condensed_bold, FontWeight.Bold),
)

/** Cotas y números del plano. */
val Mono = FontFamily(Font(R.font.share_tech_mono, FontWeight.Normal))

// ---------------------------------------------------------------- paletas

private val GamificadoOscura = Paleta(
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
    critico = Color(0xFFFF3D6E),
    pista = Color.White.copy(alpha = 0.10f),
    banda = Color.White.copy(alpha = 0.025f),
    sobreAcento = Color(0xFF0A0E1F),
    sobreOro = Color(0xFF3A2500),
    tinta = Color(0xFF8B9BFF),
    degradeJugador = listOf(Color(0xFF3B1E8A), Color(0xFF14246B), Color(0xFF0B4B63)),
    heroeTexto = Color.White,
    heroeTexto2 = Color(0xFFDCE3FF),
    heroeAcento = Color(0xFF7FF3FF),
    heroeOro = Color(0xFFFFD45C),
)

/**
 * Tema claro gamificado: fondo lavanda muy suave, tarjetas blancas con borde frío,
 * colores de estado más saturados para que contrasten sobre blanco y el mismo
 * degradé vivo en la tarjeta de jugador.
 */
private val GamificadoClara = Paleta(
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
    // Un poco más oscuro que antes: el texto blanco encima llega a 4,5:1 (WCAG AA).
    disponible = Color(0xFF067E99),
    regular = Color(0xFFE2600C),
    rendir = Color(0xFF138A43),
    oro = Color(0xFFC98600),
    oroOscuro = Color(0xFFB06F00),
    violeta = Color(0xFF6A3BE4),
    rosa = Color(0xFFD1206F),
    peligro = Color(0xFFD32F2F),
    critico = Color(0xFFD9144A),
    pista = Color(0xFF141A38).copy(alpha = 0.08f),
    banda = Color(0xFF6A3BE4).copy(alpha = 0.035f),
    sobreAcento = Color.White,
    sobreOro = Color(0xFF3A2500),
    tinta = Color(0xFF6A3BE4),
    degradeJugador = listOf(Color(0xFF6A3BE4), Color(0xFF3060F0), Color(0xFF0AA9C4)),
    heroeTexto = Color.White,
    heroeTexto2 = Color(0xFFDCE3FF),
    heroeAcento = Color(0xFF7FF3FF),
    heroeOro = Color(0xFFFFD45C),
)

/** Blueprint: azul de plano con tinta clara, como una copia heliográfica. */
private val PlanoOscura = Paleta(
    oscuro = true,
    fondo = Color(0xFF0F3A66),
    fondoAlto = Color(0xFF0C3157),
    tarjeta = Color(0xFF12426F),
    tarjetaAlta = Color(0xFF184F82),
    borde = Color(0xFF6E9FD0),
    texto = Color(0xFFEFF6FF),
    texto2 = Color(0xFFB9D2EE),
    apagado = Color(0xFF8AACD2),
    bloqueada = Color(0xFF15487A),
    bloqueadaTexto = Color(0xFF93B3D7),
    disponible = Color(0xFF8FE6FF),
    regular = Color(0xFFFFBE85),
    rendir = Color(0xFFA9F0B8),
    oro = Color(0xFFFFDA6E),
    oroOscuro = Color(0xFFE5B43E),
    violeta = Color(0xFFCDBDFF),
    rosa = Color(0xFFFFA9CF),
    peligro = Color(0xFFFF9A90),
    critico = Color(0xFFFF7E95),
    pista = Color.White.copy(alpha = 0.16f),
    banda = Color.White.copy(alpha = 0.035f),
    sobreAcento = Color(0xFF0C3157),
    sobreOro = Color(0xFF3A2800),
    tinta = Color(0xFFDDEBFF),
    degradeJugador = listOf(Color(0xFF12426F), Color(0xFF12426F)),
    heroeTexto = Color(0xFFEFF6FF),
    heroeTexto2 = Color(0xFFB9D2EE),
    heroeAcento = Color(0xFF8FE6FF),
    heroeOro = Color(0xFFFFDA6E),
)

/** Hoja de dibujo técnico: papel marfil, grafito y tinta azul. */
private val PlanoClara = Paleta(
    oscuro = false,
    fondo = Color(0xFFF3F1E9),
    fondoAlto = Color(0xFFFAF9F4),
    tarjeta = Color(0xFFFCFBF7),
    tarjetaAlta = Color(0xFFECE9DF),
    borde = Color(0xFF3B4A5C),
    texto = Color(0xFF1E2730),
    texto2 = Color(0xFF4A5563),
    apagado = Color(0xFF858B94),
    bloqueada = Color(0xFFE5E2D8),
    bloqueadaTexto = Color(0xFF7E7B72),
    disponible = Color(0xFF0A5DA3),
    regular = Color(0xFFB9500D),
    rendir = Color(0xFF1C7337),
    oro = Color(0xFF946400),
    oroOscuro = Color(0xFF7A5200),
    violeta = Color(0xFF5636BC),
    rosa = Color(0xFFAD1F5B),
    peligro = Color(0xFFC62828),
    critico = Color(0xFFC8102E),
    pista = Color(0xFF1E2730).copy(alpha = 0.10f),
    banda = Color(0xFF0A5DA3).copy(alpha = 0.035f),
    sobreAcento = Color.White,
    sobreOro = Color.White,
    tinta = Color(0xFF2C4A6B),
    degradeJugador = listOf(Color(0xFFFCFBF7), Color(0xFFFCFBF7)),
    heroeTexto = Color(0xFF1E2730),
    heroeTexto2 = Color(0xFF4A5563),
    heroeAcento = Color(0xFF0A5DA3),
    heroeOro = Color(0xFF946400),
)

/** Material You sin colores dinámicos (Android 11 o anterior): esquema fijo con semilla cian petróleo. */
private val MaterialClaroFijo = lightColorScheme(
    primary = Color(0xFF006A6A), onPrimary = Color.White,
    primaryContainer = Color(0xFF9CF1F0), onPrimaryContainer = Color(0xFF002020),
    secondary = Color(0xFF4A6363), onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCE8E7), onSecondaryContainer = Color(0xFF051F1F),
    tertiary = Color(0xFF4B607C), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3E4FF), onTertiaryContainer = Color(0xFF041C35),
    error = Color(0xFFBA1A1A), onError = Color.White,
    background = Color(0xFFF4FBFA), onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF4FBFA), onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE5E4), onSurfaceVariant = Color(0xFF3F4948),
    outline = Color(0xFF6F7979), outlineVariant = Color(0xFFBEC9C8),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFEFF5F4),
    surfaceContainer = Color(0xFFE9EFEE), surfaceContainerHigh = Color(0xFFE3E9E8),
    surfaceContainerHighest = Color(0xFFDDE4E3),
)

private val MaterialOscuroFijo = darkColorScheme(
    primary = Color(0xFF80D5D4), onPrimary = Color(0xFF003737),
    primaryContainer = Color(0xFF004F4F), onPrimaryContainer = Color(0xFF9CF1F0),
    secondary = Color(0xFFB0CCCB), onSecondary = Color(0xFF1B3534),
    secondaryContainer = Color(0xFF324B4B), onSecondaryContainer = Color(0xFFCCE8E7),
    tertiary = Color(0xFFB3C8E8), onTertiary = Color(0xFF1C314B),
    tertiaryContainer = Color(0xFF334863), onTertiaryContainer = Color(0xFFD3E4FF),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    background = Color(0xFF0E1515), onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0E1515), onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4948), onSurfaceVariant = Color(0xFFBEC9C8),
    outline = Color(0xFF889392), outlineVariant = Color(0xFF3F4948),
    surfaceContainerLowest = Color(0xFF090F0F), surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainer = Color(0xFF1A2121), surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF2F3636),
)

/** Colores dinámicos del fondo de pantalla en Android 12+, o el esquema fijo antes. */
fun esquemaMaterial(context: Context, oscuro: Boolean): ColorScheme =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (oscuro) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (oscuro) MaterialOscuroFijo else MaterialClaroFijo
    }

/**
 * Paleta Material a partir del esquema (dinámico o fijo). Los colores de estado que Material no
 * trae (naranja, verde, ámbar) se "armonizan" mezclándolos un poco con el primario del fondo.
 */
fun paletaMaterial(cs: ColorScheme, oscuro: Boolean): Paleta {
    fun armonizar(c: Color) = lerp(c, cs.primary, 0.16f)
    val ambar = armonizar(if (oscuro) Color(0xFFF4C45A) else Color(0xFF7C5800))
    return Paleta(
        oscuro = oscuro,
        fondo = cs.surface,
        fondoAlto = cs.surfaceContainer,
        tarjeta = cs.surfaceContainerLow,
        tarjetaAlta = cs.surfaceContainerHigh,
        borde = cs.outlineVariant,
        texto = cs.onSurface,
        texto2 = cs.onSurfaceVariant,
        apagado = cs.outline,
        bloqueada = cs.surfaceContainerHighest,
        bloqueadaTexto = cs.outline,
        disponible = cs.primary,
        regular = armonizar(if (oscuro) Color(0xFFFFB68A) else Color(0xFF9A4500)),
        rendir = armonizar(if (oscuro) Color(0xFF8DD89B) else Color(0xFF1E6C34)),
        oro = ambar,
        oroOscuro = lerp(ambar, Color.Black, 0.2f),
        violeta = cs.tertiary,
        rosa = cs.secondary,
        peligro = cs.error,
        critico = cs.error,
        pista = cs.secondaryContainer,
        banda = cs.primary.copy(alpha = 0.04f),
        sobreAcento = cs.onPrimary,
        sobreOro = if (oscuro) Color(0xFF2A1C00) else Color.White,
        tinta = cs.outline,
        degradeJugador = listOf(cs.primaryContainer, cs.primaryContainer),
        heroeTexto = cs.onPrimaryContainer,
        heroeTexto2 = cs.onPrimaryContainer.copy(alpha = 0.78f),
        heroeAcento = cs.onPrimaryContainer,
        heroeOro = cs.primary,
    )
}

/** Paleta fija de un estilo. Material You sin contexto usa su esquema fijo. */
fun paletaFija(estilo: Estilo, oscuro: Boolean): Paleta = when (estilo) {
    Estilo.PLANO -> if (oscuro) PlanoOscura else PlanoClara
    Estilo.GAMIFICADO -> if (oscuro) GamificadoOscura else GamificadoClara
    Estilo.MATERIAL -> paletaMaterial(if (oscuro) MaterialOscuroFijo else MaterialClaroFijo, oscuro)
}

// ---------------------------------------------------------------- formas y movimiento

private val FormasGamificado = Formas(
    grande = RoundedCornerShape(24.dp), media = RoundedCornerShape(18.dp), chica = RoundedCornerShape(12.dp),
    mini = RoundedCornerShape(9.dp), pildora = RoundedCornerShape(50), borde = 1.dp,
)

/** Esquinas casi rectas y chaflanes: como recortes de una lámina. */
private val FormasPlano = Formas(
    grande = RoundedCornerShape(3.dp), media = RoundedCornerShape(3.dp), chica = RoundedCornerShape(2.dp),
    mini = RoundedCornerShape(2.dp), pildora = CutCornerShape(5.dp), borde = 1.dp,
)

private val FormasMaterial = Formas(
    grande = RoundedCornerShape(28.dp), media = RoundedCornerShape(16.dp), chica = RoundedCornerShape(12.dp),
    mini = RoundedCornerShape(8.dp), pildora = RoundedCornerShape(50), borde = 0.dp,
)

fun temaVisual(estilo: Estilo, paleta: Paleta): TemaVisual = when (estilo) {
    Estilo.PLANO -> TemaVisual(
        estilo, paleta, fuente = Barlow, fuenteDatos = Mono, formas = FormasPlano,
        movimiento = Movimiento(rapida = 160, normal = 320, lenta = 900, rebote = false, festejo = Festejo.ENGRANAJES),
        indicador = Indicador.MANOMETRO,
    )
    Estilo.GAMIFICADO -> TemaVisual(
        estilo, paleta, fuente = Chakra, fuenteDatos = Chakra, formas = FormasGamificado,
        movimiento = Movimiento(rapida = 220, normal = 400, lenta = 900, rebote = true, festejo = Festejo.CONFETI),
        indicador = Indicador.ANILLO_DEGRADE, brillo = true,
    )
    Estilo.MATERIAL -> TemaVisual(
        estilo, paleta, fuente = FontFamily.Default, fuenteDatos = FontFamily.Default, formas = FormasMaterial,
        movimiento = Movimiento(rapida = 200, normal = 350, lenta = 700, rebote = false, festejo = Festejo.SUAVE),
        indicador = Indicador.ANILLO_TONAL,
    )
}

/**
 * Tokens activos. Es estado observable: las pantallas y los dibujos que los leen se
 * actualizan solos al cambiar de estilo o de tema, sin reiniciar nada.
 */
object C {
    var tema by mutableStateOf(temaVisual(Estilo.PREDETERMINADO, paletaFija(Estilo.PREDETERMINADO, true)))

    val estilo get() = tema.estilo
    val paleta get() = tema.paleta
    val fuente get() = tema.fuente
    val fuenteDatos get() = tema.fuenteDatos
    val formas get() = tema.formas
    val movimiento get() = tema.movimiento

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
    val critico get() = paleta.critico
    val pista get() = paleta.pista
    val banda get() = paleta.banda
    val sobreAcento get() = paleta.sobreAcento
    val sobreOro get() = paleta.sobreOro
    val tinta get() = paleta.tinta

    val degradeJugador get() = Brush.linearGradient(paleta.degradeJugador)
    val degradeOro
        get() = if (estilo == Estilo.GAMIFICADO) Brush.linearGradient(listOf(Color(0xFFFFE27A), Color(0xFFFFCB3D), Color(0xFFE39A00)))
        else Brush.linearGradient(listOf(oro, oro))

    /** Tarjeta que invita a una acción (por ejemplo, hacer el test). */
    val destacado
        get() = when (estilo) {
            Estilo.GAMIFICADO -> Brush.linearGradient(listOf(violeta, rosa))
            Estilo.MATERIAL -> Brush.linearGradient(listOf(paleta.degradeJugador.first(), paleta.degradeJugador.first()))
            Estilo.PLANO -> Brush.linearGradient(listOf(tarjetaAlta, tarjetaAlta))
        }
    val sobreDestacado get() = if (estilo == Estilo.GAMIFICADO) Color.White else heroeTexto

    /** Texto sobre la tarjeta de jugador. */
    val heroeTexto get() = paleta.heroeTexto
    val heroeTexto2 get() = paleta.heroeTexto2
    val heroeAcento get() = paleta.heroeAcento
    val heroeOro get() = paleta.heroeOro
}

fun colorEstado(e: EstadoNodo): Color = when (e) {
    EstadoNodo.BLOQUEADA -> C.bloqueadaTexto
    EstadoNodo.DISPONIBLE -> C.disponible
    EstadoNodo.REGULARIZADA -> C.regular
    EstadoNodo.PARA_RENDIR -> C.rendir
    // Misma familia que Aprobada; se distingue por el ícono y el doble aro del nodo.
    EstadoNodo.APROBADA, EstadoNodo.PROMOCIONADA -> C.oro
}

fun textoEstado(e: EstadoNodo, pps: Boolean = false): String = when (e) {
    EstadoNodo.BLOQUEADA -> "Bloqueada"
    EstadoNodo.DISPONIBLE -> if (pps) "Podés empezarla" else "Disponible"
    EstadoNodo.REGULARIZADA -> "Regularizada"
    EstadoNodo.PARA_RENDIR -> "Lista para rendir"
    EstadoNodo.APROBADA -> if (pps) "Acreditada" else "Aprobada"
    EstadoNodo.PROMOCIONADA -> "Promocionada"
}

fun iconoEstado(e: EstadoNodo): Int = when (e) {
    EstadoNodo.BLOQUEADA -> R.drawable.ic_candado
    EstadoNodo.DISPONIBLE -> R.drawable.ic_rayo
    EstadoNodo.REGULARIZADA -> R.drawable.ic_mitad
    EstadoNodo.PARA_RENDIR -> R.drawable.ic_llama
    EstadoNodo.APROBADA -> R.drawable.ic_estrella
    EstadoNodo.PROMOCIONADA -> R.drawable.ic_promocion
}

// ---------------------------------------------------------------- áreas de las frases

/** Color de un área para el medallón del autor, ajustado al estilo y al modo. */
fun colorArea(a: Area): Color {
    val oscuro = C.oscuro
    val base = when (a) {
        Area.FISICA -> if (oscuro) Color(0xFF6FC3FF) else Color(0xFF1565C0)
        Area.MATEMATICA -> if (oscuro) Color(0xFFB39DFF) else Color(0xFF5E35B1)
        Area.ENERGIA -> if (oscuro) Color(0xFFFF9B5E) else Color(0xFFD84315)
        Area.INGENIERIA -> if (oscuro) Color(0xFFFFD25C) else Color(0xFF9A6A00)
        Area.ELECTRICIDAD -> if (oscuro) Color(0xFFF4F06A) else Color(0xFF8A7F00)
        Area.COMPUTACION -> if (oscuro) Color(0xFF52E5C8) else Color(0xFF00796B)
        Area.INDUSTRIA -> if (oscuro) Color(0xFF9FB3C8) else Color(0xFF455A64)
        Area.CIENCIAS -> if (oscuro) Color(0xFF8CE38F) else Color(0xFF2E7D32)
        Area.HUMANIDADES -> if (oscuro) Color(0xFFFF8FBE) else Color(0xFFAD1457)
    }
    return when (C.estilo) {
        Estilo.GAMIFICADO -> base
        // En el plano, las áreas son "tintas" algo más apagadas, mezcladas con la tinta del dibujo.
        Estilo.PLANO -> lerp(base, C.tinta, 0.25f)
        // En Material se armonizan con el color dinámico.
        Estilo.MATERIAL -> lerp(base, C.disponible, 0.22f)
    }
}

fun iconoArea(a: Area): Int = when (a) {
    Area.FISICA -> R.drawable.ic_atomo
    Area.MATEMATICA -> R.drawable.ic_compas
    Area.ENERGIA -> R.drawable.ic_llama
    Area.INGENIERIA -> R.drawable.ic_engranaje
    Area.ELECTRICIDAD -> R.drawable.ic_rayo
    Area.COMPUTACION -> R.drawable.ic_robot
    Area.INDUSTRIA -> R.drawable.ic_fabrica
    Area.CIENCIAS -> R.drawable.ic_matraz
    Area.HUMANIDADES -> R.drawable.ic_libro
}

// ---------------------------------------------------------------- tema de Compose

@Composable
fun esOscuro(modo: ModoTema): Boolean = when (modo) {
    ModoTema.SISTEMA -> isSystemInDarkTheme()
    ModoTema.CLARO -> false
    ModoTema.OSCURO -> true
}

@Composable
fun TemaJuego(
    modo: ModoTema = ModoTema.SISTEMA,
    estilo: Estilo = Estilo.PREDETERMINADO,
    content: @Composable () -> Unit,
) {
    val oscuro = esOscuro(modo)
    val context = LocalContext.current
    val esquemaMaterial = remember(estilo, oscuro) {
        if (estilo == Estilo.MATERIAL) esquemaMaterial(context, oscuro) else null
    }
    val tema = remember(estilo, oscuro, esquemaMaterial) {
        val p = esquemaMaterial?.let { paletaMaterial(it, oscuro) } ?: paletaFija(estilo, oscuro)
        temaVisual(estilo, p)
    }
    // Se actualizan los tokens globales antes de dibujar este cuadro: el cambio es instantáneo.
    remember(tema) { C.tema = tema; tema }
    val p = tema.paleta

    val esquema = esquemaMaterial ?: (if (oscuro) darkColorScheme() else lightColorScheme()).copy(
        primary = p.disponible,
        onPrimary = p.sobreAcento,
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
    val tipografia = remember(tema.fuente) { tipografia(tema) }
    val f = tema.formas
    val formas = remember(f) {
        Shapes(
            extraSmall = f.mini as? CornerBasedShape ?: RoundedCornerShape(4.dp),
            small = f.chica as? CornerBasedShape ?: RoundedCornerShape(8.dp),
            medium = f.media as? CornerBasedShape ?: RoundedCornerShape(12.dp),
            large = f.grande as? CornerBasedShape ?: RoundedCornerShape(16.dp),
            extraLarge = f.grande as? CornerBasedShape ?: RoundedCornerShape(28.dp),
        )
    }
    MaterialTheme(colorScheme = esquema, typography = tipografia, shapes = formas, content = content)
}

private fun tipografia(tema: TemaVisual): Typography {
    val t = Typography()
    val fuente = tema.fuente
    // Barlow Condensed es angosta: un poco más grande y espaciada para que se lea igual.
    val plano = tema.estilo == Estilo.PLANO
    fun TextStyle.f(peso: FontWeight? = null) = copy(
        fontFamily = fuente,
        fontWeight = peso ?: fontWeight,
        fontSize = if (plano) fontSize * 1.08f else fontSize,
        letterSpacing = if (plano) 0.4.sp else letterSpacing,
    )
    val titulo = if (tema.estilo == Estilo.MATERIAL) FontWeight.Medium else FontWeight.Bold
    return Typography(
        displayLarge = t.displayLarge.f(titulo),
        displayMedium = t.displayMedium.f(titulo),
        displaySmall = t.displaySmall.f(titulo),
        headlineLarge = t.headlineLarge.f(titulo),
        headlineMedium = t.headlineMedium.f(titulo),
        headlineSmall = t.headlineSmall.f(titulo),
        titleLarge = t.titleLarge.f(titulo),
        titleMedium = t.titleMedium.f(FontWeight.SemiBold).copy(fontSize = if (plano) 18.sp else 17.sp),
        titleSmall = t.titleSmall.f(FontWeight.SemiBold),
        bodyLarge = t.bodyLarge.f(),
        bodyMedium = t.bodyMedium.f().copy(fontSize = if (plano) 16.sp else 15.sp),
        bodySmall = t.bodySmall.f().copy(fontSize = if (plano) 14.sp else 13.sp),
        labelLarge = t.labelLarge.f(FontWeight.SemiBold),
        labelMedium = t.labelMedium.f(FontWeight.SemiBold),
        labelSmall = t.labelSmall.f(FontWeight.SemiBold),
    )
}

/** Forma circular (nodos, medallones): igual en los tres estilos. */
val Circulo = CircleShape
