package ar.correlativas.mecanica.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Canvas
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.Estilo
import ar.correlativas.mecanica.data.HistoriaSiu
import ar.correlativas.mecanica.data.ImportacionSiu
import ar.correlativas.mecanica.data.LecturaHistoria
import ar.correlativas.mecanica.data.Libreta
import ar.correlativas.mecanica.data.ModoTema
import ar.correlativas.mecanica.data.PdfDePrueba
import ar.correlativas.mecanica.data.Plan
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** TEMPORAL (se revierte antes del merge): capturas de la vista previa para el PR, con el PDF inventado. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class CapturasCiTest(private val nombre: String, private val ancho: Int, private val alto: Int, private val estilo: Estilo, private val modo: ModoTema) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun pantallas() = listOf(
            arrayOf<Any>("celular", 411, 891, Estilo.PLANO, ModoTema.OSCURO),
            arrayOf<Any>("tablet", 1280, 800, Estilo.PLANO, ModoTema.OSCURO),
            arrayOf<Any>("celular-gamificado-claro", 411, 891, Estilo.GAMIFICADO, ModoTema.CLARO),
        )
    }

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun vistaPrevia() {
        RuntimeEnvironment.setQualifiers("w${ancho}dp-h${alto}dp-${if (ancho > alto) "land" else "port"}-mdpi")
        val plan = Plan.parse(listOf(File("../correlativas.json"), File("correlativas.json")).first { it.exists() }.readText())
        val filas = (HistoriaSiu.leer(PdfDePrueba.historia()) as LecturaHistoria.Ok).filas
        val imp = ImportacionSiu.armar(filas, Avance(plan, mapOf(2 to Estado.REGULARIZADA, 9 to Estado.APROBADA)), Libreta())
        rule.setContent { TemaJuego(modo, estilo) { ContenidoVistaPreviaSiu(imp, onAplicar = {}, onCancelar = {}) } }
        rule.mainClock.advanceTimeBy(800)
        rule.waitForIdle()
        // captureToImage espera un dibujado de la ventana que en Robolectric no llega: se dibuja la vista a mano.
        val vista = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(vista.width, vista.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { vista.draw(Canvas(bmp)) }
        val dir = File("build/capturas").apply { mkdirs() }
        File(dir, "vista-previa-$nombre.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
