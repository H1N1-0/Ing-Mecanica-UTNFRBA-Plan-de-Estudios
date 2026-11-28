package ar.correlativas.mecanica.data

import kotlin.math.min

/** Estado visual de una materia en el juego. */
enum class EstadoNodo { BLOQUEADA, DISPONIBLE, CURSANDO, REGULARIZADA, PARA_RENDIR, APROBADA, PROMOCIONADA }

/** Aprobada por final o por promoción. */
val EstadoNodo.completada: Boolean get() = this == EstadoNodo.APROBADA || this == EstadoNodo.PROMOCIONADA

fun Avance.estadoNodo(m: Materia): EstadoNodo = when (estado(m.id)) {
    Estado.APROBADA -> EstadoNodo.APROBADA
    Estado.PROMOCIONADA -> EstadoNodo.PROMOCIONADA
    Estado.REGULARIZADA -> if (puedeRendir(m)) EstadoNodo.PARA_RENDIR else EstadoNodo.REGULARIZADA
    Estado.SIN_CURSAR -> if (puedeCursar(m)) EstadoNodo.DISPONIBLE else EstadoNodo.BLOQUEADA
    Estado.CURSANDO -> EstadoNodo.CURSANDO
}

// ---------------------------------------------------------------- progreso y rango

/**
 * Avance de la carrera entre 0 y 1. Cuenta 42 unidades: las 40 obligatorias, la PPS
 * y las 10 hs de electivas (proporcional a las horas aprobadas).
 */
val Avance.porcentajeCarrera: Double
    get() {
        val pps = if (estado(plan.pps.id).aprobada) 1.0 else 0.0
        val electivas = min(horasElectivas, Plan.HORAS_ELECTIVAS_REQUERIDAS) / Plan.HORAS_ELECTIVAS_REQUERIDAS
        return (aprobadasObligatorias + pps + electivas) / (plan.obligatorias.size + 2.0)
    }

data class Rango(val numero: Int, val nombre: String, val desde: Double, val lema: String)

val RANGOS = listOf(
    Rango(1, "Ingresante", 0.0, "Recién llegás al taller"),
    Rango(2, "Aprendiz", 0.12, "Ya conocés las herramientas"),
    Rango(3, "Técnico", 0.32, "Las bases están firmes"),
    Rango(4, "Proyectista", 0.55, "Diseñás tus propias piezas"),
    Rango(5, "Casi Ingeniero", 0.80, "La recta final"),
    Rango(6, "Ingeniero Mecánico", 1.0, "Carrera completa"),
)

fun rangoPara(porcentaje: Double): Rango = RANGOS.last { porcentaje >= it.desde - 1e-9 }

fun siguienteRango(r: Rango): Rango? = RANGOS.getOrNull(r.numero)

// ---------------------------------------------------------------- logros

enum class IconoLogro { PASO, FINAL, PROMOCION, NIVEL, DECENA, MITAD, IDIOMA, ENGRANAJE, ELECTIVA, RELOJ, TITULO, PPS, PROYECTO, CORONA, BRUJULA }

data class Logro(
    val id: String,
    val titulo: String,
    val descripcion: String,
    val icono: IconoLogro,
    /** Nivel de la carrera que completa, para la celebración especial. */
    val nivel: Int? = null,
    /** Se consigue haciendo el test vocacional, no con materias. */
    val porTest: Boolean = false,
    /** (hecho, total) según las materias, para mostrar el avance hacia el logro. */
    private val porMaterias: (Avance) -> Pair<Double, Double>,
) {
    /** (hecho, total) para mostrar el avance hacia el logro. */
    fun progreso(a: Avance, testHecho: Boolean = false): Pair<Double, Double> =
        if (porTest) (if (testHecho) 1.0 else 0.0) to 1.0 else porMaterias(a)

    fun cumplido(a: Avance, testHecho: Boolean = false): Boolean = progreso(a, testHecho).let { (h, t) -> h >= t }
}

private fun Avance.aprobada(id: Int) = estado(id).aprobada
private fun Avance.contar(ids: List<Int>) = ids.count { aprobada(it) }.toDouble()

private val NOMBRES_NIVEL = mapOf(
    1 to "Cimientos", 2 to "Engranajes", 3 to "A toda máquina", 4 to "Alta presión", 5 to "Planta completa",
)

const val TECNOLOGIA_MECANICA = 104
const val HORAS_TITULO_INTERMEDIO = 6.0

/** Probabilidad y Estadística, Mecánica Racional y Cálculo Avanzado. */
val NO_PIDE_TITULO_INTERMEDIO = setOf(24, 18, 22)

fun logros(plan: Plan): List<Logro> {
    val idsNivel = (1..5).associateWith { n -> plan.obligatorias.filter { it.nivel == n }.map { it.id } }
    val niveles = (1..5).map { n ->
        Logro(
            "nivel-$n", NOMBRES_NIVEL.getValue(n), "Aprobá todas las materias del nivel $n.",
            IconoLogro.NIVEL, nivel = n,
        ) { a -> a.contar(idsNivel.getValue(n)) to idsNivel.getValue(n).size.toDouble() }
    }
    val integradoras = listOf(6, 15, 23)
    val ingles = listOf(16, 25)
    // Técnico Universitario en Mecánica: niveles 1 a 3 sin Probabilidad y Estadística,
    // Mecánica Racional ni Cálculo Avanzado.
    val tituloIntermedio = (1..3).flatMap { idsNivel.getValue(it) } - NO_PIDE_TITULO_INTERMEDIO
    return listOf(
        Logro("primer-paso", "Primer paso", "Regularizá tu primera materia.", IconoLogro.PASO) { a ->
            (if (a.estados.values.any { it.regularizada }) 1.0 else 0.0) to 1.0
        },
        Logro("primer-final", "Primer final", "Aprobá tu primera materia.", IconoLogro.FINAL) { a ->
            (if (a.estados.values.any { it == Estado.APROBADA }) 1.0 else 0.0) to 1.0
        },
        Logro("primera-promocion", "Primera promoción", "Promocioná tu primera materia (aprobada sin final).", IconoLogro.PROMOCION) { a ->
            (if (a.estados.values.any { it == Estado.PROMOCIONADA }) 1.0 else 0.0) to 1.0
        },
    ) + niveles + listOf(
        Logro("decena", "Decena", "Aprobá 10 materias obligatorias.", IconoLogro.DECENA) { a ->
            min(a.aprobadasObligatorias, 10).toDouble() to 10.0
        },
        Logro("mitad", "Mitad del camino", "Aprobá 20 materias obligatorias.", IconoLogro.MITAD) { a ->
            min(a.aprobadasObligatorias, 20).toDouble() to 20.0
        },
        Logro("ingles", "Bilingüe", "Aprobá Inglés I y II.", IconoLogro.IDIOMA) { a -> a.contar(ingles) to 2.0 },
        Logro("integrador", "Integrador", "Aprobá Ingeniería Mecánica I, II y III.", IconoLogro.ENGRANAJE) { a ->
            a.contar(integradoras) to 3.0
        },
        Logro("primera-electiva", "A mi manera", "Aprobá tu primera electiva.", IconoLogro.ELECTIVA) { a ->
            min(a.contar(plan.electivas.map { it.id }), 1.0) to 1.0
        },
        Logro(
            "titulo-intermedio", "Título intermedio",
            "Niveles 1 a 3 (sin Probabilidad, Mecánica Racional ni Cálculo Avanzado) y 6 hs de electivas con Tecnología Mecánica.", IconoLogro.TITULO,
        ) { a ->
            val hecho = a.contar(tituloIntermedio) + min(a.horasElectivas, HORAS_TITULO_INTERMEDIO) / HORAS_TITULO_INTERMEDIO * 2 +
                (if (a.aprobada(TECNOLOGIA_MECANICA)) 1.0 else 0.0)
            hecho to tituloIntermedio.size + 3.0
        },
        Logro(
            "electivas", "Electivas completas",
            "Sumá ${formatearHoras(Plan.HORAS_ELECTIVAS_REQUERIDAS)} hs anuales de electivas aprobadas.", IconoLogro.RELOJ,
        ) { a -> min(a.horasElectivas, Plan.HORAS_ELECTIVAS_REQUERIDAS) to Plan.HORAS_ELECTIVAS_REQUERIDAS },
        Logro("pps", "En la cancha", "Acreditá la Práctica Profesional Supervisada.", IconoLogro.PPS) { a ->
            (if (a.aprobada(plan.pps.id)) 1.0 else 0.0) to 1.0
        },
        Logro("proyecto-final", "Proyecto Final", "Aprobá el Proyecto Final.", IconoLogro.PROYECTO) { a ->
            (if (a.aprobada(Plan.PROYECTO_FINAL)) 1.0 else 0.0) to 1.0
        },
        Logro("ingeniero", "Ingeniero Mecánico", "Completá toda la carrera.", IconoLogro.CORONA) { a ->
            a.porcentajeCarrera to 1.0
        },
        Logro(
            "test", "Conocete", "Hacé el test «¿Qué ingeniero mecánico sos?».", IconoLogro.BRUJULA,
            porTest = true,
        ) { _ -> 0.0 to 1.0 },
    )
}

// ---------------------------------------------------------------- mapa

/** Posición de una materia en el mapa, en dp, centro del nodo. */
data class NodoMapa(val materia: Materia, val fila: Int, val x: Float, val y: Float)

/** Correlativa para cursar: [desde] habilita a [hasta]. */
data class Arista(val desde: Int, val hasta: Int, val pide: Nivel)

/**
 * Mapa de la carrera: una fila por nivel, la PPS al final del nivel 5. Las aristas son las
 * correlativas para cursar, sin las que ya quedan implicadas por otro camino (reducción
 * transitiva), para que el dibujo se pueda leer.
 */
class Mapa(plan: Plan) {
    val nodos: List<NodoMapa>
    val aristas: List<Arista>
    val ancho: Float
    val alto: Float

    init {
        val filas = (1..5).map { n ->
            plan.obligatorias.filter { it.nivel == n } + if (n == 5) listOf(plan.pps) else emptyList()
        }
        val ids = filas.flatten().map { it.id }.toSet()

        // Aristas directas (solo entre materias del mapa).
        val directas = filas.flatten().flatMap { m ->
            m.cursar.regularizadas.filter { it in ids }.map { Arista(it, m.id, Nivel.REGULARIZADA) } +
                m.cursar.aprobadas.filter { it in ids }.map { Arista(it, m.id, Nivel.APROBADA) }
        }
        val sucesores = directas.groupBy({ it.desde }, { it.hasta })
        fun alcanzaSinDirecta(desde: Int, hasta: Int): Boolean {
            val pila = ArrayDeque(sucesores[desde].orEmpty().filter { it != hasta })
            val vistos = mutableSetOf<Int>()
            while (pila.isNotEmpty()) {
                val v = pila.removeLast()
                if (v == hasta) return true
                if (vistos.add(v)) pila.addAll(sucesores[v].orEmpty())
            }
            return false
        }
        aristas = directas.filterNot { alcanzaSinDirecta(it.desde, it.hasta) }

        // Orden dentro de cada fila por baricentro de sus correlativas, para cruzar menos líneas.
        val columna = mutableMapOf<Int, Double>()
        val ordenadas = filas.mapIndexed { f, fila ->
            val orden = if (f == 0) fila else fila.sortedWith(
                compareBy<Materia>({ m ->
                    val previas = aristas.filter { it.hasta == m.id }.mapNotNull { columna[it.desde] }
                    if (previas.isEmpty()) Double.MAX_VALUE else previas.average()
                }, { it.id }),
            )
            orden.forEachIndexed { i, m -> columna[m.id] = i - (orden.size - 1) / 2.0 }
            orden
        }

        val maxPorFila = ordenadas.maxOf { it.size }
        ancho = MARGEN_X * 2 + maxPorFila * PASO_X
        alto = MARGEN_Y + filas.size * PASO_Y
        nodos = ordenadas.flatMapIndexed { f, fila ->
            fila.map { m ->
                NodoMapa(m, f, ancho / 2 + columna.getValue(m.id).toFloat() * PASO_X, MARGEN_Y + f * PASO_Y + 40f)
            }
        }
    }

    companion object {
        const val PASO_X = 116f
        const val PASO_Y = 200f
        const val MARGEN_X = 24f
        const val MARGEN_Y = 48f
    }
}
