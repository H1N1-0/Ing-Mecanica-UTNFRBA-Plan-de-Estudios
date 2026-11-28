package ar.correlativas.mecanica.data

import ar.correlativas.mecanica.data.PdfDePrueba.Fila
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Historia académica del SIU: tabla del PDF (con el fixture inventado de PdfDePrueba) y mapeo a la app. */
class HistoriaSiuTest {
    private val plan = Plan.parse(listOf(File("../correlativas.json"), File("correlativas.json")).first { it.exists() }.readText())

    private fun ok(r: LecturaHistoria) = (r as? LecturaHistoria.Ok)?.filas ?: error("se esperaba Ok y fue $r")
    private fun filas() = ok(HistoriaSiu.leer(PdfDePrueba.historia()))

    // ------------------------------------------------------------ parser

    @Test fun leeLasFilasDeLasDosPaginas() {
        val f = filas()
        assertEquals(PdfDePrueba.HISTORIA.size + PdfDePrueba.HISTORIA_PAGINA_2.size, f.size)
        assertEquals(listOf(1, 2), f.map { it.pagina }.distinct())
        val am1 = f.first()
        assertEquals("Análisis Matemático I", am1.nombre)
        assertEquals("950702", am1.codigo)
        assertEquals(LocalDate.of(2025, 12, 15), am1.fecha)
        assertEquals("Examen", am1.tipo)
        assertEquals(7, am1.nota)
        assertEquals("Aprobado", am1.resultado)
        // Sin nota ni resultado (En curso).
        f.first { it.codigo == "951407" }.let { assertNull(it.nota); assertEquals("", it.resultado); assertEquals("En curso", it.tipo) }
    }

    @Test fun unePorElNombreQueOcupaDosLineas() {
        val egpis = filas().first { it.codigo == "940872" }
        assertEquals("Evaluación y Gestión de Proyectos de Ingeniería Sustentables", egpis.nombre)
        assertEquals("Equivalencia", egpis.tipo)
        // Variante: la primera parte del nombre arriba, sola, y la fila (con el código) abajo.
        val pdf = PdfDePrueba.pdf(listOf(PdfDePrueba.paginaSiu(listOf(
            Fila("Química General (951407)", "25/03/2026", "En curso"),
            Fila("Evaluación y Gestión de Proyectos de Ingeniería Sustentables (940872)", "01/12/2025", "Examen", "8", "Aprobado"),
            Fila("Física I (950605)", "25/03/2026", "En curso"),
        ), 1, 1, anchoActividad = 40, partirArriba = true)))
        val f = ok(HistoriaSiu.leer(pdf))
        assertEquals(listOf("951407", "940872", "950605"), f.map { it.codigo })
        assertEquals("Evaluación y Gestión de Proyectos de Ingeniería Sustentables", f[1].nombre)
        assertEquals(8, f[1].nota)
    }

    @Test fun lasColumnasSeUbicanPorLosEncabezadosDeCadaPagina() {
        // La página 2 tiene las columnas en otras x: igual se leen bien.
        val p2 = filas().filter { it.pagina == 2 }
        assertEquals(listOf("Promocion", "Examen", "Examen", "Regularidad", "Taller"), p2.map { it.tipo })
        assertEquals(listOf(10, 8, 9, 4, null), p2.map { it.nota })
    }

    @Test fun losRotulosYElPieNoSonFilas() {
        val f = filas()
        assertTrue(f.none { "ESTUDIANTE" in it.actividad || "Propuesta" in it.actividad || " de " == it.actividad })
    }

    @Test fun unPdfQueNoEsDelSiuDaUnErrorClaro() {
        val otro = PdfDePrueba.pdf(listOf(listOf(PdfDePrueba.celdas(listOf(
            PdfDePrueba.Celda(50.0, 700.0, "Factura B"),
            PdfDePrueba.Celda(50.0, 680.0, "Total: 1234"),
        )))))
        val r = HistoriaSiu.leer(otro)
        assertTrue(r is LecturaHistoria.Error)
        assertTrue((r as LecturaHistoria.Error).motivo.contains("historia académica del SIU"))
        assertTrue(HistoriaSiu.leer("no es un pdf".toByteArray()) is LecturaHistoria.Error)
    }

    // ------------------------------------------------------------ mapeo

    private fun importar(estados: Map<Int, Estado> = emptyMap(), libreta: Libreta = Libreta()) =
        ImportacionSiu.armar(filas(), Avance(plan, estados), libreta)

    private fun ImportacionSiu.de(id: Int) = cambios.first { it.materia.id == id }

    @Test fun reglasDeCadaTipo() {
        val i = importar()
        // Examen aprobado → Aprobada con la nota del final (gana sobre la regularidad).
        i.de(1).let { assertEquals(Estado.APROBADA, it.nuevo); assertEquals(7, it.nota); assertEquals(LocalDate.of(2025, 12, 15), it.fecha); assertTrue(it.guardaNota) }
        // En curso → Cursando.
        assertEquals(Estado.CURSANDO, i.de(2).nuevo)
        // Regularidad aprobada + examen reprobado → Regularizada y un aplazo con su nota.
        i.de(4).let {
            assertEquals(Estado.REGULARIZADA, it.nuevo)
            assertFalse(it.guardaNota)
            assertEquals(listOf(2 to LocalDate.of(2026, 2, 10)), it.aplazos.map { a -> a.nota to a.fecha })
        }
        // Equivalencia aprobada → Aprobada (electiva de dos líneas).
        assertEquals(Estado.APROBADA, i.de(110).nuevo)
        // Regularidad reprobada → no cambia el estado.
        i.de(3).let { assertEquals(Estado.SIN_CURSAR, it.nuevo); assertFalse(it.tieneCambios) }
    }

    @Test fun conVariasFilasGanaLaMasAvanzada() {
        // Ingeniería y Sociedad: "Promocion / Promocionado" y "Regularidad / Aprobado" → Promocionada con 9.
        importar().de(5).let { assertEquals(Estado.PROMOCIONADA, it.nuevo); assertEquals(9, it.nota); assertEquals(2, it.filas.size) }
    }

    @Test fun buscaPorCodigoYSiNoPorNombre() {
        val i = importar()
        // Código que no está en el plan pero el nombre sí: Sistemas de Representación (7) e Inglés I (16).
        assertEquals(Estado.PROMOCIONADA, i.de(7).nuevo)
        assertEquals(Estado.APROBADA, i.de(16).nuevo)
        // Ni código ni nombre: va aparte, con el motivo.
        assertTrue(i.noReconocidas.any { it.fila.codigo == "123456" && "No está en el plan" in it.motivo })
        // Tipo desconocido: también aparte.
        assertTrue(i.noReconocidas.any { it.fila.codigo == "940861" && "Taller" in it.motivo })
    }

    @Test fun lasBajasVanDestacadasYDesmarcadas() {
        // Química General ya regularizada y el PDF dice En curso: bajaría, va desmarcada.
        val i = importar(mapOf(2 to Estado.REGULARIZADA, 1 to Estado.APROBADA))
        i.de(2).let { assertTrue(it.baja); assertFalse(it.marcadoPorDefecto) }
        // Subidas: marcadas.
        i.de(5).let { assertFalse(it.baja); assertTrue(it.marcadoPorDefecto) }
        // Mismo estado y otra nota: es un cambio (no una baja) y va marcado.
        i.de(1).let { assertFalse(it.baja); assertFalse(it.cambiaEstado); assertTrue(it.tieneCambios); assertTrue(it.marcadoPorDefecto) }
        // Mismo estado y misma nota: sin cambios.
        val igual = importar(mapOf(1 to Estado.APROBADA), Libreta(finales = mapOf(1 to NotaFinal(7, LocalDate.of(2025, 12, 15)))))
        assertFalse(igual.de(1).tieneCambios)
        assertTrue(igual.sinCambios.any { it.materia.id == 1 })
    }

    @Test fun reglaDeOrden() {
        val orden = listOf(Estado.SIN_CURSAR, Estado.CURSANDO, Estado.REGULARIZADA, Estado.APROBADA, Estado.PROMOCIONADA)
        assertEquals(orden, Estado.entries.sortedBy { ImportacionSiu.rango(it) })
    }

    @Test fun aplicarSoloLoElegido() {
        val libreta = Libreta(aplazos = listOf(Aplazo(1, 12, 3, null)))
        val i = importar(mapOf(2 to Estado.REGULARIZADA), libreta)
        val elegidos = i.cambios.filter { it.marcadoPorDefecto }
        val (estados, nueva) = ImportacionSiu.aplicar(elegidos, mapOf(2 to Estado.REGULARIZADA, 30 to Estado.APROBADA), libreta)
        assertEquals(Estado.APROBADA, estados[1])
        assertEquals(Estado.REGULARIZADA, estados[2]) // la baja quedó desmarcada
        assertEquals(Estado.REGULARIZADA, estados[4])
        assertEquals(Estado.PROMOCIONADA, estados[5])
        assertEquals(Estado.APROBADA, estados[30]) // lo que no está en el PDF no se toca
        assertNull(estados[3]) // sin cursar no se guarda
        assertEquals(NotaFinal(7, LocalDate.of(2025, 12, 15)), nueva.finales[1])
        assertEquals(NotaFinal(9, LocalDate.of(2026, 7, 8)), nueva.finales[5])
        assertNull(nueva.finales[4]) // la nota de una regularidad no es un final
        assertEquals(2, nueva.aplazos.size)
        assertEquals(nueva.aplazos.size, nueva.aplazos.map { it.id }.toSet().size)
    }

    @Test fun reimportarNoDuplicaAplazos() {
        val primera = importar()
        val (estados, libreta) = ImportacionSiu.aplicar(primera.cambios.filter { it.marcadoPorDefecto }, emptyMap(), Libreta())
        val segunda = ImportacionSiu.armar(filas(), Avance(plan, estados), libreta)
        assertTrue(segunda.de(4).aplazos.isEmpty())
        assertTrue(segunda.conCambios.isEmpty())
    }

    @Test fun leerCadaFila() {
        fun f(tipo: String, res: String) = ImportacionSiu.leer(FilaSiu(1, "X (1)", "X", "1", null, tipo, null, res))
        assertEquals(Estado.REGULARIZADA, f("Regularidad", "Aprobado").estado)
        assertNull(f("Regularidad", "Reprobado").estado)
        assertNull(f("Regularidad", "Ausente").estado)
        assertEquals(Estado.APROBADA, f("Examen", "Aprobado").estado)
        assertTrue(f("Examen", "Reprobado").aplazo)
        assertNull(f("Examen", "Ausente").estado)
        assertFalse(f("Examen", "Ausente").aplazo)
        assertEquals(Estado.PROMOCIONADA, f("Promoción", "Promocionado").estado)
        assertEquals(Estado.CURSANDO, f("En curso", "").estado)
        assertEquals(Estado.APROBADA, f("Equivalencia", "Aprobado").estado)
        assertTrue(f("Otra cosa", "").desconocida != null)
    }
}
