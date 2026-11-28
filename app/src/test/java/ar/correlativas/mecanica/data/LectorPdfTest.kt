package ar.correlativas.mecanica.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** Lector de PDF propio: textos con su posición, páginas, filtros y errores. */
class LectorPdfTest {
    private fun textos(vararg paginas: List<String>, comprimir: Boolean = true) =
        LectorPdf.textos(PdfDePrueba.pdf(paginas.toList(), comprimir))

    @Test fun leeTextosConPosicionYTildes() {
        val t = textos(listOf("BT 20.945 445.680 Td /F1 10.0 Tf  (Álgebra y Geometría \\(950701\\)) Tj ET"))
        assertEquals(1, t.size)
        assertEquals("Álgebra y Geometría (950701)", t[0].texto)
        assertEquals(20.945, t[0].x, 1e-6)
        assertEquals(445.68, t[0].y, 1e-6)
        assertEquals(1, t[0].pagina)
    }

    @Test fun variosStreamsYPaginasEnOrden() {
        val t = textos(
            listOf("BT 10 500 Td (A) Tj ET", "BT 10 400 Td (B) Tj ET"),
            listOf("BT 10 500 Td (C) Tj ET"),
        )
        assertEquals(listOf("A" to 1, "B" to 1, "C" to 2), t.map { it.texto to it.pagina })
    }

    @Test fun streamsSinComprimir() {
        val t = textos(listOf("BT 10 500 Td (Sin filtro) Tj ET"), comprimir = false)
        assertEquals("Sin filtro", t.single().texto)
    }

    @Test fun operadoresDeTextoYMatrices() {
        val contenido = """
            q 1 0 0 1 100 50 cm
            BT /F1 10 Tf 10 20 Td (uno) Tj 0 -12 Td (dos) Tj ET
            Q
            BT 1 0 0 1 300 400 Tm 14 TL (tres) Tj T* (cuatro) Tj ET
            BT 5 5 Td [(Hola) -300 (mundo)] TJ ET
            BT 5 600 Td <456E7472E9> Tj ( cortado) Tj ET
            BT 7 7 Td (escapes: \\ \101\102 \(x\)) Tj ET
        """.trimIndent()
        val t = textos(listOf(contenido))
        val porTexto = t.associateBy { it.texto }
        porTexto.getValue("uno").let { assertEquals(110.0, it.x, 1e-9); assertEquals(70.0, it.y, 1e-9) }
        porTexto.getValue("dos").let { assertEquals(110.0, it.x, 1e-9); assertEquals(58.0, it.y, 1e-9) }
        porTexto.getValue("tres").let { assertEquals(300.0, it.x, 1e-9); assertEquals(400.0, it.y, 1e-9) }
        porTexto.getValue("cuatro").let { assertEquals(300.0, it.x, 1e-9); assertEquals(386.0, it.y, 1e-9) }
        assertTrue(t.toString(), "Hola mundo" in porTexto)
        // Un texto que sigue sin mover el cursor se pega al anterior; hex en WinAnsi ("Entré").
        assertTrue(t.toString(), "Entré cortado" in porTexto)
        assertTrue(t.toString(), "escapes: \\ AB (x)" in porTexto)
    }

    @Test fun noEsUnPdf() {
        espera("no es un PDF") { LectorPdf.textos("hola, soy un texto".toByteArray()) }
        espera("no es un PDF") { LectorPdf.textos(ByteArray(0)) }
    }

    @Test fun pdfProtegido() {
        val pdf = PdfDePrueba.pdf(listOf(listOf("BT 10 10 Td (x) Tj ET")), extraTrailer = "/Encrypt 99 0 R ")
        espera("protegido") { LectorPdf.textos(pdf) }
    }

    @Test fun streamDaniado() {
        val bueno = PdfDePrueba.pdf(listOf(listOf("BT 10 10 Td (un texto bastante largo para comprimir) Tj ET")))
        // Se rompen los datos comprimidos (sin tocar el largo): no se puede descomprimir.
        val texto = String(bueno, Charsets.ISO_8859_1)
        val inicio = texto.indexOf("stream\n", texto.indexOf("/FlateDecode")) + "stream\n".length
        val roto = bueno.copyOf().also { for (k in inicio + 2 until inicio + 12) it[k] = 0x7F }
        espera("dañado") { LectorPdf.textos(roto) }
    }

    private fun espera(parte: String, bloque: () -> Unit) {
        try {
            bloque()
            fail("se esperaba PdfIlegible")
        } catch (e: PdfIlegible) {
            assertTrue(e.message, e.message!!.contains(parte, ignoreCase = true))
        }
    }
}
