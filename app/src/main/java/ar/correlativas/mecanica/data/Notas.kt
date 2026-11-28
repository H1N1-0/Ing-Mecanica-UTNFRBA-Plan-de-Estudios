package ar.correlativas.mecanica.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/*
 * Notas de finales y aplazos. Van en un DataStore propio ("notas"), aparte del progreso: el
 * progreso ("progreso" / "estados") NO cambia de formato, así que actualizar la app no lo toca.
 * Las notas son opcionales: una materia aprobada sin nota sigue contando como aprobada. La
 * excepción es la promoción: la app pide la nota antes de marcarla (ver CorrelativasViewModel).
 * La nota de una promocionada se guarda igual que la de un final, en `finales`.
 */

const val NOTA_MIN = 1
const val NOTA_MAX = 10

/** Nota de un aplazo: un final desaprobado (en la UTN se aprueba con 6). */
const val APLAZO_MAX = 5

/** Nota y fecha con que se aprobó una materia (por final o por promoción). */
data class NotaFinal(val nota: Int?, val fecha: LocalDate?) {
    val vacia get() = nota == null && fecha == null
}

/** Un final desaprobado, con su nota (1 a 5) y fecha opcional. [id] lo identifica para borrarlo. */
data class Aplazo(val id: Long, val materia: Int, val nota: Int, val fecha: LocalDate?)

/** Todas las notas cargadas. */
data class Libreta(
    val finales: Map<Int, NotaFinal> = emptyMap(),
    val aplazos: List<Aplazo> = emptyList(),
) {
    fun aplazosDe(materia: Int) = aplazos.filter { it.materia == materia }.sortedBy { it.fecha ?: LocalDate.MAX }

    fun conFinal(materia: Int, nf: NotaFinal?): Libreta =
        copy(finales = if (nf == null || nf.vacia) finales - materia else finales + (materia to nf))

    fun conAplazo(a: Aplazo) = copy(aplazos = aplazos + a)

    fun sinAplazo(id: Long) = copy(aplazos = aplazos.filterNot { it.id == id })

    val vacia get() = finales.isEmpty() && aplazos.isEmpty()
}

/**
 * Formato guardado (JSON). [VERSION] permite migrar si algún día cambia:
 * `{"version":1,"finales":{"12":{"nota":8,"fecha":"2025-12-10"}},"aplazos":[{"id":1,"materia":12,"nota":2,"fecha":"2025-07-20"}]}`
 */
object CodecLibreta {
    const val VERSION = 1

    fun aJson(l: Libreta): JSONObject = JSONObject().apply {
        put("version", VERSION)
        put("finales", JSONObject().apply {
            l.finales.toSortedMap().forEach { (id, nf) ->
                put(id.toString(), JSONObject().apply {
                    nf.nota?.let { put("nota", it) }
                    nf.fecha?.let { put("fecha", it.toString()) }
                })
            }
        })
        put("aplazos", JSONArray().apply {
            l.aplazos.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id)
                    put("materia", a.materia)
                    put("nota", a.nota)
                    a.fecha?.let { put("fecha", it.toString()) }
                })
            }
        })
    }

    fun codificar(l: Libreta): String = aJson(l).toString()

    /** Lee una libreta; descarta lo que no sea válido (notas fuera de rango, fechas mal escritas). */
    fun deJson(o: JSONObject?): Libreta {
        if (o == null) return Libreta()
        val finales = mutableMapOf<Int, NotaFinal>()
        o.optJSONObject("finales")?.let { f ->
            f.keys().forEach { k ->
                val id = k.toIntOrNull() ?: return@forEach
                val nf = f.optJSONObject(k) ?: return@forEach
                val nota = nf.optInt("nota", 0).takeIf { it in NOTA_MIN..NOTA_MAX }
                val fecha = fecha(nf.optString("fecha"))
                if (nota != null || fecha != null) finales[id] = NotaFinal(nota, fecha)
            }
        }
        val aplazos = mutableListOf<Aplazo>()
        o.optJSONArray("aplazos")?.let { arr ->
            for (i in 0 until arr.length()) {
                val a = arr.optJSONObject(i) ?: continue
                val materia = a.optInt("materia", -1).takeIf { it > 0 } ?: continue
                val nota = a.optInt("nota", 0).takeIf { it in NOTA_MIN..APLAZO_MAX } ?: continue
                // Ids repetidos o faltantes (por ejemplo, un archivo editado a mano) se renumeran.
                val id = a.optLong("id", 0L).takeIf { it > 0 && aplazos.none { x -> x.id == it } }
                    ?: ((aplazos.maxOfOrNull { it.id } ?: 0L) + 1)
                aplazos += Aplazo(id, materia, nota, fecha(a.optString("fecha")))
            }
        }
        return Libreta(finales, aplazos)
    }

    fun decodificar(texto: String?): Libreta =
        if (texto.isNullOrBlank()) Libreta() else runCatching { deJson(JSONObject(texto)) }.getOrDefault(Libreta())

    fun fecha(s: String?): LocalDate? = s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

private val Context.notasStore by preferencesDataStore(name = "notas")
private val LIBRETA = stringPreferencesKey("libreta")
private val VERSION_NOTAS = intPreferencesKey("version")

/** Notas guardadas en el celular. */
class Notas(context: Context) {
    private val context = context.applicationContext

    val libreta: Flow<Libreta> = this.context.notasStore.data.map { CodecLibreta.decodificar(it[LIBRETA]) }

    private suspend fun cambiar(f: (Libreta) -> Libreta) {
        context.notasStore.edit { p ->
            p[LIBRETA] = CodecLibreta.codificar(f(CodecLibreta.decodificar(p[LIBRETA])))
            p[VERSION_NOTAS] = CodecLibreta.VERSION
        }
    }

    suspend fun guardarFinal(materia: Int, nf: NotaFinal?) = cambiar { it.conFinal(materia, nf) }

    suspend fun agregarAplazo(materia: Int, nota: Int, fecha: LocalDate?) = cambiar { l ->
        l.conAplazo(Aplazo((l.aplazos.maxOfOrNull { it.id } ?: 0L) + 1, materia, nota, fecha))
    }

    suspend fun borrarAplazo(id: Long) = cambiar { it.sinAplazo(id) }

    suspend fun reemplazar(l: Libreta) = cambiar { l }

    suspend fun borrarTodo() {
        context.notasStore.edit { it.remove(LIBRETA) }
    }
}

// ---------------------------------------------------------------- estadísticas

/** Un punto de la evolución: el promedio acumulado al terminar un año. */
data class PuntoPromedio(val anio: Int, val sinAplazos: Double?, val conAplazos: Double?)

data class NotaDeMateria(val materia: Materia, val nota: Int, val fecha: LocalDate?)

/**
 * Estadísticas de la carrera a partir del progreso y las notas.
 * Solo cuentan las notas de materias que hoy están aprobadas (si una materia se desmarca, su nota
 * queda guardada pero no suma) y los aplazos de materias que existen en el plan.
 */
class Estadisticas(val avance: Avance, val libreta: Libreta) {
    private val plan = avance.plan

    /** Materias aprobadas, por final o por promoción (todas menos la PPS, que no lleva nota). */
    val aprobadas: List<Materia> = plan.todas.filter { it.tipo != Tipo.PPS && avance.estado(it.id).aprobada }

    /** Cuántas de las [aprobadas] fueron por promoción y cuántas por final. */
    val porPromocion: Int = aprobadas.count { avance.estado(it.id) == Estado.PROMOCIONADA }
    val porFinal: Int = aprobadas.size - porPromocion

    val notas: List<NotaDeMateria> = aprobadas.mapNotNull { m ->
        libreta.finales[m.id]?.let { nf -> nf.nota?.let { NotaDeMateria(m, it, nf.fecha) } }
    }

    val aplazos: List<Aplazo> = libreta.aplazos.filter { plan[it.materia] != null }

    val promedioSinAplazos: Double? = notas.map { it.nota }.promedio()

    val promedioConAplazos: Double? = (notas.map { it.nota } + aplazos.map { it.nota }).promedio()

    /** Finales rendidos: los aprobados por final (con o sin nota cargada) más los aplazos. Las promociones no son finales. */
    val finalesRendidos: Int = porFinal + aplazos.size

    val mejor: NotaDeMateria? = notas.maxWithOrNull(compareBy<NotaDeMateria> { it.nota }.thenByDescending { it.fecha ?: LocalDate.MAX })

    val peor: NotaDeMateria? = notas.minWithOrNull(compareBy<NotaDeMateria> { it.nota }.thenBy { it.fecha ?: LocalDate.MAX })

    /** Notas y aplazos sin fecha: cuentan para el promedio pero no entran en el gráfico por año. */
    val sinFecha: Int = notas.count { it.fecha == null } + aplazos.count { it.fecha == null }

    /** Promedio acumulado al final de cada año con finales (con y sin aplazos). */
    val evolucion: List<PuntoPromedio> = run {
        val anios = (notas.mapNotNull { it.fecha?.year } + aplazos.mapNotNull { it.fecha?.year }).toSortedSet()
        anios.map { anio ->
            val hasta = notas.filter { it.fecha != null && it.fecha.year <= anio }.map { it.nota }
            val aplazosHasta = aplazos.filter { it.fecha != null && it.fecha.year <= anio }.map { it.nota }
            PuntoPromedio(anio, hasta.promedio(), (hasta + aplazosHasta).promedio())
        }
    }

    val hayDatos: Boolean get() = notas.isNotEmpty() || aplazos.isNotEmpty()
}

private fun List<Int>.promedio(): Double? = if (isEmpty()) null else average()

/** "7,45" (dos decimales, con coma); "—" si no hay promedio. */
fun formatearPromedio(p: Double?): String =
    if (p == null) "—" else String.format(java.util.Locale.ROOT, "%.2f", p).replace('.', ',')
