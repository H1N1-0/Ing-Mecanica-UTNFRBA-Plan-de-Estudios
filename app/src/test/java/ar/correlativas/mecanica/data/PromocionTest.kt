package ar.correlativas.mecanica.data

import ar.correlativas.mecanica.ui.calcularDesbloqueo
import ar.correlativas.mecanica.widget.promocionadasObligatorias
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.Base64
import java.util.zip.Deflater

/** Estado Promocionada: correlativas, promedio, logro y copias de seguridad (nuevas y viejas). */
class PromocionTest {
    private val plan = Plan.parse(listOf(File("../correlativas.json"), File("correlativas.json")).first { it.exists() }.readText())
    private fun avance(vararg e: Pair<Int, Estado>) = Avance(plan, e.toMap())
    private fun m(id: Int) = plan[id]!!

    // ------------------------------------------------------------ 1. correlativas

    @Test fun unaPromocionadaHabilitaSusCorrelativasIgualQueAprobada() {
        // Estabilidad I (10) pide AM1, AGA y F1: promocionadas alcanzan para cursarla.
        val promo = avance(1 to Estado.PROMOCIONADA, 3 to Estado.PROMOCIONADA, 4 to Estado.PROMOCIONADA)
        assertTrue(promo.puedeCursar(m(10)))
        // Y para rendir: Análisis II regularizada pide AM1 y AGA aprobadas.
        val rendir = avance(1 to Estado.PROMOCIONADA, 3 to Estado.PROMOCIONADA, 12 to Estado.REGULARIZADA)
        assertTrue(rendir.puedeRendir(m(12)))
        assertTrue(rendir.faltantesRendir(m(12)).isEmpty())
    }

    @Test fun paraCadaMateriaPromocionarDaLoMismoQueAprobar() {
        // Con un avance de base a mitad de carrera, cambiar cada materia de Aprobada a Promocionada
        // no cambia nada de lo que calculan las correlativas.
        val base = plan.obligatorias.filter { (it.nivel ?: 9) <= 2 }.associate { it.id to Estado.APROBADA } +
            plan.obligatorias.filter { it.nivel == 3 }.associate { it.id to Estado.REGULARIZADA }
        (plan.obligatorias + plan.electivas).forEach { materia ->
            val aprobada = Avance(plan, base + (materia.id to Estado.APROBADA))
            val promocionada = Avance(plan, base + (materia.id to Estado.PROMOCIONADA))
            val msg = "materia ${materia.id}"
            assertEquals(msg, aprobada.cursables.map { it.id }, promocionada.cursables.map { it.id })
            assertEquals(msg, aprobada.rendibles.map { it.id }, promocionada.rendibles.map { it.id })
            assertEquals(msg, aprobada.horasElectivas, promocionada.horasElectivas, 0.0)
            assertEquals(msg, aprobada.aprobadasObligatorias, promocionada.aprobadasObligatorias)
            assertEquals(msg, aprobada.porcentajeCarrera, promocionada.porcentajeCarrera, 1e-12)
            plan.todas.filter { it.id != materia.id }.forEach { otra ->
                assertEquals("$msg → ${otra.id}", aprobada.estadoNodo(otra), promocionada.estadoNodo(otra))
                assertEquals("$msg → ${otra.id}", aprobada.faltantesCursar(otra), promocionada.faltantesCursar(otra))
            }
        }
    }

    @Test fun proyectoFinalYCaminoCriticoLaCuentanAprobada() {
        val todas = plan.obligatorias.filter { it.id != Plan.PROYECTO_FINAL }.associate { it.id to Estado.PROMOCIONADA } +
            (Plan.PROYECTO_FINAL to Estado.REGULARIZADA) +
            plan.electivas.filter { it.horasAnuales == 4.0 }.take(3).associate { it.id to Estado.PROMOCIONADA }
        val a = Avance(plan, todas)
        assertTrue(a.puedeRendir(m(Plan.PROYECTO_FINAL)))
        val critico = CaminoCritico(a)
        assertTrue(critico.traba.keys.none { a.estado(it) == Estado.PROMOCIONADA })
    }

    @Test fun estadoDeNodoPropio() {
        val a = avance(1 to Estado.PROMOCIONADA)
        assertEquals(EstadoNodo.PROMOCIONADA, a.estadoNodo(m(1)))
        assertTrue(a.estadoNodo(m(1)).completada)
        assertFalse(a.puedeCursar(m(1)))
        assertFalse(a.puedeRendir(m(1)))
        // El widget las muestra aparte (ya incluidas en las aprobadas).
        assertEquals(1, promocionadasObligatorias(avance(1 to Estado.PROMOCIONADA, 2 to Estado.APROBADA)))
        assertEquals(2, avance(1 to Estado.PROMOCIONADA, 2 to Estado.APROBADA).aprobadasObligatorias)
    }

    @Test fun elProgresoGuardadoSigueIgualYSumaElValorNuevo() {
        val viejo = "1:APROBADA,3:REGULARIZADA"
        assertEquals(mapOf(1 to Estado.APROBADA, 3 to Estado.REGULARIZADA), CodecEstados.decodificar(viejo))
        val nuevo = mapOf(1 to Estado.APROBADA, 2 to Estado.PROMOCIONADA, 3 to Estado.REGULARIZADA)
        assertEquals("1:APROBADA,2:PROMOCIONADA,3:REGULARIZADA", CodecEstados.codificar(nuevo))
        assertEquals(nuevo, CodecEstados.decodificar(CodecEstados.codificar(nuevo)))
    }

    // ------------------------------------------------------------ 2. promedio y estadísticas

    @Test fun laPromocionEntraEnElPromedio() {
        val a = avance(1 to Estado.PROMOCIONADA, 2 to Estado.APROBADA, 3 to Estado.PROMOCIONADA)
        val l = Libreta(
            finales = mapOf(1 to NotaFinal(9, LocalDate.of(2024, 7, 1)), 2 to NotaFinal(6, null), 3 to NotaFinal(8, LocalDate.of(2025, 3, 1))),
            aplazos = listOf(Aplazo(1, 2, 2, LocalDate.of(2024, 2, 1))),
        )
        val e = Estadisticas(a, l)
        assertEquals(3, e.aprobadas.size)
        assertEquals(2, e.porPromocion)
        assertEquals(1, e.porFinal)
        assertEquals((9 + 6 + 8) / 3.0, e.promedioSinAplazos!!, 1e-9)
        assertEquals((9 + 6 + 8 + 2) / 4.0, e.promedioConAplazos!!, 1e-9)
        // Las promociones no son finales: rendidos = 1 aprobado por final + 1 aplazo.
        assertEquals(2, e.finalesRendidos)
        assertEquals(9, e.mejor!!.nota)
        assertEquals(1, e.mejor!!.materia.id)
        // Si se desmarca la promoción, su nota deja de contar.
        assertEquals(1, Estadisticas(avance(2 to Estado.APROBADA), l).notas.size)
    }

    // ------------------------------------------------------------ 3. logro

    @Test fun logroDeLaPrimeraPromocion() {
        val logros = logros(plan)
        val promo = logros.first { it.id == "primera-promocion" }
        assertFalse(promo.cumplido(avance()))
        assertFalse(promo.cumplido(avance(1 to Estado.APROBADA)))
        assertTrue(promo.cumplido(avance(1 to Estado.PROMOCIONADA)))
        // Se festeja al promocionar.
        val d = calcularDesbloqueo(m(1), avance(), avance(1 to Estado.PROMOCIONADA), logros)
        assertTrue(d.logros.any { it.id == "primera-promocion" })
        // "Primer final" sigue pidiendo un final, como antes.
        assertFalse(logros.first { it.id == "primer-final" }.cumplido(avance(1 to Estado.PROMOCIONADA)))
    }

    // ------------------------------------------------------------ 4. copias de seguridad

    /** Copia tal como la exportaba la versión 1 de la app (antes de la promoción). */
    private val copiaV1 = """
        {
          "formato": "correlativas-mecanica",
          "version": 1,
          "exportado": "2026-10-01",
          "progreso": "1:APROBADA,2:APROBADA,3:REGULARIZADA,101:APROBADA",
          "notas": {"version": 1, "finales": {"1": {"nota": 8, "fecha": "2025-12-10"}, "2": {"nota": 6}},
                    "aplazos": [{"id": 1, "materia": 3, "nota": 2, "fecha": "2025-07-20"}]},
          "test": {"porcentajes": "termico:60,diseno:40", "fecha": 1700000000000, "veces": 1},
          "ajustes": {"tema": "OSCURO", "estilo": "PLANO", "nombre": "Estudiante de prueba", "nombre_preguntado": true}
        }
    """.trimIndent()

    private fun ok(r: LecturaBackup) = (r as? LecturaBackup.Ok) ?: error("se esperaba Ok y fue $r")

    @Test fun unaCopiaViejaImportaBien() {
        val r = ok(Backup.deJson(copiaV1, plan))
        assertTrue(r.avisos.toString(), r.avisos.isEmpty())
        val d = r.datos
        assertEquals(mapOf(1 to Estado.APROBADA, 2 to Estado.APROBADA, 3 to Estado.REGULARIZADA, 101 to Estado.APROBADA), d.estados)
        assertEquals(NotaFinal(8, LocalDate.of(2025, 12, 10)), d.libreta.finales[1])
        assertEquals(NotaFinal(6, null), d.libreta.finales[2])
        assertEquals(1, d.libreta.aplazos.size)
        assertEquals(ModoTema.OSCURO, d.tema)
        assertEquals(Estilo.PLANO, d.estilo)
        assertEquals(0, d.promocionadas)
        assertEquals(3, d.aprobadas)
    }

    @Test fun unQrViejoImportaBien() {
        // QR de la versión 1: prefijo "CMEC1:" y el JSON compacto comprimido, como lo armaba aQr.
        val bytes = org.json.JSONObject(copiaV1).toString().toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true).apply { setInput(bytes); finish() }
        val salida = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) salida.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        val qrViejo = "CMEC1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(salida.toByteArray())
        assertEquals(ok(Backup.deJson(copiaV1, plan)).datos, ok(Backup.deQr(qrViejo, plan)).datos)
    }

    @Test fun laCopiaNuevaLlevaLasPromocionesYSubeDeVersion() {
        val datos = ok(Backup.deJson(copiaV1, plan)).datos.let {
            it.copy(
                estados = it.estados + (4 to Estado.PROMOCIONADA),
                libreta = it.libreta.conFinal(4, NotaFinal(10, LocalDate.of(2026, 7, 15))),
            )
        }
        val json = Backup.aJson(datos)
        assertEquals(2, org.json.JSONObject(json).getInt("version"))
        assertTrue(org.json.JSONObject(json).getString("progreso").contains("4:PROMOCIONADA"))
        assertEquals(datos, ok(Backup.deJson(json, plan)).datos)
        val qr = Backup.aQr(datos)
        assertTrue(qr.startsWith("CMEC2:"))
        assertEquals(datos, ok(Backup.deQr(qr, plan)).datos)
    }

    @Test fun avisaSiUnaPromocionadaNoTraeNota() {
        val json = """{"formato":"correlativas-mecanica","version":2,"progreso":"1:PROMOCIONADA,2:PROMOCIONADA",
            "notas":{"version":1,"finales":{"1":{"nota":7}},"aplazos":[]}}"""
        val r = ok(Backup.deJson(json, plan))
        assertEquals(Estado.PROMOCIONADA, r.datos.estados[2])
        assertNull(r.datos.libreta.finales[2])
        assertTrue(r.avisos.toString(), r.avisos.any { "promocionada no tiene nota" in it })
    }
}
