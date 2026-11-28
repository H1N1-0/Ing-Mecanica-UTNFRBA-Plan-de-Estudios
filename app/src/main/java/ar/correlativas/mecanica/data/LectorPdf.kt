package ar.correlativas.mecanica.data

import java.io.ByteArrayOutputStream
import java.util.zip.Inflater

/** Un texto de una página con la posición donde empieza (puntos PDF, origen abajo a la izquierda). */
data class TextoPdf(val pagina: Int, val x: Double, val y: Double, val texto: String)

/** El archivo no se pudo leer como PDF (no es PDF, está protegido o dañado). */
class PdfIlegible(mensaje: String) : Exception(mensaje)

/**
 * Lector de PDF mínimo, sin librerías: alcanza para la historia académica del SIU Guaraní, que se
 * genera con fuentes estándar (Courier, WinAnsiEncoding) y texto con operadores simples.
 *
 * - Ubica los objetos por "n 0 obj" (también dentro de object streams) y recorre el árbol de
 *   páginas desde /Root; si no puede, toma los objetos /Type /Page en orden.
 * - Descomprime los content streams FlateDecode con [Inflater] (los streams sin filtro se leen tal cual).
 * - Interpreta BT/ET, Td, TD, Tm, T*, TL, Tj, TJ, ' y " y la matriz cm (con q/Q) para dar la posición
 *   de cada texto. Los strings se decodifican como WinAnsi (cp1252), que es lo que usa el SIU.
 *
 * No intenta leer fuentes con codificaciones propias (CID/ToUnicode): con esas el texto sale
 * ilegible y el que llama lo detecta porque no encuentra lo que busca.
 */
object LectorPdf {

    private val WIN_ANSI = charset("windows-1252")

    fun textos(bytes: ByteArray): List<TextoPdf> {
        if (bytes.size < 8 || String(bytes, 0, 5, Charsets.ISO_8859_1) != "%PDF-") throw PdfIlegible("El archivo no es un PDF.")
        val doc = Documento(bytes)
        if (doc.texto.contains("/Encrypt")) throw PdfIlegible("El PDF está protegido con contraseña.")
        val paginas = doc.paginas()
        if (paginas.isEmpty()) throw PdfIlegible("El PDF no tiene páginas que se puedan leer.")
        return paginas.flatMapIndexed { i, pagina ->
            val contenido = doc.contenidoDe(pagina)
            Interprete(i + 1).correr(contenido)
        }
    }

    // ------------------------------------------------------------ objetos

    /** Un objeto: su diccionario (texto) y, si es un stream, dónde empiezan sus datos. */
    private class Objeto(val dic: String, val inicioStream: Int?, val cuerpo: String)

    private class Documento(val bytes: ByteArray) {
        /** Los bytes como texto 1 a 1 (ISO-8859-1): las posiciones coinciden con las de [bytes]. */
        val texto = String(bytes, Charsets.ISO_8859_1)
        private val offsets = HashMap<Int, Int>()
        /** Objetos que viven comprimidos en object streams: número → texto del objeto. */
        private val comprimidos = HashMap<Int, String>()

        init {
            Regex("""(?<![0-9])(\d+)\s+\d+\s+obj\b""").findAll(texto).forEach { offsets[it.groupValues[1].toInt()] = it.range.last + 1 }
            // Object streams (PDF 1.5+): los objetos de adentro también pueden ser páginas.
            offsets.keys.toList().forEach { n ->
                val o = objeto(n) ?: return@forEach
                if (Regex("""/Type\s*/ObjStm\b""").containsMatchIn(o.dic)) runCatching { leerObjStm(o) }
            }
        }

        fun objeto(n: Int): Objeto? {
            comprimidos[n]?.let { return Objeto(it, null, it) }
            val desde = offsets[n] ?: return null
            val fin = texto.indexOf("endobj", desde).let { if (it < 0) texto.length else it }
            val cuerpo = texto.substring(desde, fin)
            val s = Regex("""\bstream\r?\n""").find(cuerpo)
            return if (s == null) Objeto(cuerpo, null, cuerpo) else Objeto(cuerpo.substring(0, s.range.first), desde + s.range.last + 1, cuerpo)
        }

        private fun entero(valor: String): Int? {
            Regex("""^\s*(\d+)\s+\d+\s+R""").find(valor)?.let { ref -> return objeto(ref.groupValues[1].toInt())?.cuerpo?.trim()?.toIntOrNull() }
            return Regex("""^\s*(\d+)""").find(valor)?.groupValues?.get(1)?.toIntOrNull()
        }

        /** Datos de un stream, descomprimidos si tienen FlateDecode. null si usa otro filtro. */
        fun datos(o: Objeto): ByteArray? {
            val inicio = o.inicioStream ?: return null
            val largo = Regex("""/Length\s+([^/>]+)""").find(o.dic)?.groupValues?.get(1)?.let { entero(it) }
            val fin = if (largo != null && inicio + largo <= bytes.size) inicio + largo
            else texto.indexOf("endstream", inicio).let { if (it < 0) bytes.size else it }
            val crudo = bytes.copyOfRange(inicio, fin)
            val filtros = Regex("""/Filter\s*(\[[^\]]*]|/\w+)""").find(o.dic)?.groupValues?.get(1).orEmpty()
            val nombres = Regex("""/(\w+)""").findAll(filtros).map { it.groupValues[1] }.toList()
            return when {
                nombres.isEmpty() -> crudo
                nombres == listOf("FlateDecode") -> inflar(crudo)
                else -> null
            }
        }

        private fun leerObjStm(o: Objeto) {
            val datos = datos(o) ?: return
            val n = Regex("""/N\s+(\d+)""").find(o.dic)?.groupValues?.get(1)?.toInt() ?: return
            val primero = Regex("""/First\s+(\d+)""").find(o.dic)?.groupValues?.get(1)?.toInt() ?: return
            val t = String(datos, Charsets.ISO_8859_1)
            val pares = t.substring(0, primero).trim().split(Regex("""\s+""")).mapNotNull { it.toIntOrNull() }
            for (i in 0 until minOf(n, pares.size / 2)) {
                val num = pares[2 * i]
                val ini = primero + pares[2 * i + 1]
                val fin = if (i + 1 < n && 2 * i + 3 < pares.size) primero + pares[2 * i + 3] else t.length
                if (ini in 0..t.length && fin in ini..t.length) comprimidos[num] = t.substring(ini, fin)
            }
        }

        /** Páginas en orden: el árbol desde /Root → /Pages; si falla, los /Type /Page en orden de número. */
        fun paginas(): List<Objeto> {
            val desdeArbol = runCatching {
                val raiz = Regex("""/Root\s+(\d+)\s+\d+\s+R""").findAll(texto).lastOrNull()?.groupValues?.get(1)?.toInt()
                    ?: offsets.keys.plus(comprimidos.keys).firstOrNull { objeto(it)?.dic?.let { d -> Regex("""/Type\s*/Catalog\b""").containsMatchIn(d) } == true }
                val catalogo = raiz?.let { objeto(it) } ?: return@runCatching emptyList()
                val pages = Regex("""/Pages\s+(\d+)\s+\d+\s+R""").find(catalogo.dic)?.groupValues?.get(1)?.toInt() ?: return@runCatching emptyList()
                val salida = mutableListOf<Objeto>()
                recorrer(pages, salida, HashSet())
                salida
            }.getOrDefault(emptyList())
            if (desdeArbol.isNotEmpty()) return desdeArbol
            return (offsets.keys + comprimidos.keys).sorted().mapNotNull { objeto(it) }
                .filter { Regex("""/Type\s*/Page(?![a-zA-Z])""").containsMatchIn(it.dic) }
        }

        private fun recorrer(n: Int, salida: MutableList<Objeto>, vistos: MutableSet<Int>) {
            if (!vistos.add(n) || vistos.size > 10_000) return
            val o = objeto(n) ?: return
            if (Regex("""/Type\s*/Pages\b""").containsMatchIn(o.dic)) {
                val kids = Regex("""/Kids\s*\[([^\]]*)]""").find(o.dic)?.groupValues?.get(1).orEmpty()
                Regex("""(\d+)\s+\d+\s+R""").findAll(kids).forEach { recorrer(it.groupValues[1].toInt(), salida, vistos) }
            } else if (Regex("""/Type\s*/Page(?![a-zA-Z])""").containsMatchIn(o.dic)) {
                salida += o
            }
        }

        /** Content streams de una página (uno o varios), concatenados. */
        fun contenidoDe(pagina: Objeto): ByteArray {
            val valor = Regex("""/Contents\s*(\[[^\]]*]|\d+\s+\d+\s+R)""").find(pagina.dic)?.groupValues?.get(1) ?: return ByteArray(0)
            var refs = Regex("""(\d+)\s+\d+\s+R""").findAll(valor).map { it.groupValues[1].toInt() }.toList()
            // /Contents puede apuntar a un objeto que es un arreglo de streams.
            if (refs.size == 1) objeto(refs[0])?.takeIf { it.inicioStream == null && it.cuerpo.trim().startsWith("[") }?.let { arr ->
                refs = Regex("""(\d+)\s+\d+\s+R""").findAll(arr.cuerpo).map { it.groupValues[1].toInt() }.toList()
            }
            val salida = ByteArrayOutputStream()
            refs.forEach { r -> objeto(r)?.let { o -> datos(o)?.let { salida.write(it); salida.write('\n'.code) } } }
            return salida.toByteArray()
        }
    }

    private fun inflar(datos: ByteArray): ByteArray {
        val inflater = Inflater()
        try {
            inflater.setInput(datos)
            val salida = ByteArrayOutputStream(datos.size * 4)
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                salida.write(buffer, 0, n)
            }
            return salida.toByteArray()
        } catch (e: java.util.zip.DataFormatException) {
            throw PdfIlegible("El PDF está dañado (no se pudo descomprimir su contenido).")
        } finally {
            inflater.end()
        }
    }

    // ------------------------------------------------------------ contenido de una página

    /** Matriz afín [a b c d e f] de PDF. */
    private data class Matriz(val a: Double, val b: Double, val c: Double, val d: Double, val e: Double, val f: Double) {
        /** this × m (primero this, después m), como en la especificación. */
        fun por(m: Matriz) = Matriz(
            a * m.a + b * m.c, a * m.b + b * m.d,
            c * m.a + d * m.c, c * m.b + d * m.d,
            e * m.a + f * m.c + m.e, e * m.b + f * m.d + m.f,
        )

        companion object {
            val IDENTIDAD = Matriz(1.0, 0.0, 0.0, 1.0, 0.0, 0.0)
            fun traslacion(x: Double, y: Double) = Matriz(1.0, 0.0, 0.0, 1.0, x, y)
        }
    }

    private class Interprete(val pagina: Int) {
        private val salida = mutableListOf<TextoPdf>()
        private var ctm = Matriz.IDENTIDAD
        private val pila = ArrayDeque<Matriz>()
        private var tm = Matriz.IDENTIDAD
        private var tlm = Matriz.IDENTIDAD
        private var interlineado = 0.0
        /** Si no se movió el cursor desde el último texto, el siguiente se le pega. */
        private var seMovio = true

        fun correr(contenido: ByteArray): List<TextoPdf> {
            val operandos = mutableListOf<Any>()
            Tokens(contenido).forEach { tok ->
                if (tok is Operador) {
                    ejecutar(tok.nombre, operandos)
                    operandos.clear()
                } else operandos += tok
            }
            return salida
        }

        private fun num(ops: List<Any>, i: Int) = (ops.getOrNull(i) as? Double) ?: 0.0

        private fun ejecutar(op: String, ops: List<Any>) {
            when (op) {
                "q" -> pila.addLast(ctm)
                "Q" -> ctm = pila.removeLastOrNull() ?: Matriz.IDENTIDAD
                "cm" -> if (ops.size >= 6) ctm = Matriz(num(ops, 0), num(ops, 1), num(ops, 2), num(ops, 3), num(ops, 4), num(ops, 5)).por(ctm)
                "BT" -> { tm = Matriz.IDENTIDAD; tlm = Matriz.IDENTIDAD; seMovio = true }
                "Td" -> mover(num(ops, 0), num(ops, 1))
                "TD" -> { interlineado = -num(ops, 1); mover(num(ops, 0), num(ops, 1)) }
                "Tm" -> if (ops.size >= 6) {
                    tlm = Matriz(num(ops, 0), num(ops, 1), num(ops, 2), num(ops, 3), num(ops, 4), num(ops, 5)); tm = tlm; seMovio = true
                }
                "TL" -> interlineado = num(ops, 0)
                "T*" -> mover(0.0, -interlineado)
                "Tj" -> mostrar(ops.lastOrNull() as? Cadena)
                "'" -> { mover(0.0, -interlineado); mostrar(ops.lastOrNull() as? Cadena) }
                "\"" -> { mover(0.0, -interlineado); mostrar(ops.lastOrNull() as? Cadena) }
                "TJ" -> {
                    val arreglo = ops.lastOrNull() as? List<*> ?: return
                    val sb = StringBuilder()
                    arreglo.forEach { e ->
                        when (e) {
                            is Cadena -> sb.append(e.texto)
                            // Un corrimiento grande hacia la derecha separa palabras.
                            is Double -> if (e < -250) sb.append(' ')
                        }
                    }
                    mostrar(Cadena(sb.toString()))
                }
            }
        }

        private fun mover(x: Double, y: Double) {
            tlm = Matriz.traslacion(x, y).por(tlm)
            tm = tlm
            seMovio = true
        }

        private fun mostrar(c: Cadena?) {
            if (c == null || c.texto.isEmpty()) return
            val m = tm.por(ctm)
            val ultimo = salida.lastOrNull()
            if (!seMovio && ultimo != null) salida[salida.lastIndex] = ultimo.copy(texto = ultimo.texto + c.texto)
            else salida += TextoPdf(pagina, m.e, m.f, c.texto)
            seMovio = false
        }
    }

    // ------------------------------------------------------------ tokens

    private class Operador(val nombre: String)
    private class Cadena(val texto: String)
    private class Nombre(val nombre: String)

    /** Tokens de un content stream: números (Double), cadenas, nombres, arreglos (List) y operadores. */
    private class Tokens(val b: ByteArray) : Sequence<Any> {
        override fun iterator(): Iterator<Any> = iterator {
            var i = 0
            val pilaArreglos = ArrayDeque<MutableList<Any>>()
            fun emitir(t: Any): Any? {
                val tope = pilaArreglos.lastOrNull()
                return if (tope != null) { tope += t; null } else t
            }
            while (i < b.size) {
                val ch = b[i].toInt() and 0xFF
                when {
                    ch.toChar().isWhitespace() || ch == 0 -> i++
                    ch == '%'.code -> while (i < b.size && b[i] != '\n'.code.toByte() && b[i] != '\r'.code.toByte()) i++
                    ch == '('.code -> {
                        val (texto, fin) = literal(i)
                        i = fin
                        emitir(Cadena(texto))?.let { yield(it) }
                    }
                    ch == '<'.code && i + 1 < b.size && b[i + 1] == '<'.code.toByte() -> {
                        // Diccionario (por ejemplo, propiedades de contenido marcado): se saltea.
                        var nivel = 0
                        while (i < b.size) {
                            if (b[i] == '<'.code.toByte() && i + 1 < b.size && b[i + 1] == '<'.code.toByte()) { nivel++; i += 2; continue }
                            if (b[i] == '>'.code.toByte() && i + 1 < b.size && b[i + 1] == '>'.code.toByte()) { nivel--; i += 2; if (nivel == 0) break; continue }
                            i++
                        }
                    }
                    ch == '<'.code -> {
                        val fin = b.indexOfFrom('>'.code.toByte(), i).let { if (it < 0) b.size else it }
                        val hex = String(b, i + 1, fin - i - 1, Charsets.ISO_8859_1).filter { it.isLetterOrDigit() }
                        val par = if (hex.length % 2 == 1) hex + "0" else hex
                        val bytes = ByteArray(par.length / 2) { k -> par.substring(2 * k, 2 * k + 2).toInt(16).toByte() }
                        i = fin + 1
                        emitir(Cadena(String(bytes, WIN_ANSI)))?.let { yield(it) }
                    }
                    ch == '['.code -> { pilaArreglos.addLast(mutableListOf()); i++ }
                    ch == ']'.code -> {
                        i++
                        val arr = pilaArreglos.removeLastOrNull() ?: continue
                        emitir(arr)?.let { yield(it) }
                    }
                    ch == '/'.code -> {
                        var j = i + 1
                        while (j < b.size && !esDelimitador(b[j].toInt() and 0xFF)) j++
                        emitir(Nombre(String(b, i + 1, j - i - 1, Charsets.ISO_8859_1)))?.let { yield(it) }
                        i = j
                    }
                    ch == '{'.code || ch == '}'.code || ch == ')'.code || ch == '>'.code -> i++
                    else -> {
                        var j = i
                        while (j < b.size && !esDelimitador(b[j].toInt() and 0xFF)) j++
                        if (j == i) { i++; continue }
                        val palabra = String(b, i, j - i, Charsets.ISO_8859_1)
                        i = j
                        val numero = palabra.toDoubleOrNull()
                        if (numero != null) emitir(numero)?.let { yield(it) }
                        else if (palabra == "BI") {
                            // Imagen en línea: sus datos son binarios, se saltean hasta EI.
                            val fin = Regex("""\sEI(\s|$)""").find(String(b, i, b.size - i, Charsets.ISO_8859_1))
                            i = if (fin == null) b.size else i + fin.range.last + 1
                        } else if (pilaArreglos.isEmpty()) yield(Operador(palabra))
                    }
                }
            }
        }

        private fun esDelimitador(c: Int) = c.toChar().isWhitespace() || c == 0 ||
            c == '('.code || c == ')'.code || c == '<'.code || c == '>'.code || c == '['.code || c == ']'.code ||
            c == '{'.code || c == '}'.code || c == '/'.code || c == '%'.code

        /** Cadena literal (...) con paréntesis anidados y escapes. Devuelve el texto y dónde sigue. */
        private fun literal(desde: Int): Pair<String, Int> {
            val out = ByteArrayOutputStream()
            var i = desde + 1
            var nivel = 1
            while (i < b.size) {
                val c = b[i].toInt() and 0xFF
                when (c) {
                    '\\'.code -> {
                        i++
                        if (i >= b.size) break
                        val e = b[i].toInt() and 0xFF
                        when (e.toChar()) {
                            'n' -> out.write('\n'.code); 'r' -> out.write('\r'.code); 't' -> out.write('\t'.code)
                            'b' -> out.write(8); 'f' -> out.write(12)
                            '(', ')', '\\' -> out.write(e)
                            '\r' -> { if (i + 1 < b.size && b[i + 1] == '\n'.code.toByte()) i++ }
                            '\n' -> Unit
                            in '0'..'7' -> {
                                var v = 0; var k = 0
                                while (k < 3 && i < b.size && (b[i].toInt() and 0xFF).toChar() in '0'..'7') { v = v * 8 + (b[i] - '0'.code.toByte()); i++; k++ }
                                out.write(v and 0xFF)
                                continue
                            }
                            else -> out.write(e)
                        }
                        i++
                    }
                    '('.code -> { nivel++; out.write(c); i++ }
                    ')'.code -> {
                        nivel--
                        i++
                        if (nivel == 0) break
                        out.write(c)
                    }
                    else -> { out.write(c); i++ }
                }
            }
            return String(out.toByteArray(), WIN_ANSI) to i
        }
    }

    private fun ByteArray.indexOfFrom(v: Byte, desde: Int): Int {
        for (k in desde until size) if (this[k] == v) return k
        return -1
    }
}
