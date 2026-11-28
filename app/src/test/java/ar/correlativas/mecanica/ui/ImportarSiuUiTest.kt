package ar.correlativas.mecanica.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.CambioSiu
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.Estilo
import ar.correlativas.mecanica.data.HistoriaSiu
import ar.correlativas.mecanica.data.ImportacionSiu
import ar.correlativas.mecanica.data.LecturaHistoria
import ar.correlativas.mecanica.data.Libreta
import ar.correlativas.mecanica.data.ModoTema
import ar.correlativas.mecanica.data.PdfDePrueba
import ar.correlativas.mecanica.data.Plan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Vista previa de la importación del SIU (con el PDF inventado de PdfDePrueba), en celular y tablet. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class ImportarSiuUiTest(private val nombre: String, private val ancho: Int, private val alto: Int) {
    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun pantallas() = listOf(
            arrayOf<Any>("celular", 411, 891),
            arrayOf<Any>("tablet", 1280, 800),
        )
    }

    @get:Rule val rule = createComposeRule()

    private val plan = Plan.parse(listOf(File("../correlativas.json"), File("correlativas.json")).first { it.exists() }.readText())

    private var aplicados: List<CambioSiu>? = null
    private var cancelado = false

    /** Química General ya está regularizada y el PDF dice "En curso": esa fila bajaría el estado. */
    private fun mostrar(): ImportacionSiu {
        RuntimeEnvironment.setQualifiers("w${ancho}dp-h${alto}dp-${if (ancho > alto) "land" else "port"}")
        val filas = (HistoriaSiu.leer(PdfDePrueba.historia()) as LecturaHistoria.Ok).filas
        val imp = ImportacionSiu.armar(filas, Avance(plan, mapOf(2 to Estado.REGULARIZADA)), Libreta())
        rule.setContent {
            TemaJuego(ModoTema.OSCURO, Estilo.PLANO) {
                VistaPreviaSiu(imp, onAplicar = { aplicados = it }, onCancelar = { cancelado = true })
            }
        }
        rule.mainClock.advanceTimeBy(500)
        return imp
    }

    private fun fila(texto: String) = rule.onNode(hasText(texto) and isToggleable())

    private fun irA(texto: String) {
        rule.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(texto, substring = true))
    }

    @Test fun lasBajasVanDestacadasYDesmarcadas() {
        val imp = mostrar()
        rule.onNodeWithText("cambio bajaría el estado", substring = true).assertExists()
        irA("Química General")
        fila("Química General").assertIsOff()
        irA("Ingeniería y Sociedad")
        fila("Ingeniería y Sociedad").assertIsOn()
        val marcadas = imp.conCambios.count { it.marcadoPorDefecto }
        rule.onNodeWithText("Aplicar ($marcadas)").assertIsEnabled().performClick()
        val ids = aplicados!!.map { it.materia.id }
        assertFalse("$nombre: la baja no se aplica si no se marca", 2 in ids)
        assertTrue(5 in ids)
    }

    @Test fun marcarUnaBajaLaAgrega() {
        val imp = mostrar()
        irA("Química General")
        fila("Química General").performClick().assertIsOn()
        val marcadas = imp.conCambios.count { it.marcadoPorDefecto } + 1
        rule.onNodeWithText("Aplicar ($marcadas)").performClick()
        assertTrue("$nombre: ahora sí va", 2 in aplicados!!.map { it.materia.id })
    }

    @Test fun muestraEstadoActualNuevoYNota() {
        mostrar()
        irA("Ingeniería y Sociedad")
        rule.onNodeWithText("Nota 9", substring = true).assertExists()
        irA("NO RECONOCIDAS")
        rule.onNodeWithText("NO RECONOCIDAS · 2").assertExists()
        rule.onNodeWithText("Materia de Otra Carrera (123456)").assertExists()
    }

    @Test fun cancelarNoAplicaNada() {
        mostrar()
        rule.onNodeWithText("Cancelar").performClick()
        assertTrue(cancelado)
        assertEquals(null, aplicados)
        assertNotNull(nombre)
    }
}
