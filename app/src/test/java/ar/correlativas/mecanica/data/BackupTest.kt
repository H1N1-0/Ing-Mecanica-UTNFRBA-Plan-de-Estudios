package ar.correlativas.mecanica.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

class BackupTest {
    private val plan = Plan.parse(listOf(File("../correlativas.json"), File("correlativas.json")).first { it.exists() }.readText())

    private val datos = DatosBackup(
        estados = (1..30).associateWith { Estado.APROBADA } + (31 to Estado.REGULARIZADA) + plan.electivas.take(4).associate { it.id to Estado.APROBADA },
        libreta = Libreta(
            finales = (1..30).associateWith { NotaFinal(4 + it % 7, LocalDate.of(2020 + it / 8, 1 + it % 12, 1 + it % 27)) },
            aplazos = (1..12).map { Aplazo(it.toLong(), it * 2, 1 + it % 5, LocalDate.of(2021, 1 + it % 12, 10)) },
        ),
        perfil = ResultadoPerfil(mapOf("termico" to 40, "diseno" to 35, "automatizacion" to 25), 1_700_000_000_000, 2),
        tema = ModoTema.OSCURO,
        estilo = Estilo.MATERIAL,
        nombre = "Estudiante de prueba",
        nombrePreguntado = true,
        exportado = LocalDate.of(2026, 11, 27),
    )

    private fun ok(r: LecturaBackup) = (r as? LecturaBackup.Ok) ?: error("se esperaba Ok y fue $r")

    @Test fun jsonIdaYVuelta() {
        val r = ok(Backup.deJson(Backup.aJson(datos), plan))
        assertEquals(datos, r.datos)
        assertTrue(r.avisos.isEmpty())
    }

    @Test fun elProgresoViajaConSuFormatoDeSiempre() {
        val o = JSONObject(Backup.aJson(datos))
        // Mismo texto que se guarda en DataStore: importar no transforma el progreso.
        assertEquals(CodecEstados.codificar(datos.estados), o.getString("progreso"))
    }

    @Test fun qrIdaYVueltaYEntraEnUnQr() {
        val texto = Backup.aQr(datos)
        assertTrue(texto.startsWith(Backup.PREFIJO_QR))
        assertEquals(datos, ok(Backup.deQr(texto, plan)).datos)
        // Un QR llega hasta 2331 bytes con corrección media: este, con 30 notas, entra con holgura.
        assertTrue("QR de ${texto.length} caracteres", texto.length < 1600)
    }

    @Test fun qrSeGeneraYSeLeeConZxing() {
        val texto = Backup.aQr(datos)
        val m = Qr.generar(texto)
        // Se dibuja a 4 px por módulo con margen blanco y se vuelve a leer como si fuera una captura.
        val escala = 4
        val margen = 4 * escala
        val lado = m.lado * escala + 2 * margen
        val pixeles = IntArray(lado * lado) { i ->
            val x = (i % lado - margen) / escala
            val y = (i / lado - margen) / escala
            val dentro = i % lado >= margen && i / lado >= margen && x < m.lado && y < m.lado
            if (dentro && m[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        assertEquals(texto, Qr.leerPixeles(lado, lado, pixeles))
    }

    @Test fun rechazaLoQueNoEsUnaCopia() {
        assertTrue(Backup.deJson("no es json", plan) is LecturaBackup.Error)
        assertTrue(Backup.deJson("""{"formato":"otra-app","version":1}""", plan) is LecturaBackup.Error)
        val nueva = Backup.deJson("""{"formato":"correlativas-mecanica","version":99}""", plan) as LecturaBackup.Error
        assertTrue(nueva.motivo.contains("más nueva"))
        assertTrue(Backup.deQr("https://example.com", plan) is LecturaBackup.Error)
        assertTrue(Backup.deQr("CMEC1:%%%dañado", plan) is LecturaBackup.Error)
        assertTrue((Backup.deQr("CMEC3:abc", plan) as LecturaBackup.Error).motivo.contains("más nueva"))
    }

    @Test fun laAppNoPidePermisoDeInternet() {
        val manifiesto = listOf(File("src/main/AndroidManifest.xml"), File("app/src/main/AndroidManifest.xml")).first { it.exists() }.readText()
        // Se quita explícitamente aunque alguna librería lo pida: todo es local.
        assertTrue(Regex("""android\.permission\.INTERNET"\s+tools:node="remove"""").containsMatchIn(manifiesto))
    }

    @Test fun descartaMateriasInexistentesConAviso() {
        val json = """{"formato":"correlativas-mecanica","version":1,"progreso":"1:APROBADA,999:APROBADA,2:RARO",
            "notas":{"version":1,"finales":{"1":{"nota":9},"999":{"nota":8}},"aplazos":[]}}"""
        val r = ok(Backup.deJson(json, plan))
        assertEquals(mapOf(1 to Estado.APROBADA), r.datos.estados)
        assertEquals(setOf(1), r.datos.libreta.finales.keys)
        assertEquals(2, r.avisos.size)
        // Sin ajustes en la copia: valores por defecto.
        assertEquals(Estilo.PREDETERMINADO, r.datos.estilo)
    }
}
