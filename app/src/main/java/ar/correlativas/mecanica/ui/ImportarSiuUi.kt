package ar.correlativas.mecanica.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.CambioSiu
import ar.correlativas.mecanica.data.DatosBackup
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.ImportacionSiu
import ar.correlativas.mecanica.data.LecturaBackup
import ar.correlativas.mecanica.data.NoReconocida
import ar.correlativas.mecanica.data.Tipo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lo que necesita la importación de la historia del SIU (lo provee el ViewModel). */
class AccionesSiu(
    /** Vista previa lista para mostrar, o null. */
    val importacion: ImportacionSiu?,
    /** Lee el PDF y arma la vista previa; devuelve el motivo si no se pudo. */
    val preparar: suspend (ByteArray) -> String?,
    val cancelar: () -> Unit,
    val aplicar: (List<CambioSiu>) -> Unit,
    val mensaje: String?,
    val limpiarMensaje: () -> Unit,
    /** Hay una copia automática de antes de la última importación. */
    val hayCopia: Boolean,
    val leerCopia: suspend () -> LecturaBackup,
    val restaurar: (DatosBackup) -> Unit,
)

/** PDFs de más de esto no son una historia académica (la del SIU pesa unos pocos KB por página). */
private const val TAMANIO_MAXIMO = 20 * 1024 * 1024

/**
 * Sección de Ajustes: importar la historia académica del SIU Guaraní desde el PDF. El archivo se
 * elige con el selector del sistema (sin permisos de almacenamiento) y se lee en el celular.
 */
@Composable
fun SeccionSiu(acciones: AccionesSiu) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var leyendo by remember { mutableStateOf(false) }
    var restaurar by remember { mutableStateOf<LecturaBackup?>(null) }

    val abrirPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        acciones.limpiarMensaje()
        leyendo = true
        scope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { leerBytes(context, uri) }.getOrNull() }
            error = if (bytes == null) "No se pudo leer el archivo." else acciones.preparar(bytes)
            leyendo = false
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Cargá tu avance desde el PDF de la historia académica del SIU Guaraní. Antes de cambiar nada te muestra qué va a hacer.",
            color = C.texto2, fontFamily = C.fuente, fontSize = 13.5.sp,
        )
        Tarjeta(Modifier.fillMaxWidth().clickable(role = Role.Button, enabled = !leyendo) { abrirPdf.launch(arrayOf("application/pdf")) }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Ic(R.drawable.ic_importar, C.disponible, tam = 18.dp)
                Text(
                    if (leyendo) "Leyendo el PDF…" else "Importar PDF del SIU",
                    Modifier.padding(start = 8.dp),
                    color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                )
            }
        }
        if (acciones.hayCopia) {
            Text(
                "Deshacer la última importación",
                Modifier
                    .clip(C.formas.mini)
                    .clickable(role = Role.Button) { scope.launch { restaurar = acciones.leerCopia() } }
                    .padding(vertical = 6.dp, horizontal = 2.dp),
                color = C.disponible, fontFamily = C.fuente, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
            )
        }
        acciones.mensaje?.let { Text(it, color = C.texto, fontFamily = C.fuente, fontSize = 14.sp) }
    }

    error?.let { motivo ->
        AlertDialog(
            onDismissRequest = { error = null },
            containerColor = C.tarjeta,
            title = { Text("No se pudo importar", color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.Bold) },
            text = { Text("$motivo\n\nNo se cambió nada.", color = C.texto2, fontFamily = C.fuente) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("Entendido", color = C.disponible, fontFamily = C.fuente) } },
        )
    }
    acciones.importacion?.let { imp ->
        VistaPreviaSiu(imp, onAplicar = acciones.aplicar, onCancelar = acciones.cancelar)
    }
    when (val l = restaurar) {
        null -> Unit
        is LecturaBackup.Error -> AlertDialog(
            onDismissRequest = { restaurar = null },
            containerColor = C.tarjeta,
            title = { Text("No se pudo deshacer", color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.Bold) },
            text = { Text(l.motivo, color = C.texto2, fontFamily = C.fuente) },
            confirmButton = { TextButton(onClick = { restaurar = null }) { Text("Entendido", color = C.disponible, fontFamily = C.fuente) } },
        )
        is LecturaBackup.Ok -> ConfirmarImportacion(
            l,
            onReemplazar = { acciones.restaurar(l.datos); restaurar = null },
            onCancelar = { restaurar = null },
        )
    }
}

private fun leerBytes(context: Context, uri: Uri): ByteArray {
    context.contentResolver.openInputStream(uri)!!.use { entrada ->
        val salida = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = entrada.read(buffer)
            if (n < 0) break
            salida.write(buffer, 0, n)
            require(salida.size() <= TAMANIO_MAXIMO) { "archivo demasiado grande" }
        }
        return salida.toByteArray()
    }
}

/** Nombre y color de un estado en la vista previa. */
private fun nombreEstado(e: Estado, tipo: Tipo): String = when (e) {
    Estado.SIN_CURSAR -> if (tipo == Tipo.PPS) "Pendiente" else "Sin cursar"
    Estado.CURSANDO -> "Cursando"
    Estado.REGULARIZADA -> "Regularizada"
    Estado.APROBADA -> if (tipo == Tipo.PPS) "Acreditada" else "Aprobada"
    Estado.PROMOCIONADA -> "Promocionada"
}

@Composable
private fun colorEstado(e: Estado): Color = when (e) {
    Estado.SIN_CURSAR -> C.texto2
    Estado.CURSANDO -> C.violeta
    Estado.REGULARIZADA -> C.regular
    Estado.APROBADA, Estado.PROMOCIONADA -> C.oro
}

private fun iconoEstado(e: Estado): Int = when (e) {
    Estado.SIN_CURSAR -> R.drawable.ic_reloj
    Estado.CURSANDO -> R.drawable.ic_libro
    Estado.REGULARIZADA -> R.drawable.ic_mitad
    Estado.APROBADA -> R.drawable.ic_estrella
    Estado.PROMOCIONADA -> R.drawable.ic_promocion
}

/**
 * Vista previa: cada materia con su estado actual → nuevo y la nota. Las que bajarían de estado
 * van destacadas y desmarcadas; las filas que no se reconocieron, aparte. Pantalla completa en el
 * celular y una columna centrada en tablet. "Atrás" cancela (es un diálogo).
 */
@Composable
fun VistaPreviaSiu(imp: ImportacionSiu, onAplicar: (List<CambioSiu>) -> Unit, onCancelar: () -> Unit) {
    Dialog(onDismissRequest = onCancelar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ContenidoVistaPreviaSiu(imp, onAplicar, onCancelar)
    }
}

/** El contenido de [VistaPreviaSiu], sin el diálogo (también sirve para sacar capturas). */
@Composable
fun ContenidoVistaPreviaSiu(imp: ImportacionSiu, onAplicar: (List<CambioSiu>) -> Unit, onCancelar: () -> Unit) {
    val conCambios = imp.conCambios
    // Elegidas por id de materia; IntArray para que se guarde al rotar.
    var elegidas by rememberSaveable(imp) { mutableStateOf(conCambios.filter { it.marcadoPorDefecto }.map { it.materia.id }.toIntArray()) }
    val bajas = conCambios.count { it.baja }

    Box(Modifier.fillMaxSize().background(C.fondo), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 720.dp).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Ic(R.drawable.ic_importar, C.disponible, tam = 24.dp)
                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                    Text(
                        "Historia académica del SIU",
                        Modifier.semantics { heading() },
                        color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    )
                    Text(
                        "${imp.cambios.size} materias leídas · ${conCambios.size} con cambios" +
                            if (imp.noReconocidas.isNotEmpty()) " · ${imp.noReconocidas.size} sin reconocer" else "",
                        color = C.texto2, fontFamily = C.fuente, fontSize = 13.5.sp,
                    )
                }
            }
            HorizontalDivider(color = C.borde)
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (bajas > 0) {
                    item(key = "aviso-bajas", contentType = "aviso") {
                        Text(
                            if (bajas == 1) "1 cambio bajaría el estado que tenés ahora: está destacado y desmarcado."
                            else "$bajas cambios bajarían el estado que tenés ahora: están destacados y desmarcados.",
                            Modifier.fillMaxWidth().clip(C.formas.chica).background(C.peligro.copy(alpha = 0.10f)).padding(10.dp),
                            color = C.texto, fontFamily = C.fuente, fontSize = 13.5.sp,
                        )
                    }
                }
                if (conCambios.isEmpty()) {
                    item(key = "nada", contentType = "aviso") {
                        Text("Todo lo del PDF ya está cargado en la app: no hay nada para cambiar.", Modifier.padding(6.dp), color = C.texto2, fontFamily = C.fuente)
                    }
                }
                items(conCambios, key = { it.materia.id }, contentType = { "cambio" }) { c ->
                    val elegida = c.materia.id in elegidas
                    FilaCambio(c, elegida) { si ->
                        elegidas = if (si) elegidas + c.materia.id else elegidas.filter { it != c.materia.id }.toIntArray()
                    }
                }
                if (imp.sinCambios.isNotEmpty()) {
                    item(key = "h-sin", contentType = "titulo") { TituloSeccion("SIN CAMBIOS · ${imp.sinCambios.size}") }
                    items(imp.sinCambios, key = { "s-${it.materia.id}" }, contentType = { "sin" }) { c ->
                        Text(
                            "${c.materia.nombre} · ${nombreEstado(c.actual, c.materia.tipo)}",
                            Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = C.texto2, fontFamily = C.fuente, fontSize = 13.5.sp,
                        )
                    }
                }
                if (imp.noReconocidas.isNotEmpty()) {
                    item(key = "h-no", contentType = "titulo") { TituloSeccion("NO RECONOCIDAS · ${imp.noReconocidas.size}") }
                    items(imp.noReconocidas.withIndex().toList(), key = { "n-${it.index}" }, contentType = { "no" }) { (_, n) -> FilaNoReconocida(n) }
                }
            }
            HorizontalDivider(color = C.borde)
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                Text(
                    "Antes de aplicar se guarda una copia automática: podés deshacerlo desde Ajustes.",
                    color = C.texto2, fontFamily = C.fuente, fontSize = 12.5.sp,
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onCancelar) { Text("Cancelar", color = C.texto2, fontFamily = C.fuente, fontSize = 15.sp) }
                    val n = elegidas.size
                    TextButton(
                        onClick = { onAplicar(conCambios.filter { it.materia.id in elegidas }) },
                        enabled = n > 0,
                    ) {
                        Text(
                            if (n == 0) "Aplicar" else "Aplicar ($n)",
                            color = if (n > 0) C.disponible else C.apagado,
                            fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TituloSeccion(texto: String) {
    Text(
        texto,
        Modifier.padding(start = 6.dp, top = 14.dp, bottom = 2.dp).semantics { heading() },
        color = C.texto2, fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaCambio(c: CambioSiu, elegida: Boolean, onElegir: (Boolean) -> Unit) {
    val forma = C.formas.chica
    Row(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(if (c.baja) C.peligro.copy(alpha = 0.08f) else C.tarjeta)
            .border(1.dp, if (c.baja) C.peligro.copy(alpha = 0.55f) else C.borde.copy(alpha = 0.6f), forma)
            .toggleable(value = elegida, role = Role.Checkbox, onValueChange = onElegir)
            .padding(start = 2.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = elegida,
            onCheckedChange = null,
            modifier = Modifier.padding(12.dp),
            colors = CheckboxDefaults.colors(checkedColor = C.disponible, uncheckedColor = C.apagado, checkmarkColor = C.fondo),
        )
        Column(Modifier.weight(1f)) {
            Text(c.materia.nombre, color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp)
            FlowRow(
                Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(c.materia.abrev, Modifier.align(Alignment.CenterVertically), color = C.texto2, fontFamily = C.fuenteDatos, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                if (c.cambiaEstado) {
                    Pildora(nombreEstado(c.actual, c.materia.tipo), colorEstado(c.actual), icono = iconoEstado(c.actual))
                    Text("→", Modifier.align(Alignment.CenterVertically), color = C.texto2, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                Pildora(nombreEstado(c.nuevo, c.materia.tipo), colorEstado(c.nuevo), icono = iconoEstado(c.nuevo))
            }
            val detalles = buildList {
                if (c.nota != null) add(if (c.guardaNota) "Nota ${c.nota}" else "Nota de cursada ${c.nota} (no se guarda)")
                if (c.nuevo == Estado.PROMOCIONADA && c.nota == null) add("Sin nota: cargala después desde el detalle")
                if (c.aplazos.isNotEmpty()) add(
                    if (c.aplazos.size == 1) "1 aplazo (${c.aplazos[0].nota})" else "${c.aplazos.size} aplazos (${c.aplazos.joinToString { it.nota.toString() }})",
                )
                c.fecha?.let { add(formatearFecha(it)) }
            }
            if (detalles.isNotEmpty()) {
                Text(detalles.joinToString(" · "), Modifier.padding(top = 4.dp), color = C.texto2, fontFamily = C.fuente, fontSize = 13.sp)
            }
            if (c.baja) {
                Text(
                    "Bajaría el estado: marcalo solo si el PDF tiene razón.",
                    Modifier.padding(top = 4.dp),
                    color = C.peligro, fontFamily = C.fuente, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun FilaNoReconocida(n: NoReconocida) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(C.formas.chica)
            .background(C.tarjeta)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(n.fila.actividad, color = C.texto, fontFamily = C.fuente, fontSize = 14.sp)
        Text(
            listOfNotNull(n.motivo, n.fila.tipo.takeIf { it.isNotBlank() }, n.fila.resultado.takeIf { it.isNotBlank() }).joinToString(" · "),
            color = C.texto2, fontFamily = C.fuente, fontSize = 12.5.sp,
        )
    }
}
