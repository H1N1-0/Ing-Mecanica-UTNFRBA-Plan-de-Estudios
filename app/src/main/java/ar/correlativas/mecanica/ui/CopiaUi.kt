package ar.correlativas.mecanica.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.Backup
import ar.correlativas.mecanica.data.DatosBackup
import ar.correlativas.mecanica.data.LecturaBackup
import ar.correlativas.mecanica.data.Qr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Lo que necesita la sección de copia de seguridad (lo provee el ViewModel). */
class AccionesCopia(
    val datos: suspend () -> DatosBackup,
    val leerJson: (String) -> LecturaBackup,
    val leerQr: (String) -> LecturaBackup,
    val importar: (DatosBackup) -> Unit,
)

/**
 * Copia de seguridad en Ajustes: exportar como archivo JSON o QR, e importar desde un archivo,
 * la cámara o una imagen del QR. Todo local: el archivo lo elige el usuario (sin permisos de
 * almacenamiento) y la app no tiene permiso de internet. Antes de reemplazar, siempre confirma.
 */
@Composable
fun SeccionCopia(acciones: AccionesCopia) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mensaje by remember { mutableStateOf<String?>(null) }
    var qr by remember { mutableStateOf<Qr.Matriz?>(null) }
    var lectura by remember { mutableStateOf<LecturaBackup?>(null) }
    var escaneando by remember { mutableStateOf(false) }

    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            mensaje = runCatching {
                val json = Backup.aJson(acciones.datos())
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                }
                "Copia guardada."
            }.getOrElse { "No se pudo guardar el archivo." }
        }
    }
    val abrirArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val texto = withContext(Dispatchers.IO) { runCatching { leerTexto(context, uri) }.getOrNull() }
            lectura = if (texto == null) LecturaBackup.Error("No se pudo leer el archivo.") else acciones.leerJson(texto)
        }
    }
    val abrirImagen = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val texto = withContext(Dispatchers.Default) { runCatching { leerQrDeImagen(context, uri) }.getOrNull() }
            lectura = if (texto == null) LecturaBackup.Error("No se encontró un QR en esa imagen.") else acciones.leerQr(texto)
        }
    }
    val pedirCamara = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) escaneando = true else mensaje = "Sin permiso de cámara: podés importar el QR desde una imagen."
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Progreso, notas, test y ajustes. Queda todo en tu celular: la app no usa internet.",
            color = C.texto2, fontFamily = C.fuente, fontSize = 13.5.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonCopia("Exportar archivo", R.drawable.ic_exportar, Modifier.weight(1f)) {
                exportar.launch(Backup.nombreArchivo(LocalDate.now()))
            }
            BotonCopia("Mostrar QR", R.drawable.ic_qr, Modifier.weight(1f)) {
                scope.launch {
                    qr = withContext(Dispatchers.Default) { runCatching { Qr.generar(Backup.aQr(acciones.datos())) }.getOrNull() }
                    if (qr == null) mensaje = "No se pudo generar el QR."
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonCopia("Importar archivo", R.drawable.ic_importar, Modifier.weight(1f)) {
                abrirArchivo.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            }
            BotonCopia("Escanear QR", R.drawable.ic_qr, Modifier.weight(1f)) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) escaneando = true
                else pedirCamara.launch(Manifest.permission.CAMERA)
            }
        }
        Text(
            "Importar QR desde una imagen",
            Modifier.clip(C.formas.mini).clickable(role = Role.Button) { abrirImagen.launch("image/*") }.padding(vertical = 6.dp, horizontal = 2.dp),
            color = C.disponible, fontFamily = C.fuente, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        )
        mensaje?.let { Text(it, color = C.texto, fontFamily = C.fuente, fontSize = 14.sp) }
    }

    qr?.let { DialogoQr(it, onCerrar = { qr = null }) }
    if (escaneando) {
        EscanerQr(
            onLeido = { texto -> escaneando = false; lectura = acciones.leerQr(texto) },
            onCerrar = { escaneando = false },
        )
    }
    when (val l = lectura) {
        null -> Unit
        is LecturaBackup.Error -> AlertDialog(
            onDismissRequest = { lectura = null },
            containerColor = C.tarjeta,
            title = { Text("No se pudo importar", color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.Bold) },
            text = { Text(l.motivo, color = C.texto2, fontFamily = C.fuente) },
            confirmButton = { TextButton(onClick = { lectura = null }) { Text("Entendido", color = C.disponible, fontFamily = C.fuente) } },
        )
        is LecturaBackup.Ok -> ConfirmarImportacion(
            l,
            onReemplazar = { acciones.importar(l.datos); lectura = null; mensaje = "Copia importada." },
            onCancelar = { lectura = null },
        )
    }
}

@Composable
private fun BotonCopia(texto: String, icono: Int, modifier: Modifier, onClick: () -> Unit) {
    Tarjeta(modifier.clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(icono, C.disponible, tam = 18.dp)
            Text(texto, Modifier.padding(start = 8.dp), color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

/** Resumen de la copia y confirmación: importar reemplaza todo lo que hay ahora. */
@Composable
internal fun ConfirmarImportacion(l: LecturaBackup.Ok, onReemplazar: () -> Unit, onCancelar: () -> Unit) {
    val d = l.datos
    AlertDialog(
        onDismissRequest = onCancelar,
        containerColor = C.tarjeta,
        shape = C.formas.grande,
        title = { Text("¿Reemplazar tus datos?", color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (d.exportado != LocalDate.MIN) Text("Copia del ${formatearFecha(d.exportado)}:", color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.SemiBold)
                Text(
                    "• ${d.aprobadas} aprobadas" + (if (d.promocionadas > 0) " (${d.promocionadas} por promoción)" else "") +
                        ", ${d.regularizadas} regularizadas" + (if (d.cursando > 0) " y ${d.cursando} cursando" else ""),
                    color = C.texto, fontFamily = C.fuente,
                )
                Text("• ${d.notas} notas y ${d.libreta.aplazos.size} aplazos", color = C.texto, fontFamily = C.fuente)
                Text("• Test vocacional: ${if (d.perfil != null) "hecho" else "sin hacer"}", color = C.texto, fontFamily = C.fuente)
                Text("• Estilo ${d.estilo.texto}, tema ${d.tema.texto.lowercase()}", color = C.texto, fontFamily = C.fuente)
                l.avisos.forEach { Text("⚠ $it", color = C.regular, fontFamily = C.fuente, fontSize = 13.sp) }
                Text(
                    "Se borra lo que tenés ahora en este celular y queda lo de la copia. No se puede deshacer.",
                    Modifier.padding(top = 8.dp), color = C.peligro, fontFamily = C.fuente, fontSize = 13.5.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onReemplazar) { Text("Reemplazar", color = C.peligro, fontFamily = C.fuente, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Cancelar", color = C.texto2, fontFamily = C.fuente) } },
    )
}

/** El QR de la copia, siempre negro sobre blanco (en cualquier estilo) para que se lea bien. */
@Composable
internal fun DialogoQr(m: Qr.Matriz, onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = C.tarjeta,
        shape = C.formas.grande,
        title = { Text("Copia en QR", color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Color.White).padding(12.dp)) {
                    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = "Código QR con tu copia de seguridad" }) {
                        val celda = size.minDimension / m.lado
                        for (y in 0 until m.lado) for (x in 0 until m.lado) {
                            if (m[x, y]) drawRect(Color.Black, Offset(x * celda, y * celda), Size(celda + 0.5f, celda + 0.5f))
                        }
                    }
                }
                Text(
                    "En el otro celular: Ajustes → Escanear QR. También podés sacarle una captura y usar «Importar QR desde una imagen».",
                    Modifier.padding(top = 10.dp), color = C.texto2, fontFamily = C.fuente, fontSize = 13.5.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Listo", color = C.disponible, fontFamily = C.fuente) } },
    )
}

private fun leerTexto(context: Context, uri: Uri): String =
    context.contentResolver.openInputStream(uri)!!.use { entrada ->
        // Una copia pesa pocos KB: se corta a 1 MB por si eligen otro archivo.
        String(entrada.readNBytesCompat(1_000_000), Charsets.UTF_8)
    }

private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
    val salida = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (total < max) {
        val n = read(buffer, 0, minOf(buffer.size, max - total))
        if (n < 0) break
        salida.write(buffer, 0, n)
        total += n
    }
    return salida.toByteArray()
}

/** Lee el QR de una imagen (por ejemplo una captura), achicándola si es muy grande. */
private fun leerQrDeImagen(context: Context, uri: Uri): String? {
    val opciones = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opciones) }
    var muestra = 1
    while (maxOf(opciones.outWidth, opciones.outHeight) / muestra > 2000) muestra *= 2
    val bitmap = context.contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra })
    } ?: return null
    val pixeles = IntArray(bitmap.width * bitmap.height)
    bitmap.getPixels(pixeles, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    return Qr.leerPixeles(bitmap.width, bitmap.height, pixeles)
}
