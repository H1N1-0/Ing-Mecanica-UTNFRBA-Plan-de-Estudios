package ar.correlativas.mecanica.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class NotasTest {
    private val plan = Plan.parse(listOf(File("../correlativas.json"), File("correlativas.json")).first { it.exists() }.readText())
    private fun d(s: String) = LocalDate.parse(s)
    private fun avance(vararg e: Pair<Int, Estado>) = Avance(plan, e.toMap())

    @Test fun codecIdaYVuelta() {
        val l = Libreta(
            finales = mapOf(1 to NotaFinal(8, d("2024-07-15")), 3 to NotaFinal(null, d("2024-12-02")), 4 to NotaFinal(10, null)),
            aplazos = listOf(Aplazo(1, 4, 2, d("2024-03-01")), Aplazo(2, 12, 4, null)),
        )
        assertEquals(l, CodecLibreta.decodificar(CodecLibreta.codificar(l)))
    }

    @Test fun codecDescartaLoInvalido() {
        val json = """{"version":1,"finales":{"1":{"nota":11},"2":{"nota":0,"fecha":"mal"},"3":{"nota":7},"x":{"nota":5}},
            "aplazos":[{"id":5,"materia":4,"nota":6},{"id":5,"materia":4,"nota":2},{"materia":-1,"nota":2},{"id":5,"materia":10,"nota":3}]}"""
        val l = CodecLibreta.decodificar(json)
        assertEquals(mapOf(3 to NotaFinal(7, null)), l.finales)
        // El aplazo con nota 6 no es un aplazo; los ids repetidos se renumeran.
        assertEquals(listOf(4 to 2, 10 to 3), l.aplazos.map { it.materia to it.nota })
        assertEquals(2, l.aplazos.map { it.id }.toSet().size)
        assertTrue(CodecLibreta.decodificar("basura").vacia)
        assertTrue(CodecLibreta.decodificar(null).vacia)
    }

    @Test fun sinNotaLaMateriaSigueAprobada() {
        val a = avance(1 to Estado.APROBADA, 2 to Estado.APROBADA)
        val e = Estadisticas(a, Libreta())
        assertEquals(2, e.aprobadas.size)
        assertNull(e.promedioSinAplazos)
        assertEquals(2, e.finalesRendidos)
        assertFalse(e.hayDatos)
        // El cálculo de correlativas no mira las notas.
        assertTrue(a.puedeCursar(plan[12]!!).not() && avance(1 to Estado.APROBADA, 3 to Estado.APROBADA).puedeCursar(plan[12]!!))
    }

    @Test fun promediosConYSinAplazos() {
        val a = avance(1 to Estado.APROBADA, 3 to Estado.APROBADA, 4 to Estado.APROBADA, 2 to Estado.REGULARIZADA)
        val l = Libreta(
            finales = mapOf(
                1 to NotaFinal(8, d("2024-07-10")),
                3 to NotaFinal(6, d("2024-12-05")),
                4 to NotaFinal(10, d("2025-02-20")),
                // Química no está aprobada: su nota no cuenta.
                2 to NotaFinal(9, d("2025-03-01")),
            ),
            aplazos = listOf(Aplazo(1, 4, 2, d("2024-12-01")), Aplazo(2, 999, 1, null)),
        )
        val e = Estadisticas(a, l)
        assertEquals(8.0, e.promedioSinAplazos!!, 1e-9)
        // (8 + 6 + 10 + 2) / 4; el aplazo de una materia inexistente no cuenta.
        assertEquals(6.5, e.promedioConAplazos!!, 1e-9)
        assertEquals(4, e.finalesRendidos)
        assertEquals(4, e.mejor!!.materia.id)
        assertEquals(3, e.peor!!.materia.id)
        assertEquals("8,00", formatearPromedio(e.promedioSinAplazos))
        assertEquals("—", formatearPromedio(null))
    }

    @Test fun evolucionPorAnioEsAcumulada() {
        val a = avance(1 to Estado.APROBADA, 3 to Estado.APROBADA, 4 to Estado.APROBADA, 8 to Estado.APROBADA)
        val l = Libreta(
            finales = mapOf(
                1 to NotaFinal(8, d("2023-07-10")),
                3 to NotaFinal(6, d("2024-12-05")),
                4 to NotaFinal(10, d("2024-02-20")),
                8 to NotaFinal(9, null),
            ),
            aplazos = listOf(Aplazo(1, 4, 2, d("2023-12-01"))),
        )
        val e = Estadisticas(a, l)
        assertEquals(listOf(2023, 2024), e.evolucion.map { it.anio })
        assertEquals(8.0, e.evolucion[0].sinAplazos!!, 1e-9)
        assertEquals(5.0, e.evolucion[0].conAplazos!!, 1e-9)
        assertEquals(8.0, e.evolucion[1].sinAplazos!!, 1e-9)
        assertEquals(6.5, e.evolucion[1].conAplazos!!, 1e-9)
        // La nota sin fecha cuenta en el promedio, pero no en el gráfico.
        assertEquals(1, e.sinFecha)
        assertEquals(8.25, e.promedioSinAplazos!!, 1e-9)
    }

    @Test fun ppsNoLlevaNota() {
        val a = avance(plan.pps.id to Estado.APROBADA)
        assertTrue(Estadisticas(a, Libreta(mapOf(plan.pps.id to NotaFinal(9, null)))).notas.isEmpty())
    }

    @Test fun libretaEditarYBorrar() {
        val l = Libreta().conFinal(1, NotaFinal(7, null)).conAplazo(Aplazo(1, 1, 2, null)).conAplazo(Aplazo(2, 1, 4, null))
        assertEquals(2, l.aplazosDe(1).size)
        assertEquals(listOf(2L), l.sinAplazo(1).aplazos.map { it.id })
        // Guardar una nota vacía la borra.
        assertTrue(l.conFinal(1, NotaFinal(null, null)).finales.isEmpty())
        assertTrue(l.conFinal(1, null).finales.isEmpty())
    }
}
