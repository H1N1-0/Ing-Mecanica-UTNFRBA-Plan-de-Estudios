package ar.correlativas.mecanica.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CalculoTest {
    private val plan = Plan.parse(
        listOf(File("../correlativas.json"), File("correlativas.json")).first { it.exists() }.readText()
    )

    private fun avance(vararg e: Pair<Int, Estado>) = Avance(plan, e.toMap())
    private fun m(id: Int) = plan[id]!!

    @Test fun cargaElPlan() {
        assertEquals(40, plan.obligatorias.size)
        assertEquals(41, plan.pps.id)
        assertTrue(plan.electivas.isNotEmpty())
    }

    @Test fun alPrincipioSoloPrimerAnioYSinCorrelativas() {
        val a = avance()
        val ids = a.cursables.map { it.id }.toSet()
        assertTrue((1..8).all { it in ids })
        assertTrue(16 in ids) // Inglés I no tiene correlativas
        assertFalse(10 in ids)
        assertTrue(a.rendibles.isEmpty())
    }

    @Test fun regularizadaAlcanzaParaCursarPeroNoParaRendir() {
        val a = avance(1 to Estado.REGULARIZADA, 3 to Estado.REGULARIZADA, 4 to Estado.REGULARIZADA)
        assertTrue(a.puedeCursar(m(10)))
        assertTrue(a.puedeRendir(m(1)))
        // Estabilidad I regularizada pero con correlativas sin aprobar: no se rinde.
        val b = avance(1 to Estado.REGULARIZADA, 3 to Estado.REGULARIZADA, 4 to Estado.REGULARIZADA, 10 to Estado.REGULARIZADA)
        assertFalse(b.puedeRendir(m(10)))
        assertEquals(3, b.faltantesRendir(m(10)).size)
        val c = avance(1 to Estado.APROBADA, 3 to Estado.APROBADA, 4 to Estado.APROBADA, 10 to Estado.REGULARIZADA)
        assertTrue(c.puedeRendir(m(10)))
    }

    @Test fun aprobadasExigeFinal() {
        // Inglés II pide Inglés I aprobada para cursar.
        assertFalse(avance(16 to Estado.REGULARIZADA).puedeCursar(m(25)))
        assertTrue(avance(16 to Estado.APROBADA).puedeCursar(m(25)))
    }

    @Test fun proyectoFinalPideTodasYDiezHorasDeElectivas() {
        val todas = plan.obligatorias.filter { it.id != 40 }.associate { it.id to Estado.APROBADA }
        val sinElectivas = Avance(plan, todas + (40 to Estado.REGULARIZADA))
        assertFalse(sinElectivas.puedeRendir(m(40)))
        assertEquals(10.0, sinElectivas.faltanHorasParaRendir(m(40)), 0.0)

        val electivas = plan.electivas.filter { it.horasAnuales == 4.0 }.take(2).map { it.id to Estado.APROBADA } +
            (101 to Estado.APROBADA) // 2 hs
        val con10 = Avance(plan, todas + (40 to Estado.REGULARIZADA) + electivas)
        assertEquals(10.0, con10.horasElectivas, 0.0)
        assertTrue(con10.puedeRendir(m(40)))
    }

    @Test fun electivaSinHorasNoSuma() {
        assertEquals(0.0, avance(115 to Estado.APROBADA).horasElectivas, 0.0)
    }

    @Test fun ppsUsaRequisitosDeProyectoFinal() {
        val req = (listOf(27, 29, 31, 32).map { it to Estado.REGULARIZADA } +
            listOf(18, 19, 20, 21).map { it to Estado.APROBADA }).toTypedArray()
        val a = avance(*req)
        assertTrue(a.puedeCursar(plan.pps))
        assertTrue(a.puedeCursar(m(40)))
        assertFalse(a.puedeRendir(plan.pps))
    }

    @Test fun habilita() {
        val h = avance().habilita(16).associateBy { it.materia.id }
        assertEquals(Nivel.APROBADA, h[25]?.paraCursar)
        assertTrue(h.getValue(40).paraRendir)
    }

    @Test fun codec() {
        val e = mapOf(1 to Estado.APROBADA, 12 to Estado.REGULARIZADA, 3 to Estado.SIN_CURSAR)
        assertEquals(e - 3, CodecEstados.decodificar(CodecEstados.codificar(e)))
        assertTrue(CodecEstados.decodificar(null).isEmpty())
        assertTrue(CodecEstados.decodificar("basura,7:XX").isEmpty())
    }
}
