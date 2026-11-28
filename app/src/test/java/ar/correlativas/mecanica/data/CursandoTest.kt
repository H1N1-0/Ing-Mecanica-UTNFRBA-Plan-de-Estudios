package ar.correlativas.mecanica.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Estado Cursando: para las correlativas cuenta como sin cursar, pero ya no se ofrece para cursar. */
class CursandoTest {
    private val plan = Plan.parse(listOf(File("../correlativas.json"), File("correlativas.json")).first { it.exists() }.readText())
    private fun avance(vararg e: Pair<Int, Estado>) = Avance(plan, e.toMap())
    private fun m(id: Int) = plan[id]!!

    @Test fun noHabilitaNadaComoSinCursar() {
        // Estabilidad I pide AM1, AGA y F1 regularizadas: cursándolas no alcanza.
        val cursando = avance(1 to Estado.CURSANDO, 3 to Estado.CURSANDO, 4 to Estado.CURSANDO)
        assertFalse(cursando.puedeCursar(m(10)))
        assertEquals(avance().faltantesCursar(m(10)), cursando.faltantesCursar(m(10)))
        assertEquals(0, cursando.aprobadasObligatorias)
        assertEquals(0.0, cursando.porcentajeCarrera, 0.0)
    }

    @Test fun noSeOfreceParaCursarNiParaRendir() {
        val a = avance(1 to Estado.CURSANDO)
        assertFalse(a.puedeCursar(m(1)))
        assertFalse(a.puedeRendir(m(1)))
        assertFalse(m(1) in a.cursables)
        assertEquals(EstadoNodo.CURSANDO, a.estadoNodo(m(1)))
        assertFalse(EstadoNodo.CURSANDO.completada)
    }

    @Test fun elCaminoCriticoLaCuentaPendiente() {
        assertEquals(CaminoCritico(avance()).anios, CaminoCritico(avance(1 to Estado.CURSANDO, 3 to Estado.CURSANDO)).anios)
    }

    @Test fun losLogrosNoCambian() {
        val logros = logros(plan)
        // "Primer paso" pide regularizar: cursar no alcanza.
        assertFalse(logros.first { it.id == "primer-paso" }.cumplido(avance(1 to Estado.CURSANDO)))
        assertTrue(logros.first { it.id == "primer-paso" }.cumplido(avance(1 to Estado.REGULARIZADA)))
    }

    @Test fun seGuardaConElFormatoDeSiempre() {
        val e = mapOf(1 to Estado.CURSANDO, 2 to Estado.REGULARIZADA)
        assertEquals("1:CURSANDO,2:REGULARIZADA", CodecEstados.codificar(e))
        assertEquals(e, CodecEstados.decodificar(CodecEstados.codificar(e)))
    }

    @Test fun laCopiaDeSeguridadLaLleva() {
        val r = Backup.deJson("""{"formato":"correlativas-mecanica","version":${Backup.VERSION},"progreso":"1:CURSANDO,2:APROBADA"}""", plan)
        val d = (r as LecturaBackup.Ok).datos
        assertEquals(Estado.CURSANDO, d.estados[1])
        assertEquals(1, d.cursando)
        assertTrue(Backup.aQr(d).startsWith(Backup.PREFIJO_QR))
        // Las copias anteriores (versión 2, QR "CMEC2:") se siguen leyendo.
        assertTrue(Backup.deJson("""{"formato":"correlativas-mecanica","version":2,"progreso":"1:PROMOCIONADA"}""", plan) is LecturaBackup.Ok)
    }
}
