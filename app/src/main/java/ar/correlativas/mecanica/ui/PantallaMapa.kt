package ar.correlativas.mecanica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.EstadoNodo
import ar.correlativas.mecanica.data.Mapa
import ar.correlativas.mecanica.data.Nivel
import ar.correlativas.mecanica.data.estadoNodo
import kotlinx.coroutines.launch

private const val ESCALA_MIN = 0.35f
private const val ESCALA_MAX = 2.2f
private const val TAM_NODO = 58f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaMapa(avance: Avance, mapa: Mapa, onAbrir: (Int) -> Unit) {
    val densidad = LocalDensity.current
    val anchoPx = with(densidad) { mapa.ancho.dp.toPx() }
    val altoPx = with(densidad) { mapa.alto.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize().background(C.fondo).clipToBounds()) {
        val vistaW = constraints.maxWidth.toFloat()
        val vistaH = constraints.maxHeight.toFloat()

        // Mantiene el mapa dentro de la vista (con un margen) mientras se desplaza.
        fun limitar(o: Offset, s: Float): Offset {
            val margen = with(densidad) { 96.dp.toPx() }
            val w = anchoPx * s
            val h = altoPx * s
            val x = if (w + 2 * margen <= vistaW) (vistaW - w) / 2 else o.x.coerceIn(vistaW - w - margen, margen)
            val y = if (h + 2 * margen <= vistaH) (vistaH - h) / 2 else o.y.coerceIn(vistaH - h - margen, margen)
            return Offset(x, y)
        }

        val escalaInicial = 0.78f
        var escala by remember { mutableStateOf(escalaInicial) }
        var desplazamiento by remember {
            mutableStateOf(limitar(Offset(0f, with(densidad) { 92.dp.toPx() }), escalaInicial))
        }
        val scope = rememberCoroutineScope()
        fun zoomA(nueva: Float) {
            scope.launch {
                val desde = escala
                val centro = Offset(vistaW / 2, vistaH / 2)
                val base = desplazamiento
                val anim = Animatable(0f)
                anim.animateTo(1f, tween(250)) {
                    val s = desde + (nueva - desde) * value
                    val p = (centro - base) / desde
                    escala = s
                    desplazamiento = limitar(centro - p * s, s)
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroide, pan, zoom, _ ->
                        val nueva = (escala * zoom).coerceIn(ESCALA_MIN, ESCALA_MAX)
                        val p = (centroide - desplazamiento) / escala
                        escala = nueva
                        desplazamiento = limitar(centroide - p * nueva + pan, nueva)
                    }
                },
        ) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(mapa.ancho.dp, mapa.alto.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = escala
                        scaleY = escala
                        translationX = desplazamiento.x
                        translationY = desplazamiento.y
                    },
            ) {
                ContenidoMapa(avance, mapa, onAbrir)
            }
        }

        // Título y leyenda
        Column(
            Modifier
                .fillMaxWidth()
                .background(C.fondo.copy(alpha = 0.88f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(R.drawable.ic_mapa, C.disponible, tam = 20.dp)
                Text("Mapa de la carrera", Modifier.padding(start = 8.dp).weight(1f), color = C.texto, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Pellizcá para zoom", color = C.apagado, fontFamily = Chakra, fontSize = 12.sp)
            }
            FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(EstadoNodo.BLOQUEADA, EstadoNodo.DISPONIBLE, EstadoNodo.REGULARIZADA, EstadoNodo.PARA_RENDIR, EstadoNodo.APROBADA).forEach { e ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ic(iconoEstado(e), colorEstado(e), tam = 13.dp)
                        Text(
                            when (e) {
                                EstadoNodo.BLOQUEADA -> "Bloqueada"
                                EstadoNodo.DISPONIBLE -> "Disponible"
                                EstadoNodo.REGULARIZADA -> "Regular"
                                EstadoNodo.PARA_RENDIR -> "Rendir"
                                else -> "Aprobada"
                            },
                            Modifier.padding(start = 4.dp),
                            color = C.texto2,
                            fontFamily = Chakra,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        // Controles de zoom
        Column(
            Modifier.align(Alignment.BottomEnd).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BotonMapa("+", "Acercar") { zoomA((escala * 1.35f).coerceAtMost(ESCALA_MAX)) }
            BotonMapa("−", "Alejar") { zoomA((escala / 1.35f).coerceAtLeast(ESCALA_MIN)) }
            BotonMapa("⤢", "Ver todo el mapa") {
                val ajuste = minOf(vistaW / anchoPx, vistaH / altoPx).coerceIn(ESCALA_MIN, ESCALA_MAX)
                zoomA(ajuste)
            }
        }
    }
}

@Composable
private fun BotonMapa(texto: String, descripcion: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(C.tarjetaAlta)
            .border(1.dp, C.borde, CircleShape)
            .semantics { contentDescription = descripcion }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = C.texto, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ContenidoMapa(avance: Avance, mapa: Mapa, onAbrir: (Int) -> Unit) {
    val porId = remember(mapa) { mapa.nodos.associateBy { it.materia.id } }
    val estados = mapa.nodos.associate { it.materia.id to avance.estadoNodo(it.materia) }

    // Fondo: bandas por nivel y conexiones.
    Canvas(Modifier.fillMaxSize()) {
        val d = density
        (0 until 5).forEach { f ->
            val top = (Mapa.MARGEN_Y + f * Mapa.PASO_Y - 22f) * d
            if (f % 2 == 0) {
                drawRect(C.banda, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, Mapa.PASO_Y * d))
            }
        }
        val radio = TAM_NODO / 2 * d
        mapa.aristas.forEach { a ->
            val o = porId[a.desde] ?: return@forEach
            val t = porId[a.hasta] ?: return@forEach
            val desde = Offset(o.x * d, o.y * d + radio)
            val hasta = Offset(t.x * d, t.y * d - radio)
            val cumple = when (a.pide) {
                Nivel.REGULARIZADA -> avance.estado(a.desde) != Estado.SIN_CURSAR
                Nivel.APROBADA -> avance.estado(a.desde) == Estado.APROBADA
            }
            val destino = estados[a.hasta]
            val recorrida = destino == EstadoNodo.APROBADA
            val color = when {
                !cumple -> C.borde.copy(alpha = 0.5f)
                recorrida -> C.oro.copy(alpha = 0.16f)
                a.pide == Nivel.APROBADA -> C.oro.copy(alpha = 0.8f)
                else -> C.regular.copy(alpha = 0.8f)
            }
            val medio = (hasta.y - desde.y) / 2
            val camino = Path().apply {
                moveTo(desde.x, desde.y)
                cubicTo(desde.x, desde.y + medio, hasta.x, hasta.y - medio, hasta.x, hasta.y)
            }
            drawPath(
                camino,
                color,
                style = Stroke(
                    width = (if (cumple && !recorrida) 2.6f else 1.6f) * d,
                    pathEffect = if (a.pide == Nivel.REGULARIZADA) PathEffect.dashPathEffect(floatArrayOf(7f * d, 5f * d)) else null,
                ),
            )
        }
    }

    // Etiquetas de nivel
    (1..5).forEach { n ->
        Text(
            "NIVEL $n",
            Modifier.offset(x = 14.dp, y = (Mapa.MARGEN_Y + (n - 1) * Mapa.PASO_Y - 16f).dp),
            color = C.apagado,
            fontFamily = Chakra,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
        )
    }

    // Nodos
    mapa.nodos.forEach { n ->
        val e = estados.getValue(n.materia.id)
        val anchoEtiqueta = Mapa.PASO_X - 8f
        Column(
            Modifier
                .offset(x = (n.x - anchoEtiqueta / 2).dp, y = (n.y - TAM_NODO / 2).dp)
                .width(anchoEtiqueta.dp)
                .clip(RoundedCornerShape(12.dp))
                .semantics(mergeDescendants = true) { contentDescription = "${n.materia.nombre}, ${textoEstado(e)}" }
                .clickable(role = Role.Button) { onAbrir(n.materia.id) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Nodo(n.materia, e, tam = TAM_NODO.dp)
            Text(
                n.materia.nombre,
                Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
                color = if (e == EstadoNodo.BLOQUEADA) C.bloqueadaTexto else C.texto,
                fontFamily = Chakra,
                fontWeight = if (e == EstadoNodo.BLOQUEADA) FontWeight.Medium else FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
