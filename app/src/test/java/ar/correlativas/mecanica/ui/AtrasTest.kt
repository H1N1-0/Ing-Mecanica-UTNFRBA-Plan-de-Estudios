package ar.correlativas.mecanica.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onLast
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import ar.correlativas.mecanica.data.Ajustes
import ar.correlativas.mecanica.data.Estilo
import ar.correlativas.mecanica.data.ModoTema
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Botón/gesto "atrás": cierra lo que esté abierto (detalle, diálogo, pantalla superpuesta), si no
 * vuelve a Inicio, y solo en Inicio deja que el sistema cierre la app. Igual en celular y tablet.
 *
 * Con el atrás predictivo de Android 14+, el sistema muestra la animación de "volver al inicio
 * del celular" solo si la app no tiene un manejador activo: por eso también se verifica
 * `hasEnabledCallbacks()` (activo en cualquier lado menos en Inicio sin nada abierto).
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class AtrasTest(private val nombre: String, private val ancho: Int, private val alto: Int) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun pantallas() = listOf(
            arrayOf<Any>("celular", 411, 891),
            arrayOf<Any>("tablet", 1280, 800),
        )
    }

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val actividad get() = rule.activity
    private val conPanel get() = ancho >= 600

    private fun abrirApp() {
        RuntimeEnvironment.setQualifiers("w${ancho}dp-h${alto}dp-${if (ancho > alto) "land" else "port"}")
        val app = ApplicationProvider.getApplicationContext<Application>()
        runBlocking { Ajustes(app).omitirNombre() }
        val vm = CorrelativasViewModel(app)
        rule.setContent {
            TemaJuego(ModoTema.OSCURO, Estilo.PLANO) {
                AppCorrelativas(vm, WindowSizeClass.calculateFromSize(DpSize(ancho.dp, alto.dp)))
            }
        }
        repeat(200) {
            if (vm.avance.value != null && vm.preferencias.value?.nombrePreguntado == true) return@repeat
            Thread.sleep(20)
            rule.mainClock.advanceTimeBy(50)
        }
        rule.mainClock.advanceTimeBy(1000)
        enInicio()
    }

    /** "Atrás" como lo manda el sistema: a la ventana con foco (diálogo, hoja o la actividad). */
    private fun atras() {
        Espresso.pressBackUnconditionally()
        rule.mainClock.advanceTimeBy(1000)
    }

    /** En Inicio: la pestaña Inicio elegida (el inicio puede estar scrolleado) y sin pantallas encima. */
    private fun enInicio() {
        rule.onNode(hasText("Inicio") and isSelected()).assertExists()
        assertTrue(rule.onAllNodesWithText("ESTILO").fetchSemanticsNodes().isEmpty())
        assertTrue(rule.onAllNodesWithText("Promedio acumulado por año").fetchSemanticsNodes().isEmpty())
        assertFalse("$nombre: la app no se tiene que cerrar", actividad.isFinishing)
    }

    private fun irA(pestania: String) {
        rule.onAllNodesWithText(pestania).onLast().performClick()
        rule.mainClock.advanceTimeBy(800)
    }

    @Test fun enInicioAtrasCierraLaApp() {
        abrirApp()
        // Sin nada abierto, la app no intercepta "atrás": el sistema la cierra (y en Android 14+
        // muestra la animación predictiva de volver al inicio del celular).
        assertFalse(actividad.onBackPressedDispatcher.hasEnabledCallbacks())
        atras()
        assertTrue("$nombre: en Inicio, atrás cierra la app", actividad.isFinishing)
    }

    @Test fun desdeCadaPestaniaVuelveAInicio() {
        abrirApp()
        listOf("Mapa", "Lista", "Logros").forEach { p ->
            irA(p)
            assertTrue("$nombre: en $p la app maneja atrás", actividad.onBackPressedDispatcher.hasEnabledCallbacks())
            atras()
            enInicio()
        }
    }

    @Test fun desdeAjustesVuelveAInicio() {
        abrirApp()
        rule.onNode(hasContentDescription("Ajustes") and hasClickAction()).performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("ESTILO").assertIsDisplayed()
        atras()
        enInicio()
        atras()
        assertTrue(actividad.isFinishing)
    }

    @Test fun desdeAjustesAbiertoEnOtraPestaniaVuelvePasoAPaso() {
        abrirApp()
        irA("Mapa")
        if (conPanel) {
            // En tablet, Ajustes se abre desde la barra lateral estando en cualquier pestaña.
            rule.onNode(hasContentDescription("Ajustes") and hasClickAction()).performClick()
            rule.mainClock.advanceTimeBy(1000)
            atras()
            rule.onNodeWithText("Mapa de la carrera").assertIsDisplayed()
        }
        atras()
        enInicio()
    }

    @Test fun conElDetalleAbiertoPrimeroLoCierra() {
        abrirApp()
        irA("Lista")
        rule.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToNode(hasText("Física I"))
        rule.onAllNodesWithText("Física I").onFirst().performClick()
        rule.mainClock.advanceTimeBy(1000)
        // El detalle muestra "Para cursar".
        rule.onAllNodesWithText("Para cursar").onFirst().assertExists()
        atras()
        // Se cerró el detalle, pero seguimos en la lista.
        rule.onAllNodesWithText("Para cursar").fetchSemanticsNodes().let { assertTrue("$nombre: el detalle sigue abierto", it.isEmpty()) }
        rule.onNodeWithText("Buscar materia o número").assertIsDisplayed()
        assertFalse(actividad.isFinishing)
        atras()
        enInicio()
    }

    @Test fun conUnDialogoAbiertoPrimeroLoCierra() {
        abrirApp()
        rule.onNode(hasContentDescription("Ajustes") and hasClickAction()).performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("Reiniciar mi progreso").performScrollTo().performClick()
        rule.mainClock.advanceTimeBy(500)
        rule.onNodeWithText("¿Reiniciar la partida?").assertExists()
        atras()
        // Se cerró el diálogo y seguimos en Ajustes.
        assertTrue(rule.onAllNodesWithText("¿Reiniciar la partida?").fetchSemanticsNodes().isEmpty())
        rule.onNodeWithText("Reiniciar mi progreso").assertExists()
        assertFalse(actividad.isFinishing)
    }

    @Test fun desdeEstadisticasVuelveAInicio() {
        abrirApp()
        rule.onAllNodes(hasScrollToIndexAction()).onFirst().performScrollToNode(hasText("Promedio"))
        rule.onAllNodesWithText("Promedio").onFirst().performClick()
        rule.mainClock.advanceTimeBy(1000)
        rule.onNodeWithText("Estadísticas").assertIsDisplayed()
        atras()
        enInicio()
    }
}
