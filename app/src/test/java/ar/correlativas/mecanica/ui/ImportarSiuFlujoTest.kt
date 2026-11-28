package ar.correlativas.mecanica.ui

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import ar.correlativas.mecanica.data.CopiaAutomatica
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.LecturaBackup
import ar.correlativas.mecanica.data.Notas
import ar.correlativas.mecanica.data.PdfDePrueba
import ar.correlativas.mecanica.data.Progreso
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/** Importación del SIU de punta a punta en el ViewModel: error sin cambios, copia automática y deshacer. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportarSiuFlujoTest {
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var vm: CorrelativasViewModel

    @Before fun arrancar() {
        // El DataStore es un singleton del proceso: cada test arranca sin progreso, notas ni copia.
        runBlocking {
            Progreso(app).borrarTodo()
            Notas(app).borrarTodo()
            Progreso(app).cambiar(2, Estado.REGULARIZADA)
        }
        File(app.filesDir, "copias").deleteRecursively()
        vm = CorrelativasViewModel(app)
    }

    /** Corre lo pendiente del hilo principal (el ViewModel) hasta que se cumpla la condición. */
    private fun esperar(condicion: () -> Boolean) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condicion()) return
            Thread.sleep(20)
        }
    }

    private fun estados() = runBlocking { Progreso(app).estados.first() }

    private fun nota(id: Int) = runBlocking { Notas(app).libreta.first() }.finales[id]?.nota

    @Test fun unPdfQueNoEsDelSiuNoCambiaNada() {
        val antes = estados()
        val motivo = runBlocking { vm.prepararSiu("no es un pdf".toByteArray()) }
        assertNotNull(motivo)
        assertNull(vm.importacionSiu.value)
        assertEquals(antes, estados())
        assertFalse(CopiaAutomatica.existe(app))
    }

    @Test fun aplicarGuardaUnaCopiaAntesYDespuesSePuedeDeshacer() {
        assertNull(runBlocking { vm.prepararSiu(PdfDePrueba.historia()) })
        val imp = vm.importacionSiu.value!!
        vm.aplicarSiu(imp.conCambios.filter { it.marcadoPorDefecto })
        // Se guarda primero el progreso y después las notas: se espera a las dos cosas.
        esperar { estados()[5] == Estado.PROMOCIONADA && nota(5) == 9 && vm.mensajeSiu.value != null }

        val despues = estados()
        assertEquals(Estado.PROMOCIONADA, despues[5])
        assertEquals(Estado.APROBADA, despues[1])
        assertEquals(Estado.REGULARIZADA, despues[2]) // la baja (En curso) no se marcó
        assertEquals(9, nota(5))
        assertNull(vm.importacionSiu.value)
        assertTrue(vm.hayCopiaAutomatica.value)
        assertNotNull(vm.mensajeSiu.value)

        // La copia automática tiene lo de antes de importar: restaurarla deja todo como estaba.
        val copia = runBlocking { vm.leerCopiaAutomatica() } as LecturaBackup.Ok
        assertEquals(mapOf(2 to Estado.REGULARIZADA), copia.datos.estados)
        vm.importar(copia.datos)
        esperar { estados() == mapOf(2 to Estado.REGULARIZADA) }
        assertEquals(mapOf(2 to Estado.REGULARIZADA), estados())
    }
}
