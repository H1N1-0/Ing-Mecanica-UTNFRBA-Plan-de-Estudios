package ar.correlativas.mecanica.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class Tipo { OBLIGATORIA, ELECTIVA, PPS }

/** Correlativas de una instancia (cursar o rendir). */
data class Requisitos(
    val regularizadas: List<Int> = emptyList(),
    val aprobadas: List<Int> = emptyList(),
) {
    val vacio get() = regularizadas.isEmpty() && aprobadas.isEmpty()
}

data class Materia(
    val id: Int,
    val nombre: String,
    val nivel: Int?,
    val tipo: Tipo,
    val cursar: Requisitos,
    /** null cuando la materia no se rinde (PPS). */
    val rendir: Requisitos?,
    /** Regla especial para rendir (Proyecto Final). */
    val reglaRendir: String? = null,
    val integradora: Boolean = false,
    val horasAnuales: Double? = null,
    val codigoSiu: String? = null,
    val requeridaTituloIntermedio: Boolean = false,
)

data class Plan(
    val carrera: String,
    val facultad: String,
    val plan: String,
    val reglas: Map<String, String>,
    val obligatorias: List<Materia>,
    val electivas: List<Materia>,
    val pps: Materia,
) {
    val todas: List<Materia> = obligatorias + pps + electivas
    private val porId = todas.associateBy { it.id }

    operator fun get(id: Int): Materia? = porId[id]

    companion object {
        const val PROYECTO_FINAL = 40
        const val HORAS_ELECTIVAS_REQUERIDAS = 10.0

        @Volatile private var cache: Plan? = null

        fun cargar(context: Context): Plan = cache ?: synchronized(this) {
            cache ?: context.applicationContext.assets.open("correlativas.json")
                .bufferedReader(Charsets.UTF_8).use { parse(it.readText()) }
                .also { cache = it }
        }

        fun parse(texto: String): Plan {
            val raiz = JSONObject(texto)
            val reglasJson = raiz.getJSONObject("reglas")
            val reglas = reglasJson.keys().asSequence().associateWith { reglasJson.getString(it) }
            val obligatorias = raiz.getJSONArray("obligatorias").objetos().map { materia(it, Tipo.OBLIGATORIA) }
            val electivas = raiz.getJSONArray("electivas").objetos().map { materia(it, Tipo.ELECTIVA) }
            val pps = materia(raiz.getJSONObject("pps"), Tipo.PPS)
            return Plan(
                carrera = raiz.optString("carrera"),
                facultad = raiz.optString("facultad"),
                plan = raiz.optString("plan"),
                reglas = reglas,
                obligatorias = obligatorias,
                electivas = electivas.sortedWith(compareBy<Materia>({ it.nivel ?: 99 }, { it.nombre })),
                pps = pps,
            )
        }

        private fun materia(o: JSONObject, tipo: Tipo): Materia {
            val rendirJson = o.optJSONObject("rendir")
            return Materia(
                id = o.getInt("id"),
                nombre = o.getString("nombre"),
                nivel = if (o.isNull("nivel")) null else o.optInt("nivel"),
                tipo = tipo,
                cursar = requisitos(o.optJSONObject("cursar")),
                rendir = rendirJson?.let { requisitos(it) },
                reglaRendir = rendirJson?.optString("regla")?.takeIf { it.isNotEmpty() },
                integradora = o.optBoolean("integradora", false),
                horasAnuales = if (o.has("horas_anuales") && !o.isNull("horas_anuales")) o.getDouble("horas_anuales") else null,
                codigoSiu = o.optString("codigo_siu").takeIf { it.isNotEmpty() },
                requeridaTituloIntermedio = o.optBoolean("requerida_titulo_intermedio", false),
            )
        }

        private fun requisitos(o: JSONObject?): Requisitos = if (o == null) Requisitos() else Requisitos(
            regularizadas = o.optJSONArray("regularizadas").enteros(),
            aprobadas = o.optJSONArray("aprobadas").enteros(),
        )

        private fun JSONArray?.enteros(): List<Int> =
            if (this == null) emptyList() else List(length()) { getInt(it) }

        private fun JSONArray.objetos(): List<JSONObject> = List(length()) { getJSONObject(it) }
    }
}
