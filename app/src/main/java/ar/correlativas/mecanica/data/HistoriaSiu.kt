package ar.correlativas.mecanica.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Importar la historia académica del SIU Guaraní (el PDF que se descarga del SIU).
 *
 * 1. [LectorPdf] saca los textos con su posición.
 * 2. [HistoriaSiu.filas] arma la tabla Actividad | Fecha | Tipo | Nota | Resultado: las columnas
 *    se ubican por la x de los encabezados de cada página, y las actividades que ocupan dos
 *    líneas se unen.
 * 3. [ImportacionSiu.armar] busca cada materia (por código SIU o, si no, por nombre), aplica las
 *    reglas y, si una materia tiene varias filas, se queda con el estado más avanzado.
 * 4. [ImportacionSiu.aplicar] calcula el progreso y la libreta resultantes con los cambios elegidos.
 *
 * Todo es local: el PDF lo elige el usuario con el selector del sistema.
 */
data class FilaSiu(
    val pagina: Int,
    /** Texto completo de la columna Actividad, por ejemplo "Estabilidad I (230821)". */
    val actividad: String,
    val nombre: String,
    val codigo: String?,
    val fecha: LocalDate?,
    val tipo: String,
    val nota: Int?,
    val resultado: String,
)

sealed interface LecturaHistoria {
    data class Ok(val filas: List<FilaSiu>) : LecturaHistoria
    data class Error(val motivo: String) : LecturaHistoria
}

object HistoriaSiu {
    private val COLUMNAS = listOf("actividad", "fecha", "tipo", "nota", "resultado")
    private val FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val CODIGO = Regex("""^(.*?)\s*\((\d{3,})\)\s*$""")

    /** Lee el PDF; si no es una historia académica del SIU, un error claro (y no cambia nada). */
    fun leer(bytes: ByteArray): LecturaHistoria {
        val textos = try {
            LectorPdf.textos(bytes)
        } catch (e: PdfIlegible) {
            return LecturaHistoria.Error(e.message ?: "No se pudo leer el PDF.")
        } catch (e: RuntimeException) {
            return LecturaHistoria.Error("No se pudo leer el PDF.")
        }
        val filas = filas(textos) ?: return LecturaHistoria.Error(
            "Este PDF no parece una historia académica del SIU Guaraní: no tiene la tabla " +
                "Actividad / Fecha / Tipo / Nota / Resultado. Descargala desde el SIU (Reportes → Historia académica) y probá de nuevo.",
        )
        return LecturaHistoria.Ok(filas)
    }

    /** Un renglón de una página: los textos que comparten la misma altura. */
    private class Renglon(val y: Double, val textos: MutableList<TextoPdf>)

    /**
     * Filas de la tabla. null si ninguna página tiene el encabezado (no es una historia del SIU).
     * Las páginas sin encabezado se ignoran.
     */
    fun filas(textos: List<TextoPdf>): List<FilaSiu>? {
        var hayEncabezado = false
        val filas = mutableListOf<FilaSiu>()
        textos.groupBy { it.pagina }.toSortedMap().forEach { (pagina, deLaPagina) ->
            val renglones = renglones(deLaPagina)
            val iEncabezado = renglones.indexOfFirst { r -> COLUMNAS.all { c -> r.textos.any { normalizar(it.texto.trim()) == c } } }
            if (iEncabezado < 0) return@forEach
            hayEncabezado = true
            val encabezado = renglones[iEncabezado]
            val xColumnas = COLUMNAS.map { c -> encabezado.textos.first { normalizar(it.texto.trim()) == c }.x }
            val ordenX = xColumnas.sorted()
            fun columna(x: Double): Int {
                // La última columna cuyo encabezado empieza antes (con un margen) que el texto.
                val k = ordenX.indexOfLast { it <= x + 3.0 }
                return if (k < 0) -1 else xColumnas.indexOf(ordenX[k])
            }

            val delaPagina = mutableListOf<FilaSiu>()
            var prefijo: String? = null
            renglones.drop(iEncabezado + 1).forEach { r ->
                val celdas = Array(COLUMNAS.size) { StringBuilder() }
                r.textos.sortedBy { it.x }.forEach { t ->
                    val c = columna(t.x)
                    if (c >= 0) celdas[c].let { if (it.isNotEmpty()) it.append(' '); it.append(t.texto.trim()) }
                }
                val (actividad, fechaTxt, tipo, notaTxt, resultado) = celdas.map { it.toString().trim() }
                val fecha = runCatching { LocalDate.parse(fechaTxt, FECHA) }.getOrNull()
                val soloActividad = actividad.isNotEmpty() && listOf(fechaTxt, tipo, notaTxt, resultado).all { it.isEmpty() }
                when {
                    actividad.isNotEmpty() && fecha != null -> {
                        val completa = listOfNotNull(prefijo, actividad).joinToString(" ")
                        prefijo = null
                        delaPagina += fila(pagina, completa, fecha, tipo, notaTxt, resultado)
                    }
                    soloActividad -> {
                        // Segunda línea de un nombre largo: si a la fila de arriba le falta el código,
                        // es su continuación; si no, es el comienzo del nombre de la fila de abajo.
                        val anterior = delaPagina.lastOrNull()
                        if (anterior != null && anterior.codigo == null) {
                            delaPagina[delaPagina.lastIndex] = fila(pagina, anterior.actividad + " " + actividad, anterior.fecha, anterior.tipo, anterior.nota?.toString().orEmpty(), anterior.resultado)
                        } else {
                            prefijo = listOfNotNull(prefijo, actividad).joinToString(" ")
                        }
                    }
                    // Pie de página, número de página, etc.: no son filas.
                    else -> Unit
                }
            }
            filas += delaPagina
        }
        return if (hayEncabezado) filas else null
    }

    private fun renglones(textos: List<TextoPdf>): List<Renglon> {
        val renglones = mutableListOf<Renglon>()
        textos.sortedByDescending { it.y }.forEach { t ->
            val r = renglones.lastOrNull()
            if (r != null && abs(r.y - t.y) <= 2.0) r.textos += t else renglones += Renglon(t.y, mutableListOf(t))
        }
        return renglones
    }

    private fun fila(pagina: Int, actividad: String, fecha: LocalDate?, tipo: String, notaTxt: String, resultado: String): FilaSiu {
        val limpia = actividad.replace(Regex("""\s+"""), " ").trim()
        val m = CODIGO.find(limpia)
        return FilaSiu(
            pagina = pagina,
            actividad = limpia,
            nombre = m?.groupValues?.get(1)?.trim() ?: limpia,
            codigo = m?.groupValues?.get(2),
            fecha = fecha,
            tipo = tipo,
            nota = notaTxt.trim().replace(',', '.').toDoubleOrNull()?.let { Math.round(it).toInt() },
            resultado = resultado,
        )
    }
}

// ---------------------------------------------------------------- mapeo a la app

/** Qué dice una fila, en términos de la app. */
data class LecturaFila(
    /** Estado al que lleva la fila; null si no cambia el estado (por ejemplo, una regularidad reprobada). */
    val estado: Estado?,
    /** Un examen reprobado: se guarda como aplazo. */
    val aplazo: Boolean = false,
    /** Si la fila no se pudo entender, por qué. */
    val desconocida: String? = null,
)

/** Una fila del PDF que no se pudo usar, con el motivo. */
data class NoReconocida(val fila: FilaSiu, val motivo: String)

/** Lo que la importación propone para una materia. */
data class CambioSiu(
    val materia: Materia,
    val actual: Estado,
    val nuevo: Estado,
    /** Nota de la fila ganadora (final, promoción o, si es regularidad, la de la cursada). */
    val nota: Int?,
    val fecha: LocalDate?,
    /** La nota se guarda en la libreta (final aprobado, promoción o equivalencia con nota). */
    val guardaNota: Boolean,
    /** Aplazos nuevos (examen reprobado con nota 1 a 5 que todavía no está en la libreta). */
    val aplazos: List<Aplazo>,
    val filas: List<FilaSiu>,
    /** La nota (y fecha) difiere de la que ya está en la libreta. */
    val notaNueva: Boolean = true,
) {
    /** Bajaría el estado (por ejemplo, de Aprobada a Regularizada): se destaca y va desmarcado. */
    val baja: Boolean get() = ImportacionSiu.rango(nuevo) < ImportacionSiu.rango(actual)

    val cambiaEstado: Boolean get() = nuevo != actual

    /** Hay algo para aplicar: estado, nota o aplazos. */
    val tieneCambios: Boolean get() = cambiaEstado || aplazos.isNotEmpty() || (guardaNota && nota != null && notaNueva)

    val marcadoPorDefecto: Boolean get() = tieneCambios && !baja
}

data class ImportacionSiu(val cambios: List<CambioSiu>, val noReconocidas: List<NoReconocida>) {
    val conCambios: List<CambioSiu> get() = cambios.filter { it.tieneCambios }
    val sinCambios: List<CambioSiu> get() = cambios.filterNot { it.tieneCambios }

    companion object {
        /** Orden de avance de los estados: cuál gana entre varias filas y qué cambio es una baja. */
        fun rango(e: Estado): Int = when (e) {
            Estado.SIN_CURSAR -> 0
            Estado.CURSANDO -> 1
            Estado.REGULARIZADA -> 2
            Estado.APROBADA -> 3
            Estado.PROMOCIONADA -> 4
        }

        /**
         * Reglas (Tipo y Resultado, sin tildes ni mayúsculas):
         *  - Regularidad Aprobada → Regularizada; Regularidad Reprobada (o ausente) → no cambia.
         *  - Examen Aprobado → Aprobada con la nota del final; Examen Reprobado → aplazo.
         *  - Promoción (Promocionado o Aprobado) → Promocionada con su nota.
         *  - En curso → Cursando.
         *  - Equivalencia aprobada → Aprobada.
         */
        fun leer(f: FilaSiu): LecturaFila {
            val tipo = normalizar(f.tipo).trim()
            val res = normalizar(f.resultado).trim()
            val aprobado = res.startsWith("aprob") || res.startsWith("promocion")
            val reprobado = res.startsWith("reprob") || res.startsWith("desaprob") || res.startsWith("insuf")
            return when {
                tipo.startsWith("en curso") || tipo == "cursando" -> LecturaFila(Estado.CURSANDO)
                tipo.startsWith("regularidad") || tipo.startsWith("cursada") -> when {
                    aprobado -> LecturaFila(Estado.REGULARIZADA)
                    else -> LecturaFila(null)
                }
                tipo.startsWith("promocion") -> if (aprobado) LecturaFila(Estado.PROMOCIONADA) else LecturaFila(null)
                tipo.startsWith("examen") || tipo.startsWith("final") -> when {
                    aprobado -> LecturaFila(Estado.APROBADA)
                    reprobado -> LecturaFila(null, aplazo = true)
                    else -> LecturaFila(null)
                }
                tipo.startsWith("equivalencia") -> if (aprobado || res.isEmpty()) LecturaFila(Estado.APROBADA) else LecturaFila(null)
                else -> LecturaFila(null, desconocida = "Tipo «${f.tipo}» desconocido")
            }
        }

        /** Busca la materia: primero por código SIU y, si no aparece, por nombre (sin tildes ni mayúsculas). */
        fun buscar(plan: Plan, f: FilaSiu): Materia? {
            f.codigo?.let { c -> plan.todas.firstOrNull { it.codigoSiu == c }?.let { return it } }
            val nombre = normalizar(f.nombre).replace(Regex("""\s+"""), " ").trim()
            return plan.todas.firstOrNull { normalizar(it.nombre).replace(Regex("""\s+"""), " ").trim() == nombre }
        }

        fun armar(filas: List<FilaSiu>, avance: Avance, libreta: Libreta): ImportacionSiu {
            val plan = avance.plan
            val noReconocidas = mutableListOf<NoReconocida>()
            val porMateria = LinkedHashMap<Int, MutableList<Pair<FilaSiu, LecturaFila>>>()
            filas.forEach { f ->
                val m = buscar(plan, f)
                val l = leer(f)
                when {
                    m == null -> noReconocidas += NoReconocida(f, if (f.codigo != null) "No está en el plan (código ${f.codigo})" else "No está en el plan")
                    l.desconocida != null -> noReconocidas += NoReconocida(f, l.desconocida)
                    else -> porMateria.getOrPut(m.id) { mutableListOf() } += f to l
                }
            }
            var proximoAplazo = (libreta.aplazos.maxOfOrNull { it.id } ?: 0L) + 1
            val cambios = porMateria.map { (id, lecturas) ->
                val m = plan[id]!!
                val actual = avance.estado(id)
                // Gana el estado más avanzado; a igual estado, la fila más reciente.
                val ganadora = lecturas.filter { it.second.estado != null }
                    .maxWithOrNull(compareBy<Pair<FilaSiu, LecturaFila>>({ rango(it.second.estado!!) }, { it.first.fecha ?: LocalDate.MIN }))
                val nuevo = ganadora?.second?.estado ?: actual
                val nota = ganadora?.first?.nota?.takeIf { it in NOTA_MIN..NOTA_MAX }
                val guardaNota = nuevo == Estado.APROBADA || nuevo == Estado.PROMOCIONADA
                val aplazos = lecturas.filter { it.second.aplazo }.mapNotNull { (f, _) ->
                    val n = f.nota?.takeIf { it in NOTA_MIN..APLAZO_MAX } ?: return@mapNotNull null
                    val repetido = libreta.aplazos.any { it.materia == id && it.nota == n && it.fecha == f.fecha }
                    if (repetido) null else Aplazo(proximoAplazo++, id, n, f.fecha)
                }.distinctBy { it.nota to it.fecha }
                CambioSiu(
                    materia = m,
                    actual = actual,
                    nuevo = nuevo,
                    nota = nota,
                    fecha = ganadora?.first?.fecha,
                    guardaNota = guardaNota && m.tipo != Tipo.PPS,
                    aplazos = aplazos,
                    filas = lecturas.map { it.first },
                    notaNueva = nota != null && libreta.finales[id].let { g -> g?.nota != nota || g.fecha != ganadora?.first?.fecha },
                )
            }.sortedWith(compareBy({ it.materia.nivel ?: 99 }, { it.materia.id }))
            return ImportacionSiu(cambios, noReconocidas)
        }

        /** Progreso y libreta después de aplicar los cambios elegidos (el resto queda igual). */
        fun aplicar(elegidos: List<CambioSiu>, estados: Map<Int, Estado>, libreta: Libreta): Pair<Map<Int, Estado>, Libreta> {
            val nuevosEstados = estados.toMutableMap()
            var nueva = libreta
            elegidos.forEach { c ->
                nuevosEstados[c.materia.id] = c.nuevo
                if (c.guardaNota && c.nota != null) nueva = nueva.conFinal(c.materia.id, NotaFinal(c.nota, c.fecha))
                c.aplazos.forEach { a ->
                    val id = (nueva.aplazos.maxOfOrNull { it.id } ?: 0L) + 1
                    nueva = nueva.conAplazo(a.copy(id = id))
                }
            }
            return nuevosEstados.filterValues { it != Estado.SIN_CURSAR } to nueva
        }
    }
}
