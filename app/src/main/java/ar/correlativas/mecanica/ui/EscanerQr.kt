package ar.correlativas.mecanica.ui

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import ar.correlativas.mecanica.data.Qr
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lector de QR con la cámara (CameraX + ZXing). Todo pasa en el celular: no se manda ninguna
 * imagen a ningún lado. Llama a [onLeido] una sola vez con el texto del primer QR que encuentra.
 */
@Composable
fun EscanerQr(onLeido: (String) -> Unit, onCerrar: () -> Unit) {
    val context = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val leido = rememberUpdatedState(onLeido)
    val vista = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val hilo = remember { Executors.newSingleThreadExecutor() }
    val listo = remember { AtomicBoolean(false) }

    DisposableEffect(ciclo) {
        val futuro = ProcessCameraProvider.getInstance(context)
        var proveedor: ProcessCameraProvider? = null
        futuro.addListener({
            val p = futuro.get()
            proveedor = p
            val preview = Preview.Builder().build().also { it.surfaceProvider = vista.surfaceProvider }
            val analisis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analisis.setAnalyzer(hilo) { imagen ->
                imagen.use {
                    if (listo.get()) return@use
                    // Plano Y (luminancia) del cuadro: alcanza para leer el QR.
                    val plano = it.planes[0]
                    val buffer = plano.buffer
                    val bytes = ByteArray(buffer.remaining()).also { b -> buffer.get(b) }
                    val texto = Qr.leerLuminancia(bytes, it.width, it.height, plano.rowStride)
                    if (texto != null && listo.compareAndSet(false, true)) {
                        ContextCompat.getMainExecutor(context).execute { leido.value(texto) }
                    }
                }
            }
            runCatching {
                p.unbindAll()
                p.bindToLifecycle(ciclo, CameraSelector.DEFAULT_BACK_CAMERA, preview, analisis)
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            proveedor?.unbindAll()
            hilo.shutdown()
        }
    }

    Dialog(onDismissRequest = onCerrar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView({ vista }, Modifier.fillMaxSize())
            // Marco guía.
            Box(Modifier.align(Alignment.Center).size(260.dp).border(3.dp, C.disponible, C.formas.media))
            Column(Modifier.align(Alignment.BottomCenter).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Apuntá al QR de la copia (Ajustes → Mostrar QR en el otro celular).",
                    color = Color.White, fontFamily = C.fuente, fontSize = 15.sp,
                )
                TextButton(onClick = onCerrar) { Text("Cancelar", color = Color.White, fontFamily = C.fuente, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
