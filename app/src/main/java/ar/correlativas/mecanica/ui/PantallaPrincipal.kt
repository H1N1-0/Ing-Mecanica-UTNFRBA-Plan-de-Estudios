package ar.correlativas.mecanica.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.Materia
import ar.correlativas.mecanica.data.Plan
import ar.correlativas.mecanica.data.Tipo
import ar.correlativas.mecanica.data.formatearHoras
import kotlinx.coroutines.launch

enum class Filtro(val texto: String) { TODAS("Todas"), CURSAR("Para cursar"), RENDIR("Para rendir") }

private data class Seccion(val clave: String, val titulo: String, val corto: String, val materias: List<Materia>)

private fun secciones(plan: Plan): List<Seccion> =
    (1..5).map { n ->
        val materias = plan.obligatorias.filter { it.nivel == n } + if (n == 5) listOf(plan.pps) else emptyList()
        Seccion("n$n", "Nivel $n", "Nivel $n", materias)
    } + Seccion("elec", "Electivas", "Electivas", plan.electivas)

@Composable
fun PantallaPrincipal(vm: CorrelativasViewModel) {
    val avance by vm.avance.collectAsStateWithLifecycle()
    val p = LocalPaleta.current
    Box(
        Modifier
            .fillMaxSize()
            .background(p.papel),
    ) {
        val a = avance
        if (a == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        } else {
            Contenido(a, vm)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Contenido(avance: Avance, vm: CorrelativasViewModel) {
    val p = LocalPaleta.current
    val plan = avance.plan
    var filtro by rememberSaveable { mutableStateOf(Filtro.TODAS) }
    var detalle by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmarBorrado by remember { mutableStateOf(false) }
    val lista = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val visibles = secciones(plan).map { s ->
        s.copy(materias = s.materias.filter {
            when (filtro) {
                Filtro.TODAS -> true
                Filtro.CURSAR -> avance.puedeCursar(it)
                Filtro.RENDIR -> avance.puedeRendir(it)
            }
        })
    }.filter { it.materias.isNotEmpty() }

    // Índice de cada encabezado de sección dentro de la LazyColumn, para el salto rápido.
    val indices = buildMap {
        var i = 1 // el ítem 0 es el rótulo con el resumen
        visibles.forEach { s -> put(s.clave, i); i += 1 + s.materias.size }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        BarraSuperior(
            filtro = filtro,
            onFiltro = { filtro = it; scope.launch { lista.scrollToItem(0) } },
            secciones = visibles,
            onSaltar = { clave -> indices[clave]?.let { scope.launch { lista.animateScrollToItem(it) } } },
            onBorrar = { confirmarBorrado = true },
        )
        LazyColumn(
            state = lista,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "rotulo") {
                Rotulo(avance, onFiltro = { filtro = if (filtro == it) Filtro.TODAS else it })
            }
            if (visibles.isEmpty()) {
                item(key = "vacio") {
                    Text(
                        when (filtro) {
                            Filtro.CURSAR -> "No hay materias que puedas cursar ahora."
                            Filtro.RENDIR -> "No tenés finales para rendir: primero regularizá alguna materia."
                            Filtro.TODAS -> ""
                        },
                        Modifier.padding(24.dp),
                        color = p.tinta2,
                    )
                }
            }
            visibles.forEach { s ->
                stickyHeader(key = "h-${s.clave}") { EncabezadoSeccion(s, plan) }
                items(s.materias, key = { it.id }) { m ->
                    FilaMateria(
                        materia = m,
                        avance = avance,
                        onCambio = { vm.cambiar(m.id, it) },
                        onAbrir = { detalle = m.id },
                    )
                }
            }
            item(key = "notas") { Notas(plan) }
        }
    }

    detalle?.let { id ->
        plan[id]?.let { m ->
            HojaDetalle(
                materia = m,
                avance = avance,
                onCambio = { vm.cambiar(m.id, it) },
                onAbrir = { detalle = it },
                onCerrar = { detalle = null },
            )
        }
    }

    if (confirmarBorrado) {
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            title = { Text("¿Borrar tu progreso?") },
            text = { Text("Todas las materias vuelven a \"sin cursar\". No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { vm.borrarTodo(); confirmarBorrado = false }) {
                    Text("Borrar", color = p.peligro)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun BarraSuperior(
    filtro: Filtro,
    onFiltro: (Filtro) -> Unit,
    secciones: List<Seccion>,
    onSaltar: (String) -> Unit,
    onBorrar: () -> Unit,
) {
    val p = LocalPaleta.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.background(p.papel)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val forma = RoundedCornerShape(9.dp)
            Row(
                Modifier
                    .clip(forma)
                    .border(1.5.dp, p.lineaFuerte, forma),
            ) {
                Filtro.entries.forEachIndexed { i, f ->
                    if (i > 0) Box(Modifier.width(1.5.dp).height(38.dp).background(p.lineaFuerte))
                    val elegido = f == filtro
                    Box(
                        Modifier
                            .height(38.dp)
                            .background(if (elegido) p.tinta else Color.Transparent)
                            .clickable { onFiltro(f) }
                            .padding(horizontal = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            f.texto,
                            color = if (elegido) p.papel else p.tinta,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Más opciones", tint = p.tinta)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Borrar mi progreso") },
                        onClick = { menu = false; onBorrar() },
                    )
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            items(secciones, key = { it.clave }) { s ->
                val forma = RoundedCornerShape(9.dp)
                Box(
                    Modifier
                        .clip(forma)
                        .border(1.5.dp, p.linea, forma)
                        .background(p.superficie)
                        .clickable { onSaltar(s.clave) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(s.corto, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = p.tinta)
                }
            }
        }
        HorizontalDivider(color = p.linea)
    }
}

@Composable
private fun Rotulo(avance: Avance, onFiltro: (Filtro) -> Unit) {
    val p = LocalPaleta.current
    val plan = avance.plan
    Column(
        Modifier
            .padding(14.dp)
            .border(2.dp, p.lineaFuerte)
            .background(p.superficie)
            .grilla(p.grilla),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
            Text(
                plan.carrera,
                style = MaterialTheme.typography.displaySmall,
                color = p.tinta,
                lineHeight = 38.sp,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                "${plan.facultad} · Plan 2023. Marcá cada materia y la app calcula qué podés cursar y rendir.",
                color = p.tinta2,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        HorizontalDivider(color = p.lineaFuerte)
        Row {
            Celda(
                titulo = "Podés cursar",
                valor = avance.cursables.size.toString(),
                color = p.acento,
                modifier = Modifier.weight(1f).clickable { onFiltro(Filtro.CURSAR) },
            )
            Box(Modifier.width(1.dp).height(86.dp).background(p.lineaFuerte))
            Celda(
                titulo = "Podés rendir",
                valor = avance.rendibles.size.toString(),
                color = p.apr,
                modifier = Modifier.weight(1f).clickable { onFiltro(Filtro.RENDIR) },
            )
        }
        HorizontalDivider(color = p.lineaFuerte)
        Row {
            Celda(
                titulo = "Aprobadas",
                valor = "${avance.aprobadasObligatorias}",
                de = "de ${plan.obligatorias.size}",
                progreso = avance.aprobadasObligatorias / plan.obligatorias.size.toFloat(),
                color = p.apr,
                modifier = Modifier.weight(1f),
            )
            Box(Modifier.width(1.dp).height(86.dp).background(p.lineaFuerte))
            Celda(
                titulo = "Electivas",
                valor = formatearHoras(avance.horasElectivas),
                de = "de ${formatearHoras(Plan.HORAS_ELECTIVAS_REQUERIDAS)} hs",
                progreso = (avance.horasElectivas / Plan.HORAS_ELECTIVAS_REQUERIDAS).toFloat().coerceAtMost(1f),
                color = p.reg,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Celda(
    titulo: String,
    valor: String,
    color: Color,
    modifier: Modifier = Modifier,
    de: String? = null,
    progreso: Float? = null,
) {
    val p = LocalPaleta.current
    Column(modifier.height(86.dp).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(titulo, fontSize = 13.sp, color = p.tinta2)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valor, fontFamily = Condensada, fontWeight = FontWeight.Bold, fontSize = 32.sp, color = color, lineHeight = 34.sp)
            if (de != null) {
                Text(de, fontSize = 14.sp, color = p.tinta2, modifier = Modifier.padding(start = 5.dp, bottom = 5.dp))
            }
        }
        if (progreso != null) {
            LinearProgressIndicator(
                progress = { progreso },
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp).height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = color,
                trackColor = p.neutroFondo,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
        }
    }
}

@Composable
private fun EncabezadoSeccion(s: Seccion, plan: Plan) {
    val p = LocalPaleta.current
    Column(Modifier.fillMaxWidth().background(p.papel)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                s.titulo,
                style = MaterialTheme.typography.headlineSmall,
                color = p.tinta,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.weight(1f))
            val n = s.materias.size
            Text(
                if (s.clave == "elec") "$n electivas" else "$n ${if (n == 1) "materia" else "materias"}",
                color = p.tinta2,
                fontSize = 14.sp,
            )
        }
        HorizontalDivider(color = p.lineaFuerte, thickness = 2.dp)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilaMateria(materia: Materia, avance: Avance, onCambio: (Estado) -> Unit, onAbrir: () -> Unit) {
    val p = LocalPaleta.current
    val estado = avance.estado(materia.id)
    Column(
        Modifier
            .fillMaxWidth()
            .background(p.superficie)
            .clickable(onClickLabel = "Ver detalle", onClick = onAbrir)
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Globo(numeroDe(materia), estado)
            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    materia.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    color = p.tinta,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    EstadoCorrelativas(materia, avance)
                    etiquetas(materia).forEach { Etiqueta(it, p.tinta2, p.neutroFondo) }
                }
            }
        }
        SelectorEstado(
            tipo = materia.tipo,
            estado = estado,
            onCambio = onCambio,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 46.dp),
        )
    }
    HorizontalDivider(color = p.linea)
}

/** Línea de estado de la fila: qué podés hacer o cuánto te falta. */
@Composable
fun EstadoCorrelativas(materia: Materia, avance: Avance) {
    val p = LocalPaleta.current
    val estado = avance.estado(materia.id)
    when {
        avance.puedeRendir(materia) -> Etiqueta("Podés rendir", p.apr, p.aprFondo)
        avance.puedeCursar(materia) ->
            Etiqueta(if (materia.tipo == Tipo.PPS) "Podés empezarla" else "Podés cursar", p.acento, p.acentoFondo)
        estado == Estado.APROBADA ->
            Etiqueta(if (materia.tipo == Tipo.PPS) "Acreditada" else "Aprobada", p.apr, Color.Transparent)
        estado == Estado.REGULARIZADA -> {
            val f = avance.faltantesRendir(materia).size
            val h = avance.faltanHorasParaRendir(materia)
            val texto = buildList {
                if (f > 0) add("faltan $f para rendir")
                if (h > 0) add("faltan ${formatearHoras(h)} hs de electivas")
            }.joinToString(" · ").replaceFirstChar { it.uppercase() }
            Etiqueta(texto.ifEmpty { "Regularizada" }, p.reg, p.regFondo)
        }
        else -> {
            val f = avance.faltantesCursar(materia).size
            Etiqueta("Te ${if (f == 1) "falta 1 correlativa" else "faltan $f correlativas"}", p.apagado, Color.Transparent)
        }
    }
}

fun numeroDe(m: Materia): String = when (m.tipo) {
    Tipo.PPS -> "PPS"
    Tipo.ELECTIVA -> "E"
    Tipo.OBLIGATORIA -> m.id.toString()
}

fun etiquetas(m: Materia): List<String> = buildList {
    if (m.integradora) add("Integradora")
    if (m.tipo == Tipo.ELECTIVA) {
        add(m.nivel?.let { "Nivel $it" } ?: "Sin nivel")
        add(m.horasAnuales?.let { "${formatearHoras(it)} hs" } ?: "hs sin dato")
        if (m.requeridaTituloIntermedio) add("Título intermedio")
    }
}

@Composable
private fun Notas(plan: Plan) {
    val p = LocalPaleta.current
    Column(Modifier.padding(horizontal = 14.dp, vertical = 20.dp)) {
        Text("Cómo se calcula", style = MaterialTheme.typography.titleLarge, color = p.tinta)
        val textos = listOfNotNull(
            plan.reglas["cursar"]?.let { "Para cursar: $it" },
            plan.reglas["rendir"]?.let { "Para rendir: $it" },
            "Proyecto Final: se rinde con todas las obligatorias aprobadas y ${formatearHoras(Plan.HORAS_ELECTIVAS_REQUERIDAS)} horas anuales de electivas aprobadas.",
            plan.reglas["pps"]?.let { "PPS: $it" },
            plan.reglas["electivas"]?.let { "Electivas: $it" },
        )
        textos.forEach {
            Text(it, color = p.tinta2, fontSize = 14.5.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            "Plan ${plan.plan}. Si algo no coincide con el SIU Guaraní, consultalo en el Departamento de Mecánica.",
            color = p.apagado,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 12.dp).windowInsetsPadding(WindowInsets.navigationBars),
        )
    }
}
