package ar.correlativas.mecanica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.APLAZO_MAX
import ar.correlativas.mecanica.data.Aplazo
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.Libreta
import ar.correlativas.mecanica.data.Materia
import ar.correlativas.mecanica.data.NOTA_MAX
import ar.correlativas.mecanica.data.NOTA_MIN
import ar.correlativas.mecanica.data.NotaFinal
import ar.correlativas.mecanica.data.Tipo
import ar.correlativas.mecanica.data.aprobada
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Notas cargadas y cómo pedir cambios; lo provee App para el detalle de cada materia. */
class AccionesNotas(
    val libreta: Libreta,
    val editarFinal: (Int) -> Unit,
    val agregarAplazo: (Int) -> Unit,
    val borrarAplazo: (Long) -> Unit,
)

val LocalNotas = staticCompositionLocalOf<AccionesNotas?> { null }

private val formatoFecha = DateTimeFormatter.ofPattern("dd/MM/yyyy")

fun formatearFecha(f: LocalDate?): String = f?.format(formatoFecha) ?: "Sin fecha"

/**
 * Nota y fecha al aprobar una materia. Con final todo es opcional: "Sin nota" guarda la materia
 * como aprobada igual. Con [promocion] la nota es obligatoria: "Guardar" no se activa sin nota y
 * cerrar el diálogo cancela (la materia no se promociona).
 */
@Composable
fun DialogoNotaFinal(
    materia: Materia,
    inicial: NotaFinal?,
    onGuardar: (NotaFinal?) -> Unit,
    onCerrar: () -> Unit,
    promocion: Boolean = false,
) {
    var nota by rememberSaveable { mutableStateOf(inicial?.nota) }
    var fecha by rememberSaveable { mutableStateOf(inicial?.fecha?.toString()) }
    val puedeGuardar = !promocion || nota != null
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = C.tarjeta,
        shape = C.formas.grande,
        title = {
            Text(if (promocion) "Nota de la promoción" else "Nota del final", color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    if (promocion) "${materia.nombre}. Para promocionarla, elegí la nota (es obligatoria)."
                    else "${materia.nombre}. Es opcional: sin nota, la materia cuenta igual como aprobada.",
                    color = C.texto2, fontFamily = C.fuente, fontSize = 14.sp,
                )
                SelectorNumero(NOTA_MIN..NOTA_MAX, nota, { nota = it }, Modifier.padding(top = 14.dp))
                CampoFecha(
                    fecha?.let(LocalDate::parse), { fecha = it?.toString() }, Modifier.padding(top = 12.dp),
                    rotulo = if (promocion) "Fecha de la promoción (opcional)" else "Fecha del final (opcional)",
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onGuardar(NotaFinal(nota, fecha?.let(LocalDate::parse))) }, enabled = puedeGuardar) {
                Text(
                    if (promocion && inicial?.nota == null) "Promocionar" else "Guardar",
                    color = if (puedeGuardar) C.disponible else C.apagado, fontFamily = C.fuente, fontWeight = FontWeight.Bold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCerrar) {
                Text(if (inicial == null && !promocion) "Sin nota" else "Cancelar", color = C.texto2, fontFamily = C.fuente)
            }
        },
    )
}

/** Un aplazo: nota (1 a 5) y fecha opcional. */
@Composable
fun DialogoAplazo(materia: Materia, onGuardar: (Int, LocalDate?) -> Unit, onCerrar: () -> Unit) {
    var nota by rememberSaveable { mutableStateOf<Int?>(2) }
    var fecha by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = C.tarjeta,
        shape = C.formas.grande,
        title = { Text("Agregar aplazo", color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "${materia.nombre}. Un final desaprobado: cuenta en el promedio con aplazos.",
                    color = C.texto2, fontFamily = C.fuente, fontSize = 14.sp,
                )
                SelectorNumero(NOTA_MIN..APLAZO_MAX, nota, { if (it != null) nota = it }, Modifier.padding(top = 14.dp), color = C.peligro)
                CampoFecha(fecha?.let(LocalDate::parse), { fecha = it?.toString() }, Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { nota?.let { onGuardar(it, fecha?.let(LocalDate::parse)) } }) {
                Text("Agregar", color = C.disponible, fontFamily = C.fuente, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = C.texto2, fontFamily = C.fuente) } },
    )
}

/** Botones de nota (1 a 10, o 1 a 5 para aplazos). Tocar la elegida la saca. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectorNumero(rango: IntRange, elegido: Int?, onElegir: (Int?) -> Unit, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = C.oro) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rango.forEach { n ->
            val si = n == elegido
            Box(
                Modifier
                    .size(40.dp)
                    .clip(C.formas.mini)
                    .background(if (si) color else C.fondoAlto)
                    .border(1.dp, if (si) color else C.borde, C.formas.mini)
                    .semantics { selected = si; contentDescription = "Nota $n" }
                    .clickable(role = Role.RadioButton) { onElegir(if (si) null else n) },
                contentAlignment = Alignment.Center,
            ) {
                Text("$n", color = if (si) C.sobreOro else C.texto, fontFamily = C.fuenteDatos, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

/** Fecha opcional con el selector de fecha de Material. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CampoFecha(fecha: LocalDate?, onCambio: (LocalDate?) -> Unit, modifier: Modifier = Modifier, rotulo: String = "Fecha del final (opcional)") {
    var abierto by rememberSaveable { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Ic(R.drawable.ic_reloj, C.texto2, tam = 18.dp)
        Text(
            if (fecha == null) rotulo else formatearFecha(fecha),
            Modifier.padding(start = 8.dp).weight(1f),
            color = if (fecha == null) C.texto2 else C.texto,
            fontFamily = C.fuente,
            fontSize = 15.sp,
        )
        TextButton(onClick = { abierto = true }) { Text(if (fecha == null) "Elegir" else "Cambiar", color = C.disponible, fontFamily = C.fuente) }
        if (fecha != null) TextButton(onClick = { onCambio(null) }) { Text("Quitar", color = C.texto2, fontFamily = C.fuente) }
    }
    if (abierto) {
        val hoy = LocalDate.now()
        val estado = rememberDatePickerState(
            initialSelectedDateMillis = (fecha ?: hoy).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            yearRange = 1990..hoy.year,
        )
        DatePickerDialog(
            onDismissRequest = { abierto = false },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { onCambio(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    abierto = false
                }) { Text("Listo", color = C.disponible, fontFamily = C.fuente) }
            },
            dismissButton = { TextButton(onClick = { abierto = false }) { Text("Cancelar", color = C.texto2, fontFamily = C.fuente) } },
            colors = DatePickerDefaults.colors(containerColor = C.tarjeta),
        ) {
            DatePicker(estado, colors = DatePickerDefaults.colors(containerColor = C.tarjeta))
        }
    }
}

/**
 * Bloque "Final" del detalle de una materia: nota y fecha si está aprobada, y los aplazos.
 * No aparece en la PPS (no se rinde).
 */
@Composable
fun BloqueFinal(materia: Materia, avance: Avance, modifier: Modifier = Modifier) {
    val notas = LocalNotas.current ?: return
    if (materia.tipo == Tipo.PPS) return
    val estado = avance.estado(materia.id)
    val aprobada = estado.aprobada
    val promocionada = estado == Estado.PROMOCIONADA
    val nf = notas.libreta.finales[materia.id]
    val aplazos = notas.libreta.aplazosDe(materia.id)
    Column(modifier.fillMaxWidth()) {
        if (aprobada) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(C.formas.chica)
                    .background(C.oro.copy(alpha = 0.10f))
                    .clickable(onClickLabel = "Editar nota") { notas.editarFinal(materia.id) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(C.oro.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text(nf?.nota?.toString() ?: "–", color = C.texto, fontFamily = C.fuenteDatos, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(
                        when {
                            promocionada && nf?.nota != null -> "Promocionada con ${nf.nota}"
                            promocionada -> "Promocionada: falta cargar la nota"
                            nf?.nota != null -> "Aprobada con ${nf.nota}"
                            else -> "Aprobada, sin nota cargada"
                        },
                        color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                    )
                    Text(formatearFecha(nf?.fecha), color = C.texto2, fontFamily = C.fuente, fontSize = 13.sp)
                }
                Text(if (nf == null) "Cargar" else "Editar", color = C.disponible, fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
        aplazos.forEach { a -> FilaAplazo(a, notas) }
        Text(
            "+ Agregar aplazo",
            Modifier
                .padding(top = 6.dp)
                .clip(C.formas.mini)
                .clickable(role = Role.Button) { notas.agregarAplazo(materia.id) }
                .padding(vertical = 8.dp, horizontal = 4.dp),
            color = C.disponible,
            fontFamily = C.fuente,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun FilaAplazo(a: Aplazo, notas: AccionesNotas) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(C.peligro.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Text("${a.nota}", color = C.texto, fontFamily = C.fuenteDatos, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text("Aplazo", color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(formatearFecha(a.fecha), color = C.texto2, fontFamily = C.fuente, fontSize = 13.sp)
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape)
                .semantics { contentDescription = "Borrar aplazo" }
                .clickable(role = Role.Button) { notas.borrarAplazo(a.id) },
            contentAlignment = Alignment.Center,
        ) { Text("✕", color = C.texto2, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
    }
}
