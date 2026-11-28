package ar.correlativas.mecanica.data

import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/**
 * Genera PDFs de prueba con la misma forma que la historia académica del SIU Guaraní: fuente
 * Courier con WinAnsiEncoding, un "BT x y Td /F1 10 Tf (texto) Tj ET" por celda y content
 * streams comprimidos con FlateDecode. Todos los datos son inventados.
 */
object PdfDePrueba {

    /** Una celda: texto en (x, y) de una página. */
    data class Celda(val x: Double, val y: Double, val texto: String)

    /** Arma un PDF con una página por lista de content streams (cada stream, texto ya armado). */
    fun pdf(paginas: List<List<String>>, comprimir: Boolean = true, extraTrailer: String = ""): ByteArray {
        val objetos = mutableListOf<ByteArray>()
        // 1 catálogo, 2 páginas, 3 fuente; después, por página: la página y sus streams.
        val idsPaginas = mutableListOf<Int>()
        var siguiente = 4
        val cuerpos = mutableListOf<Pair<Int, ByteArray>>()
        paginas.forEach { streams ->
            val idPagina = siguiente++
            idsPaginas += idPagina
            val idsStreams = streams.map { siguiente++ }
            cuerpos += idPagina to "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Contents [ ${idsStreams.joinToString(" ") { "$it 0 R" }} ] /Resources << /Font << /F1 3 0 R >> >> >>".toByteArray(Charsets.ISO_8859_1)
            streams.zip(idsStreams).forEach { (contenido, id) ->
                val datos = contenido.toByteArray(charset("windows-1252"))
                val (filtro, bytes) = if (comprimir) "/Filter /FlateDecode " to deflate(datos) else "" to datos
                val stream = ByteArrayOutputStream()
                stream.write("<< $filtro/Length ${bytes.size} >>\nstream\n".toByteArray(Charsets.ISO_8859_1))
                stream.write(bytes)
                stream.write("\nendstream".toByteArray(Charsets.ISO_8859_1))
                cuerpos += id to stream.toByteArray()
            }
        }
        objetos += "<< /Type /Catalog /Pages 2 0 R >>".toByteArray(Charsets.ISO_8859_1)
        objetos += "<< /Type /Pages /Kids [ ${idsPaginas.joinToString(" ") { "$it 0 R" }} ] /Count ${idsPaginas.size} >>".toByteArray(Charsets.ISO_8859_1)
        objetos += "<< /Type /Font /Subtype /Type1 /Name /F1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>".toByteArray(Charsets.ISO_8859_1)
        cuerpos.sortedBy { it.first }.forEach { objetos += it.second }

        val out = ByteArrayOutputStream()
        out.write("%PDF-1.3\n%âãÏÓ\n".toByteArray(Charsets.ISO_8859_1))
        val offsets = mutableListOf<Int>()
        objetos.forEachIndexed { i, o ->
            offsets += out.size()
            out.write("${i + 1} 0 obj\n".toByteArray(Charsets.ISO_8859_1))
            out.write(o)
            out.write("\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }
        val xref = out.size()
        out.write("xref\n0 ${objetos.size + 1}\n0000000000 65535 f \n".toByteArray(Charsets.ISO_8859_1))
        offsets.forEach { out.write(String.format("%010d 00000 n \n", it).toByteArray(Charsets.ISO_8859_1)) }
        out.write("trailer\n<< /Size ${objetos.size + 1} /Root 1 0 R $extraTrailer>>\nstartxref\n$xref\n%%EOF\n".toByteArray(Charsets.ISO_8859_1))
        return out.toByteArray()
    }

    fun deflate(datos: ByteArray): ByteArray {
        val d = Deflater()
        d.setInput(datos)
        d.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    /** "(texto)" con los paréntesis y barras escapados, como hace el SIU. */
    fun literal(s: String) = "(" + s.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)") + ")"

    fun celdas(celdas: List<Celda>): String = celdas.joinToString("\n") { c ->
        "BT ${"%.3f".format(java.util.Locale.ROOT, c.x)} ${"%.3f".format(java.util.Locale.ROOT, c.y)} Td /F1 10.0 Tf  ${literal(c.texto)} Tj ET"
    }

    /** Una fila de la historia: actividad, fecha, tipo, nota y resultado (vacíos se omiten). */
    data class Fila(val actividad: String, val fecha: String, val tipo: String, val nota: String = "", val resultado: String = "")

    /**
     * Página de historia académica al estilo SIU. [xs] son las x de las cinco columnas; las
     * actividades que no entran en [anchoActividad] caracteres se parten en dos líneas (la segunda,
     * sola en la columna Actividad, debajo de la fila).
     */
    fun paginaSiu(
        filas: List<Fila>,
        pagina: Int,
        total: Int,
        xs: List<Double> = listOf(20.945, 310.629, 442.753, 580.937, 676.701),
        anchoActividad: Int = 45,
        partirArriba: Boolean = false,
    ): List<String> {
        val encabezado = mutableListOf(
            Celda(xs[0], 462.73, "Actividad"), Celda(xs[1], 462.73, "Fecha"), Celda(xs[2], 462.73, "Tipo"),
            Celda(xs[3], 462.73, "Nota"), Celda(xs[4], 462.73, "Resultado"),
            Celda(152.0, 541.45, "HISTORIA ACADÉMICA"),
            Celda(333.945, 520.35, "Propuesta: Ingeniería Mecánica"),
            Celda(312.945, 499.25, "Alumno: ESTUDIANTE DE PRUEBA"),
            Celda(384.0, 22.0, "$pagina de $total"),
            Celda(678.4, 22.0, "01/01/2030 10:00:00"),
        )
        val cuerpo = mutableListOf<Celda>()
        var y = 445.68
        filas.forEach { f ->
            val (l1, l2) = if (f.actividad.length > anchoActividad) {
                val corte = f.actividad.lastIndexOf(' ', anchoActividad)
                f.actividad.substring(0, corte) to f.actividad.substring(corte + 1)
            } else f.actividad to null
            if (l2 != null && partirArriba) {
                // Variante: la primera línea del nombre va sola arriba y el resto de la fila abajo.
                cuerpo += Celda(xs[0], y, l1); y -= 14.55
                cuerpo += Celda(xs[0], y, l2)
            } else {
                cuerpo += Celda(xs[0], y, l1)
            }
            cuerpo += Celda(xs[1], y, f.fecha)
            cuerpo += Celda(xs[2], y, f.tipo)
            if (f.nota.isNotEmpty()) cuerpo += Celda(xs[3], y, f.nota)
            if (f.resultado.isNotEmpty()) cuerpo += Celda(xs[4], y, f.resultado)
            y -= 14.55
            if (l2 != null && !partirArriba) { cuerpo += Celda(xs[0], y, l2); y -= 14.55 }
        }
        // Como el SIU: un stream con encabezados y rótulos, otro con las filas y las líneas de la tabla.
        return listOf("0 0 0 rg\n" + celdas(encabezado), celdas(cuerpo) + "\n0 0 0 RG\n1 w\n15.945 475.280 m 15.945 339.330 l S")
    }

    /** Historia de ejemplo (inventada) de dos páginas; la segunda con las columnas corridas. */
    val HISTORIA = listOf(
        Fila("Análisis Matemático I (950702)", "15/12/2025", "Examen", "7", "Aprobado"),
        Fila("Análisis Matemático I (950702)", "20/11/2025", "Regularidad", "6", "Aprobado"),
        Fila("Química General (951407)", "25/03/2026", "En curso"),
        Fila("Ingeniería y Sociedad (951604)", "08/07/2026", "Promocion", "9", "Promocionado"),
        Fila("Ingeniería y Sociedad (951604)", "08/07/2026", "Regularidad", "9", "Aprobado"),
        Fila("Física I (950605)", "10/02/2026", "Examen", "2", "Reprobado"),
        Fila("Física I (950605)", "20/11/2025", "Regularidad", "7", "Aprobado"),
        Fila("Evaluación y Gestión de Proyectos de Ingeniería Sustentables (940872)", "01/12/2025", "Equivalencia", "", "Aprobado"),
    )
    val HISTORIA_PAGINA_2 = listOf(
        Fila("Sistemas de Representación (999999)", "05/07/2025", "Promocion", "10", "Promocionado"),
        Fila("Inglés I (000000)", "05/07/2025", "Examen", "8", "Aprobado"),
        Fila("Materia de Otra Carrera (123456)", "05/07/2025", "Examen", "9", "Aprobado"),
        Fila("Álgebra y Geometría Analítica (950701)", "10/03/2025", "Regularidad", "4", "Reprobado"),
        Fila("Fundamentos de Informática (940861)", "05/08/2026", "Taller", "", ""),
    )

    fun historia(): ByteArray = pdf(
        listOf(
            paginaSiu(HISTORIA, 1, 2, anchoActividad = 40),
            paginaSiu(HISTORIA_PAGINA_2, 2, 2, xs = listOf(30.0, 330.0, 455.0, 600.0, 690.0)),
        ),
    )
}
