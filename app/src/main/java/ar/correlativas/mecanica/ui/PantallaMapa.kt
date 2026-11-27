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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.StrokeCap
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
import ar.correlativas.mecanica.data.CaminoCritico
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.EstadoNodo
import ar.correlativas.mecanica.data.Mapa
import ar.correlativas.mecanica.data.NodoMapa
import ar.correlativas.mecanica.data.Nivel
import ar.correlativas.mecanica.data.estadoNodo
import kotlinx.coroutines.launch

private const val ESCALA_MIN = 0.35f
private const val ESCALA_MAX = 2.2f
private const val ESCALA_INICIAL = 0.78f
private const val TAM_NODO = 58f

/**
 * Zoom, posición y modo "camino crítico" del mapa. Se guarda con rememberSaveable para no
 * perder dónde estabas al girar la pantalla o pasar de celular a tablet.
 */
class VistaMapa(escala: Float = ESCALA_INICIAL, x: Float = Float.NaN, y: Float = Float.NaN, verCritico: Boolean = false) {
    var escala by mutableFloatStateOf(escala)
    /** Sin especificar hasta que el mapa se muestra por primera vez. */
    var desplazamiento by mutableStateOf(if (x.isNaN()) Offset.Unspecified else Offset(x, y))
    var verCritico by mutableStateOf(verCritico)
    /** Pedido de acercar la vista al camino crítico (no se guarda: es una acción puntual). */
    var encuadrarCritico by mutableStateOf(false)

    /** Muestra el camino crítico y mueve la vista para que entre entero. */
    fun mostrarCritico() {
        verCritico = true
        encuadrarCritico = true
    }

    companion object {
        val Saver = listSaver<VistaMapa, Any>(
            save = {
                val d = it.desplazamiento
                listOf(it.escala, if (d.isSpecified) d.x else Float.NaN, if (d.isSpecified) d.y else Float.NaN, it.verCritico)
            },
            restore = { VistaMapa(it[0] as Float, it[1] as Float, it[2] as Float, it[3] as Boolean) },
        )
    }
}

@Composable
fun rememberVistaMapa(): VistaMapa = rememberSaveable(saver = VistaMapa.Saver) { VistaMapa() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PantallaMapa(
    avance: Avance,
    mapa: Mapa,
    critico: CaminoCritico,
    onAbrir: (Int) -> Unit,
    vista: VistaMapa = rememberVistaMapa(),
    seleccionada: Int? = null,
) {
    val densidad = LocalDensity.current
    val anchoPx = with(densidad) { mapa.ancho.dp.toPx() }
    val altoPx = with(densidad) { mapa.alto.dp.toPx() }
    // Sin fondo propio: va sobre el Fondo de la pestaña (cuadrícula y dibujo en Plano técnico).
    BoxWithConstraints(Modifier.fillMaxSize().clipToBounds()) {
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

        // Ojo: el zoom y la posición NO se leen en la composición, solo en graphicsLayer y en los
        // gestos. Así mover el mapa redibuja una capa en vez de recomponer toda la pantalla.
        fun inicial() = limitar(Offset(0f, with(densidad) { 92.dp.toPx() }), vista.escala)
        fun actual() = vista.desplazamiento.takeIf { it.isSpecified } ?: inicial()
        // Al girar o abrir el panel de detalle cambia el tamaño: se reacomoda sin perder el zoom.
        LaunchedEffect(vistaW, vistaH) {
            if (vista.desplazamiento.isSpecified) vista.desplazamiento = limitar(vista.desplazamiento, vista.escala)
        }
        val scope = rememberCoroutineScope()
        fun zoomA(nueva: Float) {
            scope.launch {
                val desde = vista.escala
                val centro = Offset(vistaW / 2, vistaH / 2)
                val base = actual()
                val anim = Animatable(0f)
                anim.animateTo(1f, tween(250)) {
                    val s = desde + (nueva - desde) * value
                    val p = (centro - base) / desde
                    vista.escala = s
                    vista.desplazamiento = limitar(centro - p * s, s)
                }
            }
        }

        // Encuadra el camino crítico: zoom para que entre la cadena entera, centrada.
        LaunchedEffect(vista.encuadrarCritico) {
            if (!vista.encuadrarCritico) return@LaunchedEffect
            // El pedido se baja recién al terminar: cambiar la clave antes cancelaría la animación.
            try {
            val ids = critico.idsCamino
            val nodos = mapa.nodos.filter { it.materia.id in ids }
            if (nodos.isEmpty()) return@LaunchedEffect // el finally baja el pedido
            val px = densidad.density
            val minX = (nodos.minOf { it.x } - Mapa.PASO_X / 2) * px
            val maxX = (nodos.maxOf { it.x } + Mapa.PASO_X / 2) * px
            val minY = (nodos.minOf { it.y } - TAM_NODO) * px
            val maxY = (nodos.maxOf { it.y } + TAM_NODO * 1.6f) * px
            // Deja lugar arriba para el título y abajo para el cartel del camino.
            val arriba = 110.dp.value * px
            val abajo = 130.dp.value * px
            val altoUtil = (vistaH - arriba - abajo).coerceAtLeast(vistaH * 0.4f)
            val destino = minOf(vistaW / (maxX - minX), altoUtil / (maxY - minY), 1.1f).coerceIn(ESCALA_MIN, ESCALA_MAX)
            val centroMapa = Offset((minX + maxX) / 2, (minY + maxY) / 2)
            val centroVista = Offset(vistaW / 2, arriba + altoUtil / 2)
            val desdeS = vista.escala
            val desdeD = actual()
            val hastaD = limitar(centroVista - centroMapa * destino, destino)
            Animatable(0f).animateTo(1f, tween(450)) {
                vista.escala = desdeS + (destino - desdeS) * value
                vista.desplazamiento = desdeD + (hastaD - desdeD) * value
            }
            } finally {
                vista.encuadrarCritico = false
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(vistaW, vistaH) {
                    detectTransformGestures { centroide, pan, zoom, _ ->
                        val antes = vista.escala
                        val d = actual()
                        val nueva = (antes * zoom).coerceIn(ESCALA_MIN, ESCALA_MAX)
                        val p = (centroide - d) / antes
                        vista.escala = nueva
                        vista.desplazamiento = limitar(centroide - p * nueva + pan, nueva)
                    }
                },
        ) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(mapa.ancho.dp, mapa.alto.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        val d = actual()
                        scaleX = vista.escala
                        scaleY = vista.escala
                        translationX = d.x
                        translationY = d.y
                    },
            ) {
                ContenidoMapa(avance, mapa, critico, vista.verCritico, seleccionada, onAbrir)
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
                Text("Mapa de la carrera", Modifier.padding(start = 8.dp).weight(1f), color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BotonCritico(vista.verCritico) { if (vista.verCritico) vista.verCritico = false else vista.mostrarCritico() }
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
                            fontFamily = C.fuente,
                            fontSize = 12.sp,
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    InsigniaCuello(14.dp)
                    Text("Cuello de botella", Modifier.padding(start = 4.dp), color = C.texto2, fontFamily = C.fuente, fontSize = 12.sp)
                }
                Text("Pellizcá para zoom", color = C.apagado, fontFamily = C.fuente, fontSize = 12.sp)
            }
        }

        if (vista.verCritico) {
            BannerCritico(critico, Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = 14.dp, end = 74.dp))
        }

        // Controles de zoom
        Column(
            Modifier.align(Alignment.BottomEnd).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BotonMapa("+", "Acercar") { zoomA((vista.escala * 1.35f).coerceAtMost(ESCALA_MAX)) }
            BotonMapa("−", "Alejar") { zoomA((vista.escala / 1.35f).coerceAtLeast(ESCALA_MIN)) }
            BotonMapa("⤢", "Ver todo el mapa") {
                val ajuste = minOf(vistaW / anchoPx, vistaH / altoPx).coerceIn(ESCALA_MIN, ESCALA_MAX)
                zoomA(ajuste)
            }
        }
    }
}

@Composable
private fun BotonCritico(activo: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(C.formas.pildora)
            .background(if (activo) C.critico else C.critico.copy(alpha = 0.12f))
            .border(1.dp, C.critico.copy(alpha = if (activo) 1f else 0.5f), C.formas.pildora)
            .semantics { contentDescription = if (activo) "Ocultar camino crítico" else "Ver camino crítico" }
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Ic(R.drawable.ic_ruta, if (activo) C.fondo else C.critico, tam = 16.dp)
        Text(
            if (activo) "Camino crítico" else "Ver camino crítico",
            Modifier.padding(start = 6.dp),
            color = if (activo) C.fondo else C.critico,
            fontFamily = C.fuente,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun BannerCritico(critico: CaminoCritico, modifier: Modifier) {
    Column(
        modifier
            .widthIn(max = 520.dp)
            .clip(C.formas.media)
            .background(C.fondoAlto.copy(alpha = 0.96f))
            .border(1.5.dp, C.critico, C.formas.media)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ic(R.drawable.ic_ruta, C.critico, tam = 16.dp)
            Text(
                if (critico.anios == 0) "NO TE QUEDAN CURSADAS" else "CAMINO CRÍTICO · ${textoAnios(critico.anios).uppercase()} COMO MÍNIMO",
                Modifier.padding(start = 6.dp),
                color = C.critico,
                fontFamily = C.fuente,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
            )
        }
        Text(
            if (critico.anios == 0) "Ya cursaste todo: solo te quedan finales."
            else critico.camino.joinToString("  ›  ") { it.nombre },
            Modifier.padding(top = 4.dp),
            color = C.texto,
            fontFamily = C.fuente,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun ContenidoMapa(
    avance: Avance,
    mapa: Mapa,
    critico: CaminoCritico,
    verCritico: Boolean,
    seleccionada: Int?,
    onAbrir: (Int) -> Unit,
) {
    val porId = remember(mapa) { mapa.nodos.associateBy { it.materia.id } }
    // Las correlativas se evalúan una vez por cambio de avance, no en cada recomposición ni en cada frame.
    val estados = remember(avance, mapa) { mapa.nodos.associate { it.materia.id to avance.estadoNodo(it.materia) } }
    // Con el camino crítico a la vista se atenúa todo lo demás.
    val resaltar = verCritico && critico.camino.isNotEmpty()
    val atenuado = 0.22f
    val pasos = remember(critico) { critico.camino.withIndex().associate { (i, m) -> m.id to i + 1 } }
    // Geometría de las conexiones: depende solo del mapa y la densidad, se arma una vez.
    val d = LocalDensity.current.density
    val trazos = remember(mapa, d) {
        mapa.aristas.mapNotNull { a ->
            val o = porId[a.desde] ?: return@mapNotNull null
            val t = porId[a.hasta] ?: return@mapNotNull null
            a to curva(o, t, d)
        }
    }
    // Qué correlativas ya se cumplen: una vez por cambio de avance.
    val cumplidas = remember(avance, mapa) {
        mapa.aristas.associateWith { a ->
            when (a.pide) {
                Nivel.REGULARIZADA -> avance.estado(a.desde) != Estado.SIN_CURSAR
                Nivel.APROBADA -> avance.estado(a.desde) == Estado.APROBADA
            }
        }
    }
    val trazosCriticos = remember(critico, mapa, d) {
        critico.camino.zipWithNext().mapNotNull { (m1, m2) ->
            val o = porId[m1.id] ?: return@mapNotNull null
            val t = porId[m2.id] ?: return@mapNotNull null
            curva(o, t, d)
        }
    }
    val guiones = remember(d) { PathEffect.dashPathEffect(floatArrayOf(7f * d, 5f * d)) }

    // Fondo: bandas por nivel y conexiones.
    Canvas(Modifier.fillMaxSize()) {
        (0 until 5).forEach { f ->
            val top = (Mapa.MARGEN_Y + f * Mapa.PASO_Y - 22f) * d
            if (f % 2 == 0) {
                drawRect(C.banda, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, Mapa.PASO_Y * d))
            }
        }
        trazos.forEach { (a, camino) ->
            val cumple = cumplidas.getValue(a)
            val recorrida = estados[a.hasta] == EstadoNodo.APROBADA
            val base = when {
                !cumple -> C.borde.copy(alpha = 0.5f)
                recorrida -> C.oro.copy(alpha = 0.16f)
                a.pide == Nivel.APROBADA -> C.oro.copy(alpha = 0.8f)
                else -> C.regular.copy(alpha = 0.8f)
            }
            val color = if (resaltar) base.copy(alpha = base.alpha * atenuado) else base
            drawPath(
                camino,
                color,
                style = Stroke(
                    width = (if (cumple && !recorrida) 2.6f else 1.6f) * d,
                    pathEffect = if (a.pide == Nivel.REGULARIZADA) guiones else null,
                ),
            )
        }
        // El camino crítico, encima de todo: un trazo con halo entre cada cursada.
        if (resaltar) {
            trazosCriticos.forEach { camino ->
                drawPath(camino, C.critico.copy(alpha = 0.22f), style = Stroke(width = 12f * d, cap = StrokeCap.Round))
                drawPath(camino, C.critico, style = Stroke(width = 4f * d, cap = StrokeCap.Round))
            }
        }
    }

    // Etiquetas de nivel
    (1..5).forEach { n ->
        Text(
            "NIVEL $n",
            Modifier.offset(x = 14.dp, y = (Mapa.MARGEN_Y + (n - 1) * Mapa.PASO_Y - 16f).dp),
            color = C.apagado,
            fontFamily = C.fuente,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 2.sp,
        )
    }

    // Nodos
    mapa.nodos.forEach { n ->
        val e = estados.getValue(n.materia.id)
        val anchoEtiqueta = Mapa.PASO_X - 8f
        val paso = pasos[n.materia.id]
        val elegida = n.materia.id == seleccionada
        // Aro detrás del nodo: rojo si está en el camino crítico, celeste si está abierta en el panel.
        if ((resaltar && paso != null) || elegida) {
            val aro = if (resaltar && paso != null) C.critico else C.disponible
            Box(
                Modifier
                    .offset(x = (n.x - TAM_NODO / 2 - 9f).dp, y = (n.y - TAM_NODO / 2 - 9f).dp)
                    .size((TAM_NODO + 18f).dp)
                    .clip(CircleShape)
                    .background(aro.copy(alpha = 0.16f))
                    .border(2.5.dp, aro, CircleShape),
            )
        }
        Column(
            Modifier
                .offset(x = (n.x - anchoEtiqueta / 2).dp, y = (n.y - TAM_NODO / 2).dp)
                .width(anchoEtiqueta.dp)
                .alpha(if (resaltar && paso == null && !elegida) atenuado else 1f)
                .clip(C.formas.chica)
                .semantics(mergeDescendants = true) { contentDescription = "${n.materia.nombre}, ${textoEstado(e)}" }
                .clickable(role = Role.Button) { onAbrir(n.materia.id) },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Nodo(n.materia, e, tam = TAM_NODO.dp)
            Text(
                n.materia.nombre,
                Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
                color = if (e == EstadoNodo.BLOQUEADA) C.bloqueadaTexto else C.texto,
                fontFamily = C.fuente,
                fontWeight = if (e == EstadoNodo.BLOQUEADA) FontWeight.Medium else FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (resaltar && paso != null) {
                Text(
                    "CURSADA $paso",
                    Modifier
                        .padding(top = 3.dp)
                        .clip(C.formas.pildora)
                        .background(C.critico)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                    color = C.fondo,
                    fontFamily = C.fuente,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

/** Curva entre dos nodos (de abajo del primero a arriba del segundo), en px. */
private fun curva(o: NodoMapa, t: NodoMapa, d: Float): Path {
    val radio = TAM_NODO / 2 * d
    val desde = Offset(o.x * d, o.y * d + radio)
    val hasta = Offset(t.x * d, t.y * d - radio)
    val medio = (hasta.y - desde.y) / 2
    return Path().apply {
        moveTo(desde.x, desde.y)
        cubicTo(desde.x, desde.y + medio, hasta.x, hasta.y - medio, hasta.x, hasta.y)
    }
}
