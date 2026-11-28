package ar.correlativas.mecanica.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import ar.correlativas.mecanica.data.Ajustes
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.Estilo
import ar.correlativas.mecanica.data.ModoTema
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Promocionar desde el selector de estado: la nota es obligatoria. Sin nota no se puede confirmar; cancelar
 * deja la materia como estaba; con nota queda promocionada y la nota guardada. Celular y tablet.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class PromocionUiTest(private val nombre: String, private val ancho: Int, private val alto: Int) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun pantallas() = listOf(
            arrayOf<Any>("celular", 411, 891),
            arrayOf<Any>("tablet", 1280, 800),
        )
    }

    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var vm: CorrelativasViewModel

    /**
     * Espera a que DataStore guarde: la escritura va en un hilo de IO y la corrutina del ViewModel
     * sigue en el hilo principal. waitForIdle() corre esas tareas pendientes del looper principal
     * (advanceTimeBy solo avanza el reloj de Compose y no las ejecuta).
     */
    private fun esperar(condicion: () -> Boolean) {
        repeat(200) {
            rule.waitForIdle()
            if (condicion()) return
            Thread.sleep(20)
        }
    }

    private fun mostrarFisicaIEnLaLista() {
        RuntimeEnvironment.setQualifiers("w${ancho}dp-h${alto}dp-${if (ancho > alto) "land" else "port"}")
        val app = ApplicationProvider.getApplicationContext<Application>()
        runBlocking { Ajustes(app).omitirNombre() }
        vm = CorrelativasViewModel(app)
        rule.setContent {
            TemaJuego(ModoTema.OSCURO, Estilo.PLANO) {
                AppCorrelativas(vm, WindowSizeClass.calculateFromSize(DpSize(ancho.dp, alto.dp)))
            }
        }
        esperar { vm.avance.value != null && vm.preferencias.value?.nombrePreguntado == true }
        rule.mainClock.advanceTimeBy(1000)
        rule.onAllNodesWithText("Lista").onLast().performClick()
        rule.mainClock.advanceTimeBy(800)
        rule.onAllNodes(hasScrollToIndexAction()).onLast().performScrollToNode(hasText("Física I"))
    }

    /**
     * El botón "Promocionada" del selector de la fila de Física I en la lista (llama al mismo
     * vm.cambiar que el selector del detalle). Se usa el de la lista porque en el celular el detalle
     * es una hoja modal, otra ventana, y en Robolectric el toque no le llega al selector de adentro.
     */
    private fun elegirPromocionada() {
        rule.onNode(hasContentDescription("Promocionada") and hasClickAction() and hasAnyAncestor(hasText("Física I")))
            .performClick()
        rule.mainClock.advanceTimeBy(500)
        // El diálogo es el de Física I, no el de otra materia.
        rule.onNodeWithText("Física I. Para promocionarla", substring = true).assertExists()
    }

    @Test fun sinNotaNoSePuedePromocionarYCancelarNoCambiaNada() {
        mostrarFisicaIEnLaLista()
        elegirPromocionada()
        rule.onNodeWithText("Nota de la promoción").assertExists()
        rule.onNodeWithText("Promocionar").assertIsNotEnabled()
        rule.onNodeWithText("Cancelar").performClick()
        rule.mainClock.advanceTimeBy(500)
        assertEquals("$nombre: el diálogo se cerró", 0, rule.onAllNodesWithText("Nota de la promoción").fetchSemanticsNodes().size)
        assertEquals(Estado.SIN_CURSAR, vm.avance.value!!.estado(4))
    }

    @Test fun conNotaQuedaPromocionada() {
        mostrarFisicaIEnLaLista()
        elegirPromocionada()
        rule.onNode(hasContentDescription("Nota 8") and hasClickAction()).performClick()
        rule.onNodeWithText("Promocionar").assertIsEnabled().performClick()
        esperar { vm.avance.value?.estado(4) == Estado.PROMOCIONADA && vm.libreta.value.finales[4]?.nota == 8 }
        assertEquals("$nombre: estado", Estado.PROMOCIONADA, vm.avance.value!!.estado(4))
        assertEquals("$nombre: nota", 8, vm.libreta.value.finales[4]?.nota)
    }
}
