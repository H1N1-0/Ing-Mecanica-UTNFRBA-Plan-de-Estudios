package ar.correlativas.mecanica.ui

import android.app.Application
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import ar.correlativas.mecanica.data.Ajustes
import ar.correlativas.mecanica.data.Estilo
import ar.correlativas.mecanica.data.ModoTema
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Ajustes tiene que abrirse con UN solo botón y mostrar exactamente las mismas opciones en
 * celular y tablet, en vertical y horizontal (en particular, la copia de seguridad).
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class AjustesNavegacionTest(
    private val nombre: String,
    private val ancho: Int,
    private val alto: Int,
    private val escalaLetra: Float,
) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun pantallas() = listOf(
            arrayOf<Any>("celular vertical", 411, 891, 1f),
            arrayOf<Any>("celular horizontal", 891, 411, 1f),
            arrayOf<Any>("celular chico con letra grande", 360, 640, 1.3f),
            arrayOf<Any>("tablet vertical", 800, 1280, 1f),
            arrayOf<Any>("tablet horizontal", 1280, 800, 1f),
        )

        /** Todo lo que tiene que estar en Ajustes, en el mismo orden, en cualquier pantalla. */
        val OPCIONES = listOf(
            "ESTILO", "Plano técnico", "Gamificado", "Material You",
            "TEMA", "Sistema", "Claro", "Oscuro",
            "RÓTULO DE LA LÁMINA", "Tu nombre",
            "TU PERFIL",
            "TUS NOTAS", "Estadísticas",
            "COPIA DE SEGURIDAD", "Exportar archivo", "Mostrar QR", "Importar archivo", "Escanear QR",
            "Importar QR desde una imagen",
            "DATOS", "Reiniciar mi progreso",
            "ACERCA DE", "Versión",
        )
    }

    @get:Rule val rule = createComposeRule()

    private fun abrirApp(): CorrelativasViewModel {
        RuntimeEnvironment.setQualifiers("w${ancho}dp-h${alto}dp-${if (ancho > alto) "land" else "port"}")
        RuntimeEnvironment.setFontScale(escalaLetra)
        val app = ApplicationProvider.getApplicationContext<Application>()
        // Sin la pregunta del nombre del primer uso, para ir directo a lo que se prueba.
        runBlocking { Ajustes(app).omitirNombre() }
        val vm = CorrelativasViewModel(app)
        rule.setContent {
            TemaJuego(ModoTema.OSCURO, Estilo.PLANO) {
                AppCorrelativas(vm, WindowSizeClass.calculateFromSize(DpSize(ancho.dp, alto.dp)))
            }
        }
        // DataStore carga en otro hilo: se avanza el reloj hasta que la app muestra el inicio.
        repeat(200) {
            if (vm.avance.value != null && vm.preferencias.value?.nombrePreguntado == true) return@repeat
            Thread.sleep(20)
            rule.mainClock.advanceTimeBy(50)
        }
        rule.mainClock.advanceTimeBy(1000)
        return vm
    }

    private fun abrirAjustes() {
        rule.onNode(hasContentDescription("Ajustes") and hasClickAction()).performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.onAllNodesWithText("Ajustes").onFirst().assertIsDisplayed()
    }

    /** Ancho compacto (< 600 dp): celular en vertical. Si no, barra lateral. */
    private val conBarraLateral get() = ancho >= 600

    @Test fun hayUnSoloBotonDeAjustesYFunciona() {
        abrirApp()
        // Un solo botón para abrir Ajustes en toda la pantalla (antes, en tablet había dos).
        rule.onAllNodes(hasContentDescription("Ajustes")).assertCountEquals(1)
        val boton = rule.onNode(hasContentDescription("Ajustes") and hasClickAction()).fetchSemanticsNode()
        val densidad = boton.layoutInfo.density.density
        val izquierda = boton.boundsInRoot.left / densidad
        if (conBarraLateral) {
            // En tablet y horizontal, el botón está arriba de la barra lateral (80 dp de ancho).
            assertTrue("En $nombre el botón de Ajustes tendría que estar en la barra lateral (x = $izquierda dp)", izquierda < 80f)
        } else {
            // En el celular, arriba a la derecha del inicio.
            assertTrue("En $nombre el botón de Ajustes tendría que estar a la derecha (x = $izquierda dp)", izquierda > ancho / 2f)
        }
        abrirAjustes()
        rule.onNodeWithText("ESTILO").assertIsDisplayed()
    }

    @Test fun ajustesMuestraTodasLasOpciones() {
        abrirApp()
        abrirAjustes()
        val faltan = OPCIONES.filterNot { texto ->
            runCatching {
                rule.onAllNodesWithText(texto, substring = texto == "Versión").onFirst().performScrollTo().assertIsDisplayed()
            }.isSuccess
        }
        assertTrue("En $nombre faltan en Ajustes: $faltan", faltan.isEmpty())
    }

    @Test fun lasOpcionesDeCopiaSeVenYSonTocables() {
        abrirApp()
        abrirAjustes()
        listOf("Exportar archivo", "Mostrar QR", "Importar archivo", "Escanear QR", "Importar QR desde una imagen").forEach { texto ->
            rule.onNode(androidx.compose.ui.test.hasText(texto) and hasClickAction(), useUnmergedTree = false)
                .performScrollTo()
                .assertIsDisplayed()
        }
    }
}
