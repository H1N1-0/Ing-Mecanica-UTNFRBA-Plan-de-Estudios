package ar.correlativas.mecanica.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.coincide
import ar.correlativas.mecanica.data.normalizar
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.EstadoNodo
import ar.correlativas.mecanica.data.Materia
import ar.correlativas.mecanica.data.Tipo
import ar.correlativas.mecanica.data.estadoNodo
import ar.correlativas.mecanica.data.formatearHoras

private data class Grupo(val titulo: String, val materias: List<Materia>)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PantallaLista(
    avance: Avance,
    filtro: EstadoNodo?,
    onFiltro: (EstadoNodo?) -> Unit,
    onCambio: (Int, Estado) -> Unit,
    onAbrir: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Materia abierta en el panel de al lado (tablet), para marcarla. */
    seleccionada: Int? = null,
    scroll: LazyListState = rememberLazyListState(),
) {
    val plan = avance.plan
    var busqueda by rememberSaveable { mutableStateOf("") }
    val q = normalizar(busqueda.trim())

    // Filtrar recorre todas las materias: se recalcula solo si cambia el avance, el filtro o la búsqueda.
    val grupos = remember(avance, filtro, q) { ((1..5).map { n ->
        Grupo("Nivel $n", plan.obligatorias.filter { it.nivel == n } + if (n == 5) listOf(plan.pps) else emptyList())
    } + Grupo("Electivas", plan.electivas)).map { g ->
        g.copy(materias = g.materias.filter { m ->
            (filtro == null || avance.estadoNodo(m) == filtro ||
                (filtro == EstadoNodo.REGULARIZADA && avance.estadoNodo(m) == EstadoNodo.PARA_RENDIR) ||
                // "Aprobadas" incluye las promocionadas; "Promocionadas" muestra solo esas.
                (filtro == EstadoNodo.APROBADA && avance.estadoNodo(m) == EstadoNodo.PROMOCIONADA)) &&
                m.coincide(q)
        })
    }.filter { it.materias.isNotEmpty() } }

    Column(modifier.fillMaxSize()) {
        Column(Modifier.background(C.fondo).padding(top = 12.dp)) {
            OutlinedTextField(
                value = busqueda,
                onValueChange = { busqueda = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                placeholder = { Text("Buscar por nombre, sigla o número", fontFamily = C.fuente) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = C.texto2) },
                trailingIcon = {
                    if (busqueda.isNotEmpty()) {
                        IconButton(onClick = { busqueda = "" }) { Icon(Icons.Default.Close, contentDescription = "Borrar búsqueda", tint = C.texto2) }
                    }
                },
                singleLine = true,
                shape = C.formas.media,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = C.tarjeta,
                    unfocusedContainerColor = C.tarjeta,
                    focusedBorderColor = C.disponible,
                    unfocusedBorderColor = C.borde,
                    cursorColor = C.disponible,
                ),
            )
            val filtros = listOf<Pair<EstadoNodo?, String>>(
                null to "Todas",
                EstadoNodo.DISPONIBLE to "Disponibles",
                EstadoNodo.PARA_RENDIR to "Para rendir",
                EstadoNodo.REGULARIZADA to "Regularizadas",
                EstadoNodo.APROBADA to "Aprobadas",
                EstadoNodo.PROMOCIONADA to "Promocionadas",
                EstadoNodo.BLOQUEADA to "Bloqueadas",
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(filtros, key = { it.second }) { (e, texto) ->
                    val elegido = e == filtro
                    val color = e?.let { colorEstado(it) } ?: C.texto
                    Row(
                        Modifier
                            .clip(C.formas.pildora)
                            .background(if (elegido) color.copy(alpha = 0.2f) else C.tarjeta)
                            .border(1.dp, if (elegido) color else C.borde, C.formas.pildora)
                            .clickable { onFiltro(e) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (e != null) Ic(iconoEstado(e), color, tam = 14.dp, modifier = Modifier.padding(end = 6.dp))
                        Text(texto, color = if (elegido) color else C.texto2, fontFamily = C.fuente, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
            HorizontalDivider(color = C.borde)
        }
        LazyColumn(Modifier.fillMaxSize(), state = scroll, contentPadding = PaddingValues(bottom = 24.dp)) {
            if (grupos.isEmpty()) {
                item { Text("No hay materias con ese filtro.", Modifier.padding(24.dp), color = C.texto2, fontFamily = C.fuente) }
            }
            grupos.forEach { g ->
                stickyHeader(key = "h-${g.titulo}", contentType = "nivel") {
                    Row(
                        Modifier.fillMaxWidth().background(C.fondo).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(g.titulo.uppercase(), color = C.texto2, fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f).semantics { heading() })
                        Text("${g.materias.size}", color = C.apagado, fontFamily = C.fuente, fontSize = 13.sp)
                    }
                }
                items(g.materias, key = { it.id }, contentType = { "materia" }) { m -> FilaLista(m, avance, m.id == seleccionada, onCambio, onAbrir) }
            }
        }
    }
}

@Composable
private fun FilaLista(m: Materia, avance: Avance, elegida: Boolean, onCambio: (Int, Estado) -> Unit, onAbrir: (Int) -> Unit) {
    val e = avance.estadoNodo(m)
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (elegida) C.disponible.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClickLabel = "Ver detalle") { onAbrir(m.id) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(top = 4.dp, end = 4.dp)) { Nodo(m, e, tam = 42.dp) }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(
                m.nombre,
                color = if (e == EstadoNodo.BLOQUEADA) C.texto2 else C.texto,
                fontFamily = C.fuente,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.5.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                // La sigla, para ubicarla rápido y porque también se busca por ella.
                Text(m.abrev, color = C.texto2, fontFamily = C.fuenteDatos, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1)
                Text(" · ", color = C.apagado, fontFamily = C.fuente, fontSize = 12.5.sp)
                Text(textoEstado(e, m.tipo == Tipo.PPS), color = colorEstado(e), fontFamily = C.fuente, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
                val extra = when {
                    e == EstadoNodo.BLOQUEADA -> avance.faltantesCursar(m).size.let { " · faltan $it" }
                    m.tipo == Tipo.ELECTIVA -> " · " + (m.horasAnuales?.let { "${formatearHoras(it)} hs" } ?: "hs sin dato")
                    else -> ""
                }
                Text(extra, color = C.apagado, fontFamily = C.fuente, fontSize = 12.5.sp, maxLines = 1)
            }
        }
        Spacer(Modifier.height(1.dp))
        SelectorEstado(m.tipo, avance.estado(m.id), { onCambio(m.id, it) }, Modifier.padding(start = 8.dp), compacto = true)
    }
    HorizontalDivider(Modifier.padding(start = 70.dp), color = C.borde.copy(alpha = 0.5f))
}
