package ar.correlativas.mecanica.data

import ar.correlativas.mecanica.ui.calcularDesbloqueo
import ar.correlativas.mecanica.widget.repartir
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JuegoTest {
    private val plan = Plan.parse(
        listOf(File("../correlativas.json"), File("correlativas.json")).first { it.exists() }.readText()
    )
    private val logros = logros(plan)
    private fun avance(e: Map<Int, Estado>) = Avance(plan, e)
    private val todo = (plan.obligatorias + plan.pps).associate { it.id to Estado.APROBADA } +
        plan.electivas.filter { it.horasAnuales == 4.0 }.take(3).associate { it.id to Estado.APROBADA }

    @Test fun estadosDeNodo() {
        val a = avance(mapOf(1 to Estado.APROBADA, 3 to Estado.APROBADA, 4 to Estado.REGULARIZADA, 12 to Estado.REGULARIZADA))
        assertEquals(EstadoNodo.APROBADA, a.estadoNodo(plan[1]!!))
        assertEquals(EstadoNodo.PARA_RENDIR, a.estadoNodo(plan[4]!!))
        assertEquals(EstadoNodo.PARA_RENDIR, a.estadoNodo(plan[12]!!)) // pide 1 y 3 aprobadas
        assertEquals(EstadoNodo.DISPONIBLE, a.estadoNodo(plan[10]!!))
        assertEquals(EstadoNodo.BLOQUEADA, a.estadoNodo(plan[17]!!))
    }

    @Test fun porcentajeYRango() {
        assertEquals(0.0, avance(emptyMap()).porcentajeCarrera, 1e-9)
        assertEquals("Ingresante", rangoPara(0.0).nombre)
        assertEquals(1.0, avance(todo).porcentajeCarrera, 1e-9)
        assertEquals("Ingeniero Mecánico", rangoPara(1.0).nombre)
        assertEquals("Técnico", rangoPara(0.4).nombre)
        assertEquals("Casi Ingeniero", rangoPara(0.85).nombre)
    }

    @Test fun logros() {
        assertTrue(logros.none { it.cumplido(avance(emptyMap())) })
        assertTrue(logros.all { it.cumplido(avance(todo)) })
        val nivel1 = plan.obligatorias.filter { it.nivel == 1 }.associate { it.id to Estado.APROBADA }
        val conseguidos = logros.filter { it.cumplido(avance(nivel1)) }.map { it.id }.toSet()
        assertTrue("nivel-1" in conseguidos)
        assertTrue("primer-final" in conseguidos)
        assertFalse("nivel-2" in conseguidos)
    }

    @Test fun tituloIntermedio() {
        val logro = logros.first { it.id == "titulo-intermedio" }
        val materias = plan.obligatorias.filter { (it.nivel ?: 9) <= 3 && it.id !in setOf(18, 22, 24) }
        val base = materias.associate { it.id to Estado.APROBADA }
        // Tecnología Mecánica (4 hs) + Diseño en 3D (2 hs) = 6 hs, sin 18, 22 ni 24.
        assertTrue(logro.cumplido(avance(base + (104 to Estado.APROBADA) + (101 to Estado.APROBADA))))
        // 6 hs pero sin Tecnología Mecánica: no alcanza.
        assertFalse(logro.cumplido(avance(base + (102 to Estado.APROBADA) + (101 to Estado.APROBADA))))
        // Tecnología Mecánica sola son 4 hs: no alcanza.
        assertFalse(logro.cumplido(avance(base + (104 to Estado.APROBADA))))
        // Falta una materia que sí pide (Estabilidad II).
        assertFalse(logro.cumplido(avance(base - 19 + (104 to Estado.APROBADA) + (101 to Estado.APROBADA))))
    }

    @Test fun desbloqueoAlAprobar() {
        val antes = avance(mapOf(1 to Estado.REGULARIZADA, 3 to Estado.REGULARIZADA))
        val despues = avance(mapOf(1 to Estado.REGULARIZADA, 3 to Estado.REGULARIZADA, 4 to Estado.REGULARIZADA))
        val d = calcularDesbloqueo(plan[4]!!, antes, despues, logros)
        val ids = d.materias.map { it.id }.toSet()
        assertTrue(10 in ids && 13 in ids) // Estabilidad I y Física II
        assertTrue(d.logros.isEmpty())

        val nivel1 = plan.obligatorias.filter { it.nivel == 1 }.associate { it.id to Estado.APROBADA }
        val d2 = calcularDesbloqueo(plan[8]!!, avance(nivel1 - 8), avance(nivel1), logros)
        assertEquals(listOf(1), d2.nivelesCompletos)
    }

    @Test fun mapa() {
        val m = Mapa(plan)
        assertEquals(41, m.nodos.size)
        // Reducción transitiva: Física I -> Termodinámica sobra porque ya pasa por Física II.
        assertFalse(m.aristas.any { it.desde == 4 && it.hasta == 17 })
        assertTrue(m.aristas.any { it.desde == 13 && it.hasta == 17 })
        // Toda arista va de un nivel a uno posterior.
        val fila = m.nodos.associate { it.materia.id to it.fila }
        assertTrue(m.aristas.all { fila.getValue(it.desde) < fila.getValue(it.hasta) })
        // Sin nodos superpuestos.
        assertEquals(m.nodos.size, m.nodos.map { it.x to it.y }.toSet().size)
    }

    @Test fun repartirLineas() {
        assertEquals(3 to 2, repartir(5, 10, 2))
        assertEquals(2 to 3, repartir(5, 2, 10))
        assertEquals(3 to 2, repartir(5, 10, 10))
        assertEquals(0 to 0, repartir(0, 3, 3))
        assertEquals(1 to 0, repartir(4, 1, 0))
    }
}
