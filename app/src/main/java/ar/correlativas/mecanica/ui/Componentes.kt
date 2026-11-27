package ar.correlativas.mecanica.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.EstadoNodo
import ar.correlativas.mecanica.data.Estilo
import ar.correlativas.mecanica.data.Materia
import ar.correlativas.mecanica.data.Tipo

@Composable
fun Ic(@DrawableRes res: Int, tint: Color, modifier: Modifier = Modifier, tam: Dp = 20.dp, descripcion: String? = null) {
    Icon(ImageVector.vectorResource(res), contentDescription = descripcion, tint = tint, modifier = modifier.size(tam))
}

fun numeroDe(m: Materia): String = when (m.tipo) {
    Tipo.PPS -> "PPS"
    Tipo.ELECTIVA -> "E"
    Tipo.OBLIGATORIA -> m.id.toString()
}

/**
 * Nodo de una materia: bloqueada (gris con candado), disponible (late suave),
 * regularizada (media carga), lista para rendir (late en verde) o aprobada (dorada con estrella).
 */
@Composable
fun Nodo(
    materia: Materia,
    estado: EstadoNodo,
    modifier: Modifier = Modifier,
    tam: Dp = 56.dp,
    animado: Boolean = true,
) {
    val color by animateColorAsState(colorEstado(estado), tween(400), label = "color")
    val late = animado && (estado == EstadoNodo.DISPONIBLE || estado == EstadoNodo.PARA_RENDIR)
    // El pulso se lee solo dentro del Canvas: cada frame redibuja el nodo, sin recomponerlo.
    val pulso: State<Float>? = if (late) {
        rememberInfiniteTransition(label = "pulso").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "p",
        )
    } else null
    val carga by animateFloatAsState(
        when (estado) {
            EstadoNodo.REGULARIZADA, EstadoNodo.PARA_RENDIR -> 0.5f
            EstadoNodo.APROBADA -> 1f
            else -> 0f
        },
        tween(500), label = "carga",
    )
    Box(modifier.size(tam)) {
        // Trazos y efectos se crean una vez por tamaño (drawWithCache); cada frame del pulso solo dibuja.
        Spacer(
            Modifier.matchParentSize().drawWithCache {
                val trazos = TrazosNodo(density, size)
                onDrawBehind {
                    when (C.estilo) {
                        Estilo.PLANO -> nodoPlano(estado, color, pulso, carga, trazos)
                        Estilo.MATERIAL -> nodoMaterial(estado, color, pulso, carga)
                        Estilo.GAMIFICADO -> nodoGamificado(estado, color, pulso, carga)
                    }
                }
            },
        )
        val numero = numeroDe(materia)
        Text(
            numero,
            Modifier.align(Alignment.Center),
            color = when (estado) {
                EstadoNodo.APROBADA -> C.sobreOro
                EstadoNodo.BLOQUEADA -> C.bloqueadaTexto
                EstadoNodo.DISPONIBLE -> C.disponible
                else -> C.texto
            },
            fontFamily = C.fuenteDatos,
            fontWeight = FontWeight.Bold,
            fontSize = (tam.value * if (numero.length > 2) 0.26f else 0.36f).sp,
        )
        val insignia = tam * 0.42f
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = insignia * 0.28f, y = -insignia * 0.22f)
                .size(insignia)
                .clip(CircleShape)
                .background(C.fondo)
                .border(1.5.dp, if (estado == EstadoNodo.BLOQUEADA) C.borde else color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Ic(iconoEstado(estado), color, tam = insignia * 0.62f)
        }
        if (LocalCritico.current?.esCuello(materia.id) == true) {
            InsigniaCuello(
                insignia,
                Modifier.align(Alignment.BottomStart).offset(x = -insignia * 0.28f, y = insignia * 0.12f),
            )
        }
    }
}

private fun DrawScope.nodoGamificado(estado: EstadoNodo, color: Color, pulso: State<Float>?, carga: Float) {
    val r = size.minDimension / 2
    val anillo = (r * 0.12f).coerceAtLeast(2.5f)
    if (pulso != null) {
        val p = pulso.value
        drawCircle(color.copy(alpha = 0.40f * (1 - p)), radius = r * (1f + 0.45f * p))
        drawCircle(color.copy(alpha = 0.18f), radius = r * 1.12f)
    }
    drawCircle(if (estado == EstadoNodo.BLOQUEADA) C.bloqueada else C.fondoAlto, radius = r)
    if (estado == EstadoNodo.APROBADA) {
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFE89A), Color(0xFFFFCB3D), Color(0xFFE39A00)), center = Offset(r * 0.7f, r * 0.6f), radius = r * 1.6f),
            radius = r,
        )
    } else if (carga > 0f) {
        // Media carga: la mitad de abajo llena.
        drawArc(color.copy(alpha = 0.45f), 0f, 180f, useCenter = true, topLeft = Offset.Zero, size = Size(r * 2, r * 2), alpha = (carga / 0.5f).coerceIn(0f, 1f))
    }
    drawCircle(if (estado == EstadoNodo.BLOQUEADA) C.borde else color, radius = r - anillo / 2, style = Stroke(anillo))
}

/**
 * Nodo de plano: circunferencia a tinta con ejes en cruz que asoman. Bloqueada = línea oculta
 * (de trazos); regularizada = media sección rayada; aprobada = rellena con doble circunferencia.
 */
/** Efectos de trazo del nodo de plano, armados una vez por tamaño. */
private class TrazosNodo(d: Float, size: Size) {
    val eje = PathEffect.dashPathEffect(floatArrayOf(5f * d, 2f * d, 1.2f * d, 2f * d))
    val pulso = Stroke(1f * d)
    val oculta = Stroke(1.2f * d, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f * d, 2.5f * d)))
    val llena = Stroke(1.8f * d)
    val fina = Stroke(0.8f * d)
    val circulo = androidx.compose.ui.graphics.Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(Offset(size.width / 2, size.height / 2), size.minDimension / 2))
    }
}

private fun DrawScope.nodoPlano(estado: EstadoNodo, color: Color, pulso: State<Float>?, carga: Float, t: TrazosNodo) {
    val r = size.minDimension / 2
    val d = density
    val eje = t.eje
    val tinta = C.tinta.copy(alpha = 0.45f)
    drawLine(tinta, Offset(center.x - r * 1.28f, center.y), Offset(center.x + r * 1.28f, center.y), 0.6f * d, pathEffect = eje)
    drawLine(tinta, Offset(center.x, center.y - r * 1.28f), Offset(center.x, center.y + r * 1.28f), 0.6f * d, pathEffect = eje)
    if (pulso != null) {
        val p = pulso.value
        drawCircle(color.copy(alpha = 0.55f * (1 - p)), radius = r * (1f + 0.35f * p), style = t.pulso)
    }
    drawCircle(if (estado == EstadoNodo.BLOQUEADA) C.bloqueada else C.fondoAlto, radius = r)
    when {
        estado == EstadoNodo.APROBADA -> {
            drawCircle(color, radius = r)
            drawCircle(C.fondoAlto.copy(alpha = 0.7f), radius = r * 0.82f, style = t.fina)
        }
        carga > 0f -> {
            // Media sección rayada (mitad de abajo).
            val paso = 3.2f * d
            clipPath(t.circulo) {
                clipRect(top = center.y, bottom = size.height) {
                    var x = -size.height
                    while (x < size.width) {
                        drawLine(color.copy(alpha = 0.75f * (carga / 0.5f).coerceIn(0f, 1f)), Offset(x, size.height), Offset(x + size.height, 0f), 0.8f * d)
                        x += paso
                    }
                }
            }
        }
    }
    val borde = if (estado == EstadoNodo.BLOQUEADA) t.oculta else t.llena
    drawCircle(if (estado == EstadoNodo.BLOQUEADA) C.bloqueadaTexto else color, radius = r - borde.width / 2, style = borde)
}

/** Nodo Material: círculo tonal; aprobada, relleno pleno; disponible, contorno y un halo suave. */
private fun DrawScope.nodoMaterial(estado: EstadoNodo, color: Color, pulso: State<Float>?, carga: Float) {
    val r = size.minDimension / 2
    if (pulso != null) {
        val p = pulso.value
        drawCircle(color.copy(alpha = 0.22f * (1 - p)), radius = r * (1f + 0.3f * p))
    }
    when (estado) {
        EstadoNodo.APROBADA -> drawCircle(color, radius = r)
        EstadoNodo.BLOQUEADA -> drawCircle(C.bloqueada, radius = r)
        else -> {
            drawCircle(C.fondoAlto, radius = r)
            drawCircle(color.copy(alpha = 0.16f), radius = r)
            if (carga > 0f) drawArc(color.copy(alpha = 0.38f), 0f, 180f, useCenter = true, topLeft = Offset.Zero, size = Size(r * 2, r * 2))
            drawCircle(color, radius = r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
        }
    }
}

/** Anillo de progreso con degradé. */
@Composable
fun Anillo(
    progreso: Float,
    modifier: Modifier = Modifier,
    grosor: Dp = 14.dp,
    colores: List<Color> = listOf(C.disponible, C.violeta, C.rosa, C.oro, C.disponible),
    pista: Color = C.pista,
) {
    val p by animateFloatAsState(progreso.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing), label = "anillo")
    Canvas(modifier) {
        val g = grosor.toPx()
        val lado = size.minDimension - g
        val tl = Offset((size.width - lado) / 2, (size.height - lado) / 2)
        drawArc(pista, -90f, 360f, false, tl, Size(lado, lado), style = Stroke(g))
        if (p > 0f) {
            drawArc(
                Brush.sweepGradient(colores, center = center), -90f, 360f * p, false, tl, Size(lado, lado),
                style = Stroke(g, cap = StrokeCap.Round),
            )
        }
    }
}

/**
 * Barra de progreso: redondeada (Gamificado y Material) o tipo regla, recta y con marcas cada
 * 10 % (Plano técnico).
 */
@Composable
fun Barra(progreso: Float, color: Color, modifier: Modifier = Modifier, alto: Dp = 8.dp, pista: Color = C.pista) {
    val p by animateFloatAsState(progreso.coerceIn(0f, 1f), tween(C.movimiento.lenta * 3 / 4), label = "barra")
    val plano = C.estilo == Estilo.PLANO
    Canvas(modifier.height(if (plano) alto + 3.dp else alto)) {
        if (plano) {
            val h = alto.toPx()
            val y0 = size.height - h
            drawRect(pista, Offset(0f, y0), Size(size.width, h))
            if (p > 0f) drawRect(color, Offset(0f, y0), Size(size.width * p, h))
            drawRect(C.tinta.copy(alpha = 0.5f), Offset(0f, y0), Size(size.width, h), style = Stroke(0.8.dp.toPx()))
            for (i in 0..10) {
                val x = size.width * i / 10f
                drawLine(C.tinta.copy(alpha = 0.6f), Offset(x, y0), Offset(x, y0 - (if (i % 5 == 0) 3.dp else 1.5.dp).toPx()), 0.8.dp.toPx())
            }
        } else {
            val h = size.height
            val radio = androidx.compose.ui.geometry.CornerRadius(h / 2)
            drawRoundRect(pista, cornerRadius = radio)
            if (p > 0f) drawRoundRect(color, size = Size(size.width * p, h), cornerRadius = radio)
        }
    }
}

@Composable
fun Pildora(texto: String, color: Color, modifier: Modifier = Modifier, @DrawableRes icono: Int? = null) {
    Row(
        modifier
            .clip(C.formas.pildora)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.35f), C.formas.pildora)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icono != null) Ic(icono, color, tam = 14.dp)
        Text(texto, color = color, fontFamily = C.fuente, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
    }
}

private data class Opcion(val estado: Estado, val texto: String, val icono: Int, val color: Color)

/** Selector de estado con íconos. [compacto] muestra solo íconos (para la lista). */
@Composable
fun SelectorEstado(
    tipo: Tipo,
    estado: Estado,
    onCambio: (Estado) -> Unit,
    modifier: Modifier = Modifier,
    compacto: Boolean = false,
) {
    val opciones = if (tipo == Tipo.PPS) listOf(
        Opcion(Estado.SIN_CURSAR, "Pendiente", R.drawable.ic_reloj, C.texto2),
        Opcion(Estado.APROBADA, "Acreditada", R.drawable.ic_estrella, C.oro),
    ) else listOf(
        Opcion(Estado.SIN_CURSAR, "Sin cursar", R.drawable.ic_reloj, C.texto2),
        Opcion(Estado.REGULARIZADA, "Regularizada", R.drawable.ic_mitad, C.regular),
        Opcion(Estado.APROBADA, "Aprobada", R.drawable.ic_estrella, C.oro),
    )
    val forma = C.formas.chica
    Row(
        modifier
            .height(if (compacto) 38.dp else 48.dp)
            .clip(forma)
            .background(C.fondo)
            .border(1.dp, C.borde, forma)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        opciones.forEach { o ->
            val elegido = o.estado == estado
            val fondo by animateColorAsState(if (elegido) o.color.copy(alpha = 0.20f) else Color.Transparent, label = "f")
            val tinta by animateColorAsState(if (elegido) o.color else C.apagado, label = "t")
            Row(
                Modifier
                    .then(if (compacto) Modifier.width(40.dp) else Modifier.weight(1f))
                    .fillMaxHeight()
                    .clip(C.formas.mini)
                    .background(fondo)
                    .then(if (elegido) Modifier.border(1.dp, o.color.copy(alpha = 0.6f), C.formas.mini) else Modifier)
                    .semantics { selected = elegido; contentDescription = o.texto }
                    .clickable(role = Role.RadioButton) { onCambio(o.estado) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Ic(o.icono, tinta, tam = 18.dp)
                if (!compacto) {
                    Text(
                        o.texto,
                        Modifier.padding(start = 6.dp),
                        color = tinta,
                        fontFamily = C.fuente,
                        fontWeight = if (elegido) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Tarjeta base. Cada estilo la viste distinto: borde fino y marcas de lámina (Plano técnico),
 * borde con brillo arriba (Gamificado) o superficie tonal sin borde (Material).
 * [borde] reemplaza el borde por uno de acento; [cota] agrega una cota arriba en Plano técnico.
 */
@Composable
fun Tarjeta(
    modifier: Modifier = Modifier,
    fondo: Color = C.tarjeta,
    borde: Brush? = null,
    cota: String? = null,
    content: @Composable () -> Unit,
) {
    val forma = C.formas.media
    val estilo = C.estilo
    val medidor = if (estilo == Estilo.PLANO && cota != null) rememberTextMeasurer() else null
    Box(
        modifier
            .then(if (estilo == Estilo.PLANO) Modifier.marcasDePlano(cota, medidor) else Modifier)
            .clip(forma)
            .background(fondo)
            .then(
                if (estilo == Estilo.GAMIFICADO) {
                    Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (C.oscuro) 0.035f else 0.5f), Color.Transparent)))
                } else Modifier,
            )
            .then(
                when {
                    borde != null -> Modifier.border(1.5.dp, borde, forma)
                    estilo == Estilo.PLANO -> Modifier.border(1.dp, C.tinta.copy(alpha = 0.55f), forma)
                    C.formas.borde > 0.dp -> Modifier.border(C.formas.borde, C.borde.copy(alpha = 0.7f), forma)
                    else -> Modifier
                },
            ),
    ) { content() }
}
