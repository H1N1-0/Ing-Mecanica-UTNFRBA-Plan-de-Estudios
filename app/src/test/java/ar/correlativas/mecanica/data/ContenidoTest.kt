package ar.correlativas.mecanica.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContenidoTest {
    private fun leer(nombre: String) = listOf(File("../$nombre"), File(nombre)).first { it.exists() }.readText()
    private val plan = Plan.parse(leer("correlativas.json"))
    private val test = TestVocacional.parse(leer("perfiles.json"))
    private val frases = Frases.parse(leer("frases.json"))

    @Test fun perfilesBienFormados() {
        assertEquals(7, test.perfiles.size)
        assertEquals(12, test.preguntas.size)
        val ids = test.perfiles.map { it.id }.toSet()
        // Toda opción suma a perfiles que existen.
        test.preguntas.flatMap { it.opciones }.forEach { op -> assertTrue(op.puntos.keys.all { it in ids }) }
        // Toda electiva recomendada existe en correlativas.json y es electiva.
        test.perfiles.flatMap { it.electivas }.forEach { assertEquals(Tipo.ELECTIVA, plan[it]?.tipo) }
        // Todos los perfiles se pueden ganar.
        ids.forEach { id -> assertTrue(test.preguntas.count { p -> p.opciones.any { (it.puntos[id] ?: 0) >= 2 } } >= 5) }
    }

    @Test fun porcentajesSuman100() {
        val r = test.calcular(List(12) { it % 4 })
        assertEquals(100, r.values.sum())
        assertEquals(test.perfiles.size, r.size)
        // Todo "termico" posible: gana térmico.
        val termico = test.preguntas.map { p -> p.opciones.indexOfFirst { (it.puntos["termico"] ?: 0) >= 2 }.coerceAtLeast(0) }
        assertEquals("termico", test.calcular(termico).maxBy { it.value }.key)
        // Sin respuestas no explota.
        assertEquals(0, test.calcular(emptyList()).values.sum())
    }

    @Test fun resultadoSeGuardaYLee() {
        val r = ResultadoPerfil(mapOf("termico" to 40, "diseno" to 60), 123L, 2)
        val leido = ResultadoPerfil.decodificar(r.codificar(), 123L, 2)!!
        assertEquals(r.porcentajes, leido.porcentajes)
        assertEquals("diseno", leido.principal)
        assertEquals(null, ResultadoPerfil.decodificar("", 0L, 0))
    }

    @Test fun frasesBienFormadas() {
        assertTrue(frases.todas.size in 100..170)
        assertEquals(frases.todas.size, frases.todas.map { it.id }.toSet().size)
        frases.todas.forEach {
            assertTrue(it.texto.isNotBlank() && it.autor.isNotBlank() && it.fuente.isNotBlank())
            it.materias.forEach { id -> assertTrue("materia $id", plan[id] != null) }
        }
        // Frase relacionada: Termodinámica trae algo de termodinámica.
        val f = frases.paraMateria(17, semilla = 1)
        assertTrue(17 in f.materias)
        // Frase del día y del widget: verificables; la del widget, corta.
        assertTrue(frases.delDia(20000).verificable)
        val w = frases.paraWidget(123456)
        assertTrue(w.verificable && w.texto.length <= Frases.MAX_WIDGET)
    }
}
