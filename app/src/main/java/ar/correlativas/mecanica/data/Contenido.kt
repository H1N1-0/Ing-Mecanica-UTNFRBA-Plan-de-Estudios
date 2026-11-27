package ar.correlativas.mecanica.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.floor
import kotlin.random.Random

// ---------------------------------------------------------------- frases

data class Frase(
    val id: Int,
    val texto: String,
    val autor: String,
    val fuente: String,
    /** true si la fuente es verificable; false si es una atribución popular. */
    val verificable: Boolean,
    val nota: String?,
    val materias: List<Int>,
)

class Frases(val todas: List<Frase>) {
    private val verificables = todas.filter { it.verificable }.ifEmpty { todas }
    private val cortas = verificables.filter { it.texto.length <= MAX_WIDGET }.ifEmpty { verificables }

    /** Frase del día: cambia una vez por día, solo entre las de fuente verificable. */
    fun delDia(dia: Long, desplazamiento: Int = 0): Frase =
        verificables[Math.floorMod(dia * 37 + desplazamiento, verificables.size.toLong()).toInt()]

    /** Para el widget: rota cada [HORAS_ROTACION] horas, entre frases cortas. */
    fun paraWidget(horaEpoch: Long): Frase =
        cortas[Math.floorMod((horaEpoch / HORAS_ROTACION) * 53, cortas.size.toLong()).toInt()]

    /** Frase al aprobar una materia: una relacionada si hay, si no una general. */
    fun paraMateria(materiaId: Int, semilla: Long): Frase {
        val relacionadas = todas.filter { materiaId in it.materias }
        val candidatas = relacionadas.ifEmpty { verificables.filter { it.materias.isEmpty() }.ifEmpty { verificables } }
        return candidatas[Random(semilla).nextInt(candidatas.size)]
    }

    companion object {
        const val MAX_WIDGET = 120
        const val HORAS_ROTACION = 3L

        @Volatile private var cache: Frases? = null

        fun cargar(context: Context): Frases = cache ?: synchronized(this) {
            cache ?: parse(leerAsset(context, "frases.json")).also { cache = it }
        }

        fun parse(texto: String): Frases = Frases(
            JSONObject(texto).getJSONArray("frases").objetos().map { o ->
                Frase(
                    id = o.getInt("id"),
                    texto = o.getString("texto"),
                    autor = o.getString("autor"),
                    fuente = o.optString("fuente"),
                    verificable = o.optString("verificacion") == "verificable",
                    nota = o.optString("nota").takeIf { it.isNotEmpty() },
                    materias = o.optJSONArray("materias").enteros(),
                )
            },
        )
    }
}

// ---------------------------------------------------------------- test vocacional

data class Referente(val nombre: String, val descripcion: String)

data class Perfil(
    val id: String,
    val nombre: String,
    val icono: String,
    val color: Long,
    val descripcion: String,
    val trabajo: List<String>,
    val referente: Referente,
    val electivas: List<Int>,
)

data class Opcion(val texto: String, val puntos: Map<String, Int>)

data class Pregunta(val texto: String, val opciones: List<Opcion>)

class TestVocacional(val titulo: String, val perfiles: List<Perfil>, val preguntas: List<Pregunta>) {
    private val porId = perfiles.associateBy { it.id }

    operator fun get(id: String): Perfil? = porId[id]

    /**
     * Porcentaje de cada perfil según las respuestas (índice de opción por pregunta).
     * Los porcentajes suman 100 (método del mayor resto).
     */
    fun calcular(respuestas: List<Int>): Map<String, Int> {
        val puntos = perfiles.associate { it.id to 0.0 }.toMutableMap()
        respuestas.forEachIndexed { i, r ->
            preguntas.getOrNull(i)?.opciones?.getOrNull(r)?.puntos?.forEach { (id, p) ->
                if (id in puntos) puntos[id] = puntos.getValue(id) + p
            }
        }
        val total = puntos.values.sum()
        if (total <= 0.0) return perfiles.associate { it.id to 0 }
        val exactos = puntos.mapValues { it.value * 100.0 / total }
        val base = exactos.mapValues { floor(it.value).toInt() }.toMutableMap()
        var resto = 100 - base.values.sum()
        exactos.entries.sortedByDescending { it.value - floor(it.value) }.forEach { (id, _) ->
            if (resto > 0) { base[id] = base.getValue(id) + 1; resto-- }
        }
        return perfiles.associate { it.id to base.getValue(it.id) }
    }

    companion object {
        @Volatile private var cache: TestVocacional? = null

        fun cargar(context: Context): TestVocacional = cache ?: synchronized(this) {
            cache ?: parse(leerAsset(context, "perfiles.json")).also { cache = it }
        }

        fun parse(texto: String): TestVocacional {
            val raiz = JSONObject(texto)
            val perfiles = raiz.getJSONArray("perfiles").objetos().map { o ->
                val ref = o.getJSONObject("referente")
                Perfil(
                    id = o.getString("id"),
                    nombre = o.getString("nombre"),
                    icono = o.optString("icono", "engranaje"),
                    color = parseColor(o.optString("color", "#22E1F0")),
                    descripcion = o.getString("descripcion"),
                    trabajo = o.optJSONArray("trabajo").textos(),
                    referente = Referente(ref.getString("nombre"), ref.getString("descripcion")),
                    electivas = o.optJSONArray("electivas").enteros(),
                )
            }
            val preguntas = raiz.getJSONArray("preguntas").objetos().map { p ->
                Pregunta(
                    texto = p.getString("texto"),
                    opciones = p.getJSONArray("opciones").objetos().map { op ->
                        val pts = op.getJSONObject("puntos")
                        Opcion(op.getString("texto"), pts.keys().asSequence().associateWith { pts.getInt(it) })
                    },
                )
            }
            return TestVocacional(raiz.optString("titulo", "¿Qué ingeniero mecánico sos?"), perfiles, preguntas)
        }

        private fun parseColor(hex: String): Long {
            val limpio = hex.removePrefix("#")
            val argb = if (limpio.length == 6) "FF$limpio" else limpio
            return argb.toLongOrNull(16) ?: 0xFF22E1F0
        }
    }
}

/** Resultado guardado del test. */
data class ResultadoPerfil(val porcentajes: Map<String, Int>, val fecha: Long, val veces: Int) {
    val ordenados: List<Pair<String, Int>> get() = porcentajes.toList().sortedByDescending { it.second }
    val principal: String get() = ordenados.first().first

    fun codificar(): String = porcentajes.entries.joinToString(",") { "${it.key}:${it.value}" }

    companion object {
        fun decodificar(texto: String?, fecha: Long, veces: Int): ResultadoPerfil? {
            val mapa = texto.orEmpty().split(',').mapNotNull { par ->
                val (k, v) = par.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
                v.toIntOrNull()?.let { k to it }
            }.toMap()
            return if (mapa.isEmpty()) null else ResultadoPerfil(mapa, fecha, veces)
        }
    }
}

// ---------------------------------------------------------------- utilidades

private fun leerAsset(context: Context, nombre: String): String =
    context.applicationContext.assets.open(nombre).bufferedReader(Charsets.UTF_8).use { it.readText() }

private fun JSONArray.objetos(): List<JSONObject> = List(length()) { getJSONObject(it) }

private fun JSONArray?.enteros(): List<Int> = if (this == null) emptyList() else List(length()) { getInt(it) }

private fun JSONArray?.textos(): List<String> = if (this == null) emptyList() else List(length()) { getString(it) }
