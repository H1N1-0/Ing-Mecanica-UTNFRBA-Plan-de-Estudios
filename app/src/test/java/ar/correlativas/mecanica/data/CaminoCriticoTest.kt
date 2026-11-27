package ar.correlativas.mecanica.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CaminoCriticoTest {
    private val plan = Plan.parse(
        listOf(File("../correlativas.json"), File("correlativas.json")).first { it.exists() }.readText()
    )
    private val grafo = GrafoPlan(plan)

    private fun critico(estados: Map<Int, Estado>) = CaminoCritico(Avance(plan, estados), grafo)
    private fun aprobadas(ids: Iterable<Int>) = ids.associateWith { Estado.APROBADA }

    // ------------------------------------------------------------------ plan chico a mano

    /**
     * 1 → 2 → 4 → 40 (Proyecto Final) y 3 → 40, más 5 suelta.
     * La cadena más larga es 1, 2, 4, 40: cuatro cursadas.
     */
    private val chico = Plan(
        carrera = "Prueba", facultad = "", plan = "", reglas = emptyMap(),
        obligatorias = listOf(
            materia(1, 1),
            materia(2, 2, cursar = Requisitos(regularizadas = listOf(1))),
            materia(3, 2),
            materia(4, 3, cursar = Requisitos(aprobadas = listOf(2)), rendir = Requisitos(aprobadas = listOf(2, 3))),
            materia(5, 1),
            materia(40, 4, cursar = Requisitos(regularizadas = listOf(3, 4)), reglaRendir = "todas"),
        ),
        electivas = emptyList(),
        pps = materia(41, null, cursar = Requisitos(regularizadas = listOf(4)), tipo = Tipo.PPS, rendir = null),
    )

    private fun materia(
        id: Int, nivel: Int?, cursar: Requisitos = Requisitos(), rendir: Requisitos? = Requisitos(),
        reglaRendir: String? = null, tipo: Tipo = Tipo.OBLIGATORIA,
    ) = Materia(id, "M$id", nivel, tipo, cursar, rendir, reglaRendir)

    @Test fun planChico_cadenaMasLargaYAnios() {
        val c = CaminoCritico(Avance(chico, emptyMap()))
        assertEquals(4, c.anios)
        assertEquals(listOf(1, 2, 4, 40), c.camino.map { it.id })
        assertEquals(0, c.holgura(1))
        assertEquals(0, c.holgura(40))
        // La 3 se puede cursar hasta la tercera cursada sin atrasar nada: 2 de margen.
        assertEquals(2, c.holgura(3))
        // La PPS termina a la par del Proyecto Final: tampoco tiene margen, pero el camino elige el PF.
        assertEquals(0, c.holgura(41))
        assertFalse(c.enCamino(41))
    }

    @Test fun planChico_trabaCuentaDirectasEIndirectas() {
        val c = CaminoCritico(Avance(chico, emptyMap()))
        // 1 traba 2, 4, 40 y la PPS (que pide la 4).
        assertEquals(4, c.trabaDe(1))
        // 3 traba 4 (para rendir), 40 y la PPS (a través de la 4).
        assertEquals(3, c.trabaDe(3))
        // 5 no es correlativa de nadie, pero Proyecto Final se rinde con todas las obligatorias.
        assertEquals(1, c.trabaDe(5))
        assertEquals(0, c.trabaDe(40))
        assertEquals(0, c.trabaDe(41))
    }

    @Test fun planChico_lasAprobadasNoCuentanNiTraban() {
        val c = CaminoCritico(Avance(chico, mapOf(1 to Estado.APROBADA, 2 to Estado.APROBADA)))
        assertEquals(2, c.anios)
        // 3 → 40 y 4 → 40 son igual de largas: gana la 3, que traba más (4, 40 y PPS).
        assertEquals(listOf(3, 40), c.camino.map { it.id })
        assertEquals(0, c.trabaDe(1))
        // La 3 ya no traba a la 2 (aprobada) pero sí a 4, 40 y PPS.
        assertEquals(3, c.trabaDe(3))
    }

    @Test fun planChico_regularizadaNoSumaCursada() {
        // Con la 1 y la 2 regularizadas solo falta cursar 4 y 40, aunque la 2 todavía traba.
        val c = CaminoCritico(Avance(chico, mapOf(1 to Estado.REGULARIZADA, 2 to Estado.REGULARIZADA)))
        assertEquals(2, c.anios)
        assertNull(c.holgura(2))
        assertEquals(3, c.trabaDe(2))
    }

    // ------------------------------------------------------------------ plan real

    @Test fun alEmpezarFaltanCincoCursadas() {
        val c = critico(emptyMap())
        assertEquals(5, c.anios)
        assertEquals(5, c.camino.size)
        assertEquals(Plan.PROYECTO_FINAL, c.camino.last().id)
        assertEquals(listOf(1, 2, 3, 4, 5), c.camino.map { it.nivel })
        // Física I es la que más traba al principio.
        assertEquals(4, c.camino.first().id)
    }

    @Test fun elCaminoEsUnaCadenaDeCorrelativas() {
        listOf(
            emptyMap(),
            aprobadas(1..8),
            aprobadas(1..16) + (17 to Estado.REGULARIZADA),
        ).forEach { estados ->
            val c = critico(estados)
            assertEquals(c.anios, c.camino.size)
            c.camino.zipWithNext().forEach { (a, b) ->
                assertTrue("${a.id} → ${b.id}", a.id in grafo.previasCursar.getValue(b.id))
            }
            c.camino.forEach { assertEquals(Estado.SIN_CURSAR, Avance(plan, estados).estado(it.id)) }
        }
    }

    @Test fun conPrimerAnioAprobadoFaltanCuatro() {
        val c = critico(aprobadas(1..8))
        assertEquals(4, c.anios)
        assertTrue(c.camino.none { it.nivel == 1 })
    }

    @Test fun conTodoCursadoNoFaltanCursadas() {
        val todo = (plan.obligatorias + plan.pps).associate { it.id to Estado.REGULARIZADA }
        val c = critico(todo)
        assertEquals(0, c.anios)
        assertTrue(c.camino.isEmpty())
        // Igual siguen trabando: sin aprobar Análisis I no se rinde Proyecto Final.
        assertTrue(c.trabaDe(1) > 0)
    }

    @Test fun carreraTerminadaNoTrabaNada() {
        val c = critico(aprobadas((plan.obligatorias + plan.pps).map { it.id }))
        assertEquals(0, c.anios)
        assertTrue(c.traba.isEmpty())
        assertTrue(c.cuellos.isEmpty())
        assertTrue(c.prioridades().isEmpty())
    }

    @Test fun trabaIncluyeIndirectas() {
        val c = critico(emptyMap())
        val directas = grafo.ids.count { 1 in grafo.dependeDe.getValue(it) }
        // Análisis I traba más de las que la piden directamente (por ejemplo, Elementos de Máquinas vía Estabilidad II).
        assertTrue(c.trabaDe(1) > directas)
        assertTrue(27 in grafo.dependientes.getValue(1))
        // Inglés I: Inglés II y Proyecto Final.
        assertEquals(2, c.trabaDe(16))
        // Al aprobar Inglés II, Inglés I sigue trabando solo el Proyecto Final.
        assertEquals(1, critico(mapOf(25 to Estado.APROBADA)).trabaDe(16))
    }

    @Test fun cuellosDeBotellaSonLasQueMasTraban() {
        val c = critico(emptyMap())
        assertTrue(c.esCuello(4))
        assertTrue(c.esCuello(1))
        assertFalse(c.esCuello(16))
        val minimoCuello = c.cuellos.minOf { c.trabaDe(it) }
        c.traba.filterKeys { it !in c.cuellos }.values.forEach { assertTrue(it <= minimoCuello) }
        assertTrue(c.cuellos.size >= CaminoCritico.MAX_CUELLOS)
    }

    @Test fun prioridadesSonCursablesOrdenadasPorTraba() {
        val avance = Avance(plan, emptyMap())
        val p = CaminoCritico(avance, grafo).prioridades()
        assertEquals(3, p.size)
        assertEquals(listOf(4, 1, 3), p.map { it.materia.id })
        p.forEach { assertTrue(avance.puedeCursar(it.materia)) }
        assertEquals(p.sortedByDescending { it.traba }, p)
        assertTrue(p.first().enCaminoCritico)
    }

    @Test fun prioridadCuentaLoQueAbreYa() {
        // Con Análisis I y Álgebra aprobadas, aprobar Física I abre Estabilidad I y Física II
        // (además de Ingeniería Mecánica II si tenés Ingeniería Mecánica I).
        val c = critico(aprobadas(listOf(1, 3)))
        val fisica = c.prioridades(10).first { it.materia.id == 4 }
        assertEquals(2, fisica.abreYa)
        val conIM1 = critico(aprobadas(listOf(1, 3, 6))).prioridades(10).first { it.materia.id == 4 }
        assertEquals(3, conIM1.abreYa)
    }

    @Test fun prioridadIncluyeFinalesParaRendir() {
        // Física I regularizada y lista para rendir: aparece aunque ya no se pueda cursar.
        val c = critico(mapOf(4 to Estado.REGULARIZADA))
        assertTrue(c.prioridades().any { it.materia.id == 4 })
    }

    @Test fun seRecalculaAlMarcar() {
        val antes = critico(emptyMap())
        val despues = critico(aprobadas(listOf(4)))
        assertTrue(antes.esCuello(4))
        assertFalse(despues.esCuello(4))
        assertTrue(despues.prioridades().none { it.materia.id == 4 })
    }

    @Test fun noTieneCiclos() {
        // Toda materia del grafo tiene un largo finito: si hubiera ciclos, la recursión no terminaría.
        grafo.ids.forEach { id -> assertFalse(id in grafo.dependientes.getValue(id)) }
    }
}
