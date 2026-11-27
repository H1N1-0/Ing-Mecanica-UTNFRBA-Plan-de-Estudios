package ar.correlativas.mecanica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.data.Area
import ar.correlativas.mecanica.data.Estilo
import ar.correlativas.mecanica.data.iniciales
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Componentes base que cambian según el estilo. Son los ÚNICOS lugares (junto con Nodo, Barra y
 * Tarjeta en Componentes.kt) que miran C.estilo: el resto de las pantallas solo los usa.
 *
 * Rendimiento: los fondos se arman una vez por tamaño en drawWithCache y van en su propia capa
 * (graphicsLayer), así que scrollear encima no los redibuja. Las animaciones se leen dentro de
 * Canvas/graphicsLayer, nunca en la composición.
 */

// ---------------------------------------------------------------- fondo de pantalla

/**
 * Fondo de una pantalla. En Plano técnico: cuadrícula de lámina y un dibujo técnico distinto por
 * pantalla, en línea fina y baja opacidad. En Gamificado: dos brillos suaves. En Material: liso.
 */
@Composable
fun Fondo(dibujo: Dibujo, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(C.fondo)) {
        CapaFondo(dibujo, Modifier.matchParentSize())
        content()
    }
}

@Composable
fun CapaFondo(dibujo: Dibujo, modifier: Modifier = Modifier) {
    when (C.estilo) {
        Estilo.PLANO -> {
            val medidor = rememberTextMeasurer()
            Spacer(modifier.graphicsLayer().fondoPlano(dibujo, medidor))
        }
        Estilo.GAMIFICADO -> Spacer(modifier.graphicsLayer().fondoGamificado())
        Estilo.MATERIAL -> Unit
    }
}

private fun Modifier.fondoPlano(dibujo: Dibujo, medidor: TextMeasurer) = drawWithCache {
    val tinta = C.tinta
    val oscuro = C.oscuro
    val d = density
    val lineas = Lineas(d)
    val grilla = cuadricula(size, 10f * d)
    // El dibujo ocupa la parte de abajo: arriba suele haber texto.
    val lado = minOf(size.width * 0.95f, size.height * 0.62f)
    val centro = Offset(size.width * 0.56f, size.height - lado * 0.56f)
    val rotulador = Rotulador(medidor, TextStyle(fontFamily = Mono, fontSize = 11.sp))
    val trazos = trazosDe(dibujo, Rect(centro, lado / 2), rotulador)
    val menor = Stroke(0.5f * d)
    val mayor = Stroke(0.8f * d)
    val margen = 6f * d
    onDrawBehind {
        drawPath(grilla.menor, tinta.copy(alpha = if (oscuro) 0.07f else 0.06f), style = menor)
        drawPath(grilla.mayor, tinta.copy(alpha = if (oscuro) 0.13f else 0.11f), style = mayor)
        // Margen de la lámina.
        drawRect(
            tinta.copy(alpha = 0.22f), Offset(margen, margen), Size(size.width - 2 * margen, size.height - 2 * margen),
            style = Stroke(0.8f * d),
        )
        dibujar(trazos, tinta.copy(alpha = if (oscuro) 0.17f else 0.20f), lineas)
    }
}

private fun Modifier.fondoGamificado() = drawWithCache {
    val oscuro = C.oscuro
    val violeta = Brush.radialGradient(
        listOf(C.violeta.copy(alpha = if (oscuro) 0.16f else 0.08f), Color.Transparent),
        center = Offset(size.width * 0.05f, size.height * 0.02f), radius = size.width * 0.9f,
    )
    val cian = Brush.radialGradient(
        listOf(C.disponible.copy(alpha = if (oscuro) 0.10f else 0.06f), Color.Transparent),
        center = Offset(size.width * 1.0f, size.height * 0.65f), radius = size.width * 0.8f,
    )
    onDrawBehind {
        drawRect(violeta)
        drawRect(cian)
    }
}

// ---------------------------------------------------------------- decoración de tarjetas

/**
 * Brillo de color alrededor de una tarjeta destacada (Gamificado). Es una sombra del sistema
 * (RenderNode): no cuesta un dibujo extra por frame.
 */
fun Modifier.brillo(forma: Shape, color: Color = C.violeta): Modifier =
    if (C.tema.brillo) shadow(18.dp, forma, clip = false, ambientColor = color, spotColor = color) else this

/**
 * Marcas de lámina alrededor de una tarjeta (Plano técnico): marcas de esquina, ejes de simetría
 * que asoman arriba y abajo, y una cota opcional sobre la tarjeta. Se dibuja por fuera del borde.
 */
fun Modifier.marcasDePlano(cota: String?, medidor: TextMeasurer?) = drawWithCache {
    val d = density
    val tinta = C.tinta
    val fondo = C.fondo
    val brazo = 9f * d
    val sep = 4f * d
    val fina = 0.9f * d
    val texto = if (cota != null && medidor != null) {
        medidor.measure(cota, TextStyle(fontFamily = Mono, fontSize = 10.sp, letterSpacing = 1.sp))
    } else null
    val eje = PathEffect.dashPathEffect(floatArrayOf(7f * d, 2.5f * d, 1.5f * d, 2.5f * d))
    onDrawBehind {
        val c = tinta.copy(alpha = 0.55f)
        val w = size.width
        val h = size.height
        // Marcas de esquina: una L por esquina, separadas del borde.
        listOf(
            Offset(-sep, -sep) to Offset(1f, 1f),
            Offset(w + sep, -sep) to Offset(-1f, 1f),
            Offset(-sep, h + sep) to Offset(1f, -1f),
            Offset(w + sep, h + sep) to Offset(-1f, -1f),
        ).forEach { (p, dir) ->
            drawLine(c, p, p + Offset(brazo * dir.x, 0f), fina)
            drawLine(c, p, p + Offset(0f, brazo * dir.y), fina)
        }
        // Eje de simetría vertical: asoma arriba y abajo.
        drawLine(c, Offset(w / 2, -sep * 2.2f), Offset(w / 2, sep * 1.5f), fina, pathEffect = eje)
        drawLine(c, Offset(w / 2, h - sep * 1.5f), Offset(w / 2, h + sep * 2.2f), fina, pathEffect = eje)
        // Cota sobre la tarjeta, de borde a borde, con el texto al medio.
        if (texto != null) {
            val y = -sep * 2.4f
            val flecha = 5f * d
            drawLine(c, Offset(0f, y), Offset(w, y), fina)
            listOf(Offset(0f, y) to 1f, Offset(w, y) to -1f).forEach { (p, s) ->
                val tri = androidx.compose.ui.graphics.Path().apply {
                    moveTo(p.x, p.y)
                    lineTo(p.x + s * flecha, p.y - flecha * 0.32f)
                    lineTo(p.x + s * flecha, p.y + flecha * 0.32f)
                    close()
                }
                drawPath(tri, c)
            }
            val tw = texto.size.width.toFloat()
            val th = texto.size.height.toFloat()
            val x = (w - tw) / 2
            drawRect(fondo, Offset(x - 4f * d, y - th / 2), Size(tw + 8f * d, th))
            drawText(texto, color = tinta.copy(alpha = 0.8f), topLeft = Offset(x, y - th / 2))
        }
    }
}

// ---------------------------------------------------------------- indicador de progreso

/**
 * Avance de la carrera con el indicador del estilo: anillo con degradé (Gamificado), anillo tonal
 * (Material) o manómetro con aguja (Plano técnico). [heroe] = sobre la tarjeta de jugador.
 */
@Composable
fun Medidor(progreso: Float, modifier: Modifier = Modifier, grosor: Dp = 13.dp, heroe: Boolean = false) {
    when (C.tema.indicador) {
        Indicador.ANILLO_DEGRADE -> Anillo(
            progreso, modifier, grosor,
            pista = if (heroe) C.heroeTexto.copy(alpha = 0.16f) else C.pista,
        )
        Indicador.ANILLO_TONAL -> AnilloTonal(
            progreso, modifier, grosor,
            color = if (heroe) C.heroeOro else C.disponible,
            pista = if (heroe) C.heroeTexto.copy(alpha = 0.14f) else C.pista,
        )
        Indicador.MANOMETRO -> Manometro(progreso, modifier)
    }
}

@Composable
private fun AnilloTonal(progreso: Float, modifier: Modifier, grosor: Dp, color: Color, pista: Color) {
    val p by animateFloatAsState(progreso.coerceIn(0f, 1f), tween(C.movimiento.lenta, easing = FastOutSlowInEasing), label = "tonal")
    Canvas(modifier) {
        val g = grosor.toPx()
        val lado = size.minDimension - g
        val tl = Offset((size.width - lado) / 2, (size.height - lado) / 2)
        drawArc(pista, -90f, 360f, false, tl, Size(lado, lado), style = Stroke(g))
        // Material 3: un pequeño hueco entre lo hecho y lo que falta.
        if (p > 0f) drawArc(color, -90f, (360f * p - 6f).coerceAtLeast(1f), false, tl, Size(lado, lado), style = Stroke(g, cap = StrokeCap.Round))
    }
}

/** Manómetro: dial de 270°, escala 0–100 con marcas, zona final y aguja animada. */
@Composable
fun Manometro(progreso: Float, modifier: Modifier = Modifier) {
    val p by animateFloatAsState(progreso.coerceIn(0f, 1f), tween(1400, easing = FastOutSlowInEasing), label = "aguja")
    val medidor = rememberTextMeasurer()
    Box(modifier.semantics { contentDescription = "Manómetro de avance: ${(progreso * 100).roundToInt()} por ciento" }) {
        Spacer(
            Modifier.matchParentSize().drawWithCache {
                val d = density
                val tinta = C.tinta
                val texto = C.texto
                val r = size.minDimension / 2 - 2f * d
                val c = Offset(size.width / 2, size.height / 2)
                val desde = 135f
                val barrido = 270f
                fun ang(v: Float) = Math.toRadians((desde + barrido * v).toDouble())
                fun pto(radio: Float, v: Float) = Offset(c.x + radio * cos(ang(v)).toFloat(), c.y + radio * sin(ang(v)).toFloat())
                val numeros = (0..5).map { i ->
                    val v = i / 5f
                    v to medidor.measure("${i * 20}", TextStyle(fontFamily = Mono, fontSize = 9.sp))
                }
                onDrawBehind {
                    // Caja del instrumento.
                    drawCircle(C.tarjeta, r)
                    drawCircle(tinta.copy(alpha = 0.85f), r, style = Stroke(1.6f * d))
                    drawCircle(tinta.copy(alpha = 0.4f), r * 0.9f, style = Stroke(0.6f * d))
                    // Zona final (80–100 %).
                    drawArc(
                        C.rendir.copy(alpha = 0.55f), desde + barrido * 0.8f, barrido * 0.2f, false,
                        Offset(c.x - r * 0.82f, c.y - r * 0.82f), Size(r * 1.64f, r * 1.64f), style = Stroke(3f * d),
                    )
                    // Escala: marca cada 2 %, mayor cada 10 %.
                    for (i in 0..50) {
                        val v = i / 50f
                        val mayor = i % 5 == 0
                        drawLine(
                            texto.copy(alpha = if (mayor) 0.9f else 0.5f),
                            pto(r * (if (mayor) 0.70f else 0.77f), v), pto(r * 0.86f, v),
                            (if (mayor) 1.3f else 0.6f) * d,
                        )
                    }
                    numeros.forEach { (v, t) ->
                        val en = pto(r * 0.52f, v)
                        drawText(t, color = texto.copy(alpha = 0.8f), topLeft = en - Offset(t.size.width / 2f, t.size.height / 2f))
                    }
                    // Aguja con contrapeso y eje.
                    val punta = pto(r * 0.80f, p)
                    val cola = pto(-r * 0.16f, p)
                    drawLine(C.critico, cola, punta, 2.2f * d, cap = StrokeCap.Round)
                    drawCircle(C.critico, 5f * d, c)
                    drawCircle(C.tarjeta, 2f * d, c)
                }
            },
        )
        Text(
            "${(progreso * 100).roundToInt()}%",
            Modifier.align(Alignment.BottomCenter).offset(y = (-14).dp),
            color = C.texto,
            fontFamily = C.fuenteDatos,
            fontSize = 22.sp,
        )
    }
}

// ---------------------------------------------------------------- medallón del autor

/**
 * Medallón del autor de una frase: iniciales, color de su área y un ícono temático.
 * Plano técnico: globo de referencia partido (iniciales arriba, ícono abajo).
 * Gamificado: moneda con degradé y brillo. Material: contenedor tonal con insignia.
 */
@Composable
fun Medallon(autor: String, area: Area, modifier: Modifier = Modifier, tam: Dp = 52.dp) {
    val color = colorArea(area)
    val ini = remember(autor) { iniciales(autor) }
    val descripcion = "$autor, ${area.nombre}"
    when (C.estilo) {
        Estilo.PLANO -> Box(
            modifier.size(tam).semantics { contentDescription = descripcion }.drawBehind {
                val r = size.minDimension / 2 - 1.dp.toPx()
                drawCircle(C.tarjeta, r)
                drawCircle(color, r, style = Stroke(1.4.dp.toPx()))
                drawLine(color, Offset(center.x - r, center.y + r * 0.12f), Offset(center.x + r, center.y + r * 0.12f), 0.9.dp.toPx())
            },
        ) {
            Text(
                ini,
                Modifier.align(Alignment.TopCenter).offset(y = tam * 0.14f),
                color = color,
                fontFamily = Mono,
                fontSize = (tam.value * 0.30f).sp,
            )
            Ic(iconoArea(area), color, Modifier.align(Alignment.BottomCenter).offset(y = -tam * 0.12f), tam = tam * 0.28f)
        }
        Estilo.GAMIFICADO -> Box(modifier.size(tam).semantics { contentDescription = descripcion }) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(color, lerpColor(color, Color.Black, 0.45f))))
                    .border(2.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.7f), color.copy(alpha = 0.2f))), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(ini, color = Color.White, fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = (tam.value * 0.34f).sp)
            }
            Insignia(area, color, tam)
        }
        Estilo.MATERIAL -> Box(modifier.size(tam).semantics { contentDescription = descripcion }) {
            Box(
                Modifier.matchParentSize().clip(CircleShape).background(color.copy(alpha = if (C.oscuro) 0.24f else 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(ini, color = color, fontFamily = C.fuente, fontWeight = FontWeight.Medium, fontSize = (tam.value * 0.34f).sp)
            }
            Insignia(area, color, tam)
        }
    }
}

@Composable
private fun BoxScope.Insignia(area: Area, color: Color, tam: Dp) {
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .offset(x = tam * 0.08f, y = tam * 0.08f)
            .size(tam * 0.42f)
            .clip(CircleShape)
            .background(C.fondoAlto)
            .border(1.5.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Ic(iconoArea(area), color, tam = tam * 0.25f) }
}

private fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

// ---------------------------------------------------------------- engranajes animados

/**
 * Tren de tres engranajes que gira y frena (Plano técnico, al desbloquear). Las ruedas engranan:
 * cada una gira en sentido contrario y a la velocidad que le toca por su número de dientes.
 */
@Composable
fun EngranajesGirando(modifier: Modifier = Modifier) {
    val giro = remember { Animatable(0f) }
    LaunchedEffect(Unit) { giro.animateTo(1f, tween(2600, easing = FastOutSlowInEasing)) }
    Spacer(
        modifier.drawWithCache {
            val l = size.minDimension
            val z = intArrayOf(20, 12, 9)
            val r1 = l * 0.36f
            val m = 2 * r1 / z[0]
            val radios = z.map { it * m / 2 }
            val c1 = Offset(size.width / 2 - r1 * 0.45f, size.height / 2 + l * 0.03f)
            val a12 = -0.35
            val c2 = Offset(c1.x + (radios[0] + radios[1]) * cos(a12).toFloat(), c1.y + (radios[0] + radios[1]) * sin(a12).toFloat())
            val a13 = -2.3
            val c3 = Offset(c1.x + (radios[0] + radios[2]) * cos(a13).toFloat(), c1.y + (radios[0] + radios[2]) * sin(a13).toFloat())
            val ruedas = listOf(
                Triple(c1, engranaje(c1, radios[0], z[0], a12), 1f),
                Triple(c2, engranaje(c2, radios[1], z[1], a12 + PI + PI / z[1]), -z[0] / z[1].toFloat()),
                Triple(c3, engranaje(c3, radios[2], z[2], a13 + PI + PI / z[2]), -z[0] / z[2].toFloat()),
            )
            val gruesa = Stroke(1.6f * density)
            val ejes = PathEffect.dashPathEffect(floatArrayOf(8f * density, 2.5f * density, 1.5f * density, 2.5f * density))
            onDrawBehind {
                val tinta = C.tinta
                val base = giro.value * 220f
                ruedas.forEachIndexed { i, (c, path, relacion) ->
                    rotate(base * relacion, c) {
                        drawPath(path, C.tarjetaAlta)
                        drawPath(path, tinta, style = gruesa)
                        drawCircle(tinta, radios[i] * 0.28f, c, style = gruesa)
                        drawLine(tinta.copy(alpha = 0.6f), Offset(c.x - radios[i] * 1.2f, c.y), Offset(c.x + radios[i] * 1.2f, c.y), 0.6f * density, pathEffect = ejes)
                        drawLine(tinta.copy(alpha = 0.6f), Offset(c.x, c.y - radios[i] * 1.2f), Offset(c.x, c.y + radios[i] * 1.2f), 0.6f * density, pathEffect = ejes)
                    }
                }
            }
        },
    )
}
