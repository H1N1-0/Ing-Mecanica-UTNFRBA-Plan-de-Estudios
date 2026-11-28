package ar.correlativas.mecanica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.Estadisticas
import ar.correlativas.mecanica.data.NotaDeMateria
import ar.correlativas.mecanica.data.PuntoPromedio
import ar.correlativas.mecanica.data.formatearPromedio

/** Estadísticas de notas: promedios, finales, evolución por año y mejor y peor nota. */
@Composable
fun PantallaEstadisticas(e: Estadisticas, onAbrir: (Int) -> Unit, onVolver: () -> Unit) {
    BackHandler(onBack = onVolver)
    Column(Modifier.fillMaxSize()) {
        BarraSuperior("Estadísticas", onVolver)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DatoGrande("Promedio", formatearPromedio(e.promedioSinAplazos), "sin aplazos", C.disponible, Modifier.weight(1f))
                DatoGrande("Promedio", formatearPromedio(e.promedioConAplazos), "con aplazos", C.regular, Modifier.weight(1f))
            }
            // Cómo se aprobó cada materia: con final o por promoción (sin final, con nota).
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DatoGrande("Por final", "${e.porFinal}", "de ${e.aprobadas.size} aprobadas", C.oro, Modifier.weight(1f))
                DatoGrande("Por promoción", "${e.porPromocion}", "de ${e.aprobadas.size} aprobadas", C.oro, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DatoGrande("Finales rendidos", "${e.finalesRendidos}", "${e.porFinal} aprobados · ${e.aplazos.size} aplazos", C.texto, Modifier.weight(1f))
                DatoGrande("Con nota", "${e.notas.size}", "de ${e.aprobadas.size} aprobadas", C.texto, Modifier.weight(1f))
            }
            if (!e.hayDatos) {
                Tarjeta(Modifier.fillMaxWidth()) {
                    Text(
                        "Todavía no cargaste notas. Cuando marcás una materia como aprobada podés poner la nota y la fecha " +
                            "del final (al promocionarla, la nota es obligatoria); también podés hacerlo desde el detalle " +
                            "de cada materia, junto con los aplazos.",
                        Modifier.padding(16.dp),
                        color = C.texto2, fontFamily = C.fuente, fontSize = 14.sp, lineHeight = 19.sp,
                    )
                }
                return@Column
            }
            Tarjeta(Modifier.fillMaxWidth(), cota = "EVOLUCIÓN") {
                Column(Modifier.padding(16.dp)) {
                    Text("Promedio acumulado por año", color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (e.evolucion.isEmpty()) {
                        Text(
                            "Cargá la fecha de los finales para ver cómo cambia tu promedio año a año.",
                            Modifier.padding(top = 8.dp), color = C.texto2, fontFamily = C.fuente, fontSize = 14.sp,
                        )
                    } else {
                        GraficoEvolucion(e.evolucion, Modifier.fillMaxWidth().padding(top = 12.dp).height(200.dp))
                        TablaEvolucion(e.evolucion)
                    }
                    if (e.sinFecha > 0) {
                        Text(
                            "${e.sinFecha} ${if (e.sinFecha == 1) "nota sin fecha cuenta" else "notas sin fecha cuentan"} en el promedio pero no en el gráfico.",
                            Modifier.padding(top = 8.dp), color = C.texto2, fontFamily = C.fuente, fontSize = 12.5.sp,
                        )
                    }
                }
            }
            if (e.mejor != null && e.peor != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NotaDestacada("Mejor nota", e.mejor, C.rendir, R.drawable.ic_estrella, onAbrir, Modifier.weight(1f))
                    NotaDestacada("Peor nota", e.peor, C.regular, R.drawable.ic_mitad, onAbrir, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DatoGrande(titulo: String, valor: String, detalle: String, color: Color, modifier: Modifier) {
    Tarjeta(modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(titulo, color = C.texto2, fontFamily = C.fuente, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (color != C.texto) Box(Modifier.size(10.dp).padding(end = 0.dp)) { Canvas(Modifier.size(10.dp)) { drawCircle(color) } }
                Text(valor, Modifier.padding(start = if (color != C.texto) 8.dp else 0.dp), color = C.texto, fontFamily = C.fuenteDatos, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            }
            Text(detalle, color = C.texto2, fontFamily = C.fuente, fontSize = 12.5.sp)
        }
    }
}

@Composable
private fun NotaDestacada(titulo: String, n: NotaDeMateria, color: Color, icono: Int, onAbrir: (Int) -> Unit, modifier: Modifier) {
    Tarjeta(modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(icono, color, tam = 16.dp)
                Text(titulo, Modifier.padding(start = 6.dp), color = C.texto2, fontFamily = C.fuente, fontSize = 13.sp)
            }
            Text("${n.nota}", color = C.texto, fontFamily = C.fuenteDatos, fontWeight = FontWeight.Bold, fontSize = 30.sp)
            NombreOAbrev(
                n.materia,
                TextStyle(color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 17.sp),
                Modifier.padding(top = 2.dp),
            )
            Text(formatearFecha(n.fecha), color = C.texto2, fontFamily = C.fuente, fontSize = 12.5.sp)
            Text("Ver materia", Modifier.padding(top = 6.dp).semantics { contentDescription = "Ver ${n.materia.nombre}" }
                .pointerInput(n.materia.id) { detectTapGestures { onAbrir(n.materia.id) } },
                color = C.disponible, fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

/**
 * Promedio acumulado por año. Dos series en un solo eje (notas 1–10): "sin aplazos" con línea
 * llena y círculos, "con aplazos" con línea de trazos y cuadrados, así el color nunca es la única
 * pista. Tocar un año muestra sus valores. Colores, grilla y fuentes salen del estilo activo.
 */
@Composable
fun GraficoEvolucion(puntos: List<PuntoPromedio>, modifier: Modifier = Modifier) {
    val medidor = rememberTextMeasurer()
    var elegido by rememberSaveable { mutableStateOf<Int?>(null) }
    val descripcion = remember(puntos) {
        puntos.joinToString("; ") { "${it.anio}: ${formatearPromedio(it.sinAplazos)} sin aplazos, ${formatearPromedio(it.conAplazos)} con aplazos" }
    }
    // Eje y: del piso entero bajo el mínimo al techo sobre el máximo (entre 1 y 10), sin exagerar diferencias chicas.
    val valores = puntos.flatMap { listOfNotNull(it.sinAplazos, it.conAplazos) }
    val min = (kotlin.math.floor(valores.minOrNull() ?: 1.0) - 1).coerceIn(1.0, 9.0)
    val max = (kotlin.math.ceil(valores.maxOrNull() ?: 10.0) + 1).coerceIn(min + 1, 10.0)
    Column(modifier.semantics { contentDescription = "Gráfico de evolución del promedio. $descripcion" }) {
        Leyenda()
        Canvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(puntos) {
                    detectTapGestures { p ->
                        val izq = 34.dp.toPx()
                        val ancho = size.width - izq - 16.dp.toPx()
                        val i = if (puntos.size == 1) 0 else ((p.x - izq) / ancho * (puntos.size - 1)).let { kotlin.math.round(it).toInt() }
                        elegido = puntos.getOrNull(i.coerceIn(0, puntos.lastIndex))?.anio
                    }
                },
        ) {
            val d = density
            val izq = 34f * d
            val der = 16f * d
            val arriba = 10f * d
            val abajo = 22f * d
            val w = size.width - izq - der
            val h = size.height - arriba - abajo
            fun x(i: Int) = izq + if (puntos.size == 1) w / 2 else w * i / (puntos.size - 1)
            fun y(v: Double) = arriba + h * (1 - ((v - min) / (max - min))).toFloat()
            val estiloEje = TextStyle(fontFamily = C.fuenteDatos, fontSize = 10.sp, color = C.texto2)
            // Grilla tenue en cada nota entera y etiquetas del eje.
            var v = min
            while (v <= max + 1e-9) {
                val yy = y(v)
                drawLine(C.borde.copy(alpha = 0.45f), Offset(izq, yy), Offset(izq + w, yy), 0.8f * d)
                val t = medidor.measure(v.toInt().toString(), estiloEje)
                drawText(t, topLeft = Offset(izq - t.size.width - 6f * d, yy - t.size.height / 2))
                v += 1.0
            }
            puntos.forEachIndexed { i, p ->
                val t = medidor.measure("${p.anio}", estiloEje)
                drawText(t, topLeft = Offset(x(i) - t.size.width / 2, arriba + h + 6f * d))
            }
            elegido?.let { anio ->
                val i = puntos.indexOfFirst { it.anio == anio }
                if (i >= 0) drawLine(C.texto2.copy(alpha = 0.5f), Offset(x(i), arriba), Offset(x(i), arriba + h), 1f * d)
            }
            fun serie(valor: (PuntoPromedio) -> Double?, color: Color, trazos: Boolean) {
                val pts = puntos.mapIndexedNotNull { i, p -> valor(p)?.let { Offset(x(i), y(it)) } }
                if (pts.size > 1) {
                    val path = Path().apply { pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
                    drawPath(
                        path, color,
                        style = Stroke(2f * d, cap = StrokeCap.Round, pathEffect = if (trazos) PathEffect.dashPathEffect(floatArrayOf(6f * d, 4f * d)) else null),
                    )
                }
                pts.forEach { o ->
                    // Anillo del color de la tarjeta para separar marcadores superpuestos.
                    if (trazos) {
                        drawRect(C.tarjeta, o - Offset(6f * d, 6f * d), Size(12f * d, 12f * d))
                        drawRect(color, o - Offset(4.5f * d, 4.5f * d), Size(9f * d, 9f * d))
                    } else {
                        drawCircle(C.tarjeta, 6.5f * d, o)
                        drawCircle(color, 4.5f * d, o)
                    }
                }
            }
            serie({ it.conAplazos }, C.regular, trazos = true)
            serie({ it.sinAplazos }, C.disponible, trazos = false)
            // Etiqueta directa del último valor de cada serie (texto en tinta de texto, no del color de la serie).
            val ultimo = puntos.last()
            val estiloValor = TextStyle(fontFamily = C.fuenteDatos, fontSize = 11.sp, color = C.texto, fontWeight = FontWeight.Bold)
            listOfNotNull(ultimo.sinAplazos?.let { it to -1f }, ultimo.conAplazos?.let { it to 1f }).forEach { (val0, lado) ->
                val t = medidor.measure(formatearPromedio(val0), estiloValor)
                val yy = (y(val0) + lado * 9f * d - t.size.height / 2).coerceIn(0f, size.height - t.size.height)
                drawText(t, topLeft = Offset((x(puntos.lastIndex) - t.size.width - 8f * d).coerceAtLeast(izq), yy))
            }
        }
        val sel = puntos.firstOrNull { it.anio == elegido }
        Text(
            if (sel == null) "Tocá un año para ver sus valores."
            else "${sel.anio}: ${formatearPromedio(sel.sinAplazos)} sin aplazos · ${formatearPromedio(sel.conAplazos)} con aplazos",
            Modifier.padding(top = 6.dp),
            color = if (sel == null) C.texto2 else C.texto,
            fontFamily = C.fuente,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun Leyenda() {
    Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        MuestraSerie(C.disponible, trazos = false)
        Text("Sin aplazos", Modifier.padding(start = 6.dp, end = 16.dp), color = C.texto, fontFamily = C.fuente, fontSize = 13.sp)
        MuestraSerie(C.regular, trazos = true)
        Text("Con aplazos", Modifier.padding(start = 6.dp), color = C.texto, fontFamily = C.fuente, fontSize = 13.sp)
    }
}

@Composable
private fun MuestraSerie(color: Color, trazos: Boolean) {
    Canvas(Modifier.width(26.dp).height(12.dp)) {
        val y = size.height / 2
        drawLine(color, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), pathEffect = if (trazos) PathEffect.dashPathEffect(floatArrayOf(5f, 4f)) else null)
        if (trazos) drawRect(color, Offset(size.width / 2 - 4.dp.toPx(), y - 4.dp.toPx()), Size(8.dp.toPx(), 8.dp.toPx()))
        else drawCircle(color, 4.dp.toPx(), Offset(size.width / 2, y))
    }
}

/** La misma información en tabla, para leerla sin el gráfico. */
@Composable
private fun TablaEvolucion(puntos: List<PuntoPromedio>) {
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row {
            listOf("Año", "Sin aplazos", "Con aplazos").forEach {
                Text(it, Modifier.weight(1f), color = C.texto2, fontFamily = C.fuente, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
            }
        }
        puntos.forEach { p ->
            Row(Modifier.padding(top = 2.dp)) {
                listOf("${p.anio}", formatearPromedio(p.sinAplazos), formatearPromedio(p.conAplazos)).forEach {
                    Text(it, Modifier.weight(1f), color = C.texto, fontFamily = C.fuenteDatos, fontSize = 13.sp)
                }
            }
        }
    }
}
