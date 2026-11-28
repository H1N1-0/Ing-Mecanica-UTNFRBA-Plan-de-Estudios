package ar.correlativas.mecanica.data

/**
 * El plan como grafo de dependencias, igual que en planificación de proyectos.
 *
 * Nodos: las obligatorias y la PPS (las electivas no traban nada del plan).
 * Solo lee las correlativas de [Plan]: no cambia ninguna regla de [Avance].
 *
 *  - Dependencias para "trabar": B depende de A si A está en sus correlativas para cursar o
 *    para rendir. Proyecto Final depende además de todas las obligatorias (se rinde con todas aprobadas).
 *  - Dependencias para el tiempo: solo las de cursar. Cada materia sin cursar es un paso de una
 *    cursada (la mayoría son anuales); las regularizadas y aprobadas ya no suman cursadas.
 */
class GrafoPlan(val plan: Plan) {
    val ids: List<Int> = (plan.obligatorias + plan.pps).map { it.id }
    private val enGrafo = ids.toSet()

    /** Correlativas para cursar de cada materia (dentro del grafo). */
    val previasCursar: Map<Int, List<Int>> = ids.associateWith { id ->
        val m = plan[id]!!
        (m.cursar.regularizadas + m.cursar.aprobadas).filter { it in enGrafo }.distinct()
    }

    /** Materias que piden a cada una para cursar. */
    val siguientesCursar: Map<Int, List<Int>> = invertir(previasCursar)

    /** Todo lo que cada materia necesita (cursar, rendir y la regla de Proyecto Final). */
    val dependeDe: Map<Int, List<Int>> = ids.associateWith { id ->
        val m = plan[id]!!
        val rendir = when {
            m.reglaRendir != null -> plan.obligatorias.map { it.id }
            m.rendir != null -> m.rendir.regularizadas + m.rendir.aprobadas
            else -> emptyList()
        }
        (previasCursar.getValue(id) + rendir).filter { it in enGrafo && it != id }.distinct()
    }

    /** Todas las materias que dependen de cada una, directa o indirectamente. */
    val dependientes: Map<Int, Set<Int>> = invertir(dependeDe).let { directos ->
        ids.associateWith { id ->
            val vistos = linkedSetOf<Int>()
            val pila = ArrayDeque(directos[id].orEmpty())
            while (pila.isNotEmpty()) {
                val v = pila.removeLast()
                if (vistos.add(v)) pila.addAll(directos[v].orEmpty())
            }
            vistos
        }
    }

    private fun invertir(m: Map<Int, List<Int>>): Map<Int, List<Int>> {
        val r = ids.associateWith { mutableListOf<Int>() }
        m.forEach { (hasta, desdes) -> desdes.forEach { r.getValue(it).add(hasta) } }
        return r.mapValues { (_, v) -> v.sorted() }
    }
}

/** Una materia recomendada en la tarjeta "Prioridad". */
data class Prioridad(
    val materia: Materia,
    /** Materias pendientes que dependen de esta (todas las que se traban si no la aprobás). */
    val traba: Int,
    /** Materias que se podrían cursar ya mismo si la aprobaras. */
    val abreYa: Int,
    val enCaminoCritico: Boolean,
)

/**
 * Camino crítico de la carrera para un [Avance]. Se calcula de cero cada vez que cambia el
 * progreso (es un grafo de 41 nodos).
 */
class CaminoCritico(val avance: Avance, val grafo: GrafoPlan = GrafoPlan(avance.plan)) {
    private val plan = avance.plan

    private fun pendiente(id: Int) = !avance.estado(id).aprobada

    /** Materias sin cursar: cada una es una cursada que falta. */
    // Cursando cuenta como sin cursar: todavía falta terminar esa cursada.
    private val sinCursar: Set<Int> = grafo.ids.filter { !avance.estado(it).regularizada }.toSet()

    /** Para cada materia pendiente, cuántas materias pendientes traba (directa o indirectamente). */
    val traba: Map<Int, Int> = grafo.ids.filter { pendiente(it) }.associateWith { id ->
        grafo.dependientes.getValue(id).count { pendiente(it) }
    }

    fun trabaDe(id: Int): Int = traba[id] ?: 0

    /** Cursadas en la cadena más larga que empieza en cada materia sin cursar (ella incluida). */
    private val hastaElFinal: Map<Int, Int> = run {
        val memo = mutableMapOf<Int, Int>()
        fun largo(id: Int): Int = memo[id] ?: (1 + (grafo.siguientesCursar.getValue(id)
            .filter { it in sinCursar }.maxOfOrNull { largo(it) } ?: 0)).also { memo[id] = it }
        sinCursar.forEach { largo(it) }
        memo
    }

    /** Cursadas en la cadena más larga que termina en cada materia sin cursar (ella incluida). */
    private val desdeAhora: Map<Int, Int> = run {
        val memo = mutableMapOf<Int, Int>()
        fun largo(id: Int): Int = memo[id] ?: (1 + (grafo.previasCursar.getValue(id)
            .filter { it in sinCursar }.maxOfOrNull { largo(it) } ?: 0)).also { memo[id] = it }
        sinCursar.forEach { largo(it) }
        memo
    }

    /** Mínimo de cursadas (años, si son anuales) que faltan para recibirse. */
    val anios: Int = hastaElFinal.values.maxOrNull() ?: 0

    /**
     * Cuántas cursadas se puede atrasar una materia sin cursar sin atrasar la carrera.
     * 0 = no tiene margen. null para las que ya se cursaron.
     */
    fun holgura(id: Int): Int? {
        val h = hastaElFinal[id] ?: return null
        return anios - (desdeAhora.getValue(id) + h - 1)
    }

    /** Materias desde las que se llega a cursar Proyecto Final por correlativas para cursar. */
    private val llegaAlProyecto: Set<Int> = buildSet {
        val pila = ArrayDeque(listOf(Plan.PROYECTO_FINAL))
        while (pila.isNotEmpty()) {
            val v = pila.removeLast()
            if (add(v)) pila.addAll(grafo.previasCursar[v].orEmpty())
        }
    }

    /**
     * La cadena más larga de materias sin cursar, de la próxima cursada a la última. Si hay
     * varias igual de largas, prefiere la que termina en Proyecto Final y la que pasa por las
     * materias que más traban.
     */
    val camino: List<Materia> = run {
        val orden = compareByDescending<Int> { it in llegaAlProyecto }
            .thenByDescending { trabaDe(it) }
            .thenBy { it }
        val inicio = hastaElFinal.filterValues { it == anios }.keys.sortedWith(orden).firstOrNull()
        generateSequence(inicio) { actual ->
            val falta = hastaElFinal.getValue(actual) - 1
            grafo.siguientesCursar.getValue(actual)
                .filter { hastaElFinal[it] == falta }
                .sortedWith(orden)
                .firstOrNull()
        }.mapNotNull { plan[it] }.toList()
    }

    val idsCamino: Set<Int> = camino.map { it.id }.toSet()

    fun enCamino(id: Int) = id in idsCamino

    /**
     * Cuellos de botella: las materias pendientes que más traban (las 5 primeras, con empates),
     * siempre que traben al menos [MIN_CUELLO].
     */
    val cuellos: Set<Int> = run {
        val orden = traba.filterValues { it >= MIN_CUELLO }.entries.sortedByDescending { it.value }
        val corte = orden.getOrNull(MAX_CUELLOS - 1)?.value ?: 0
        orden.filter { it.value >= corte }.map { it.key }.toSet()
    }

    fun esCuello(id: Int) = id in cuellos

    /**
     * Las materias que más conviene aprobar ahora: las que se pueden cursar o rendir ya,
     * ordenadas por cuántas traban; a igualdad, primero las del camino crítico.
     */
    fun prioridades(cantidad: Int = 3): List<Prioridad> {
        val cursables = avance.cursables.map { it.id }.toSet()
        return grafo.ids
            .filter { pendiente(it) }
            .mapNotNull { plan[it] }
            .filter { avance.puedeCursar(it) || avance.puedeRendir(it) }
            .sortedWith(
                compareByDescending<Materia> { trabaDe(it.id) }
                    .thenByDescending { enCamino(it.id) }
                    .thenBy { holgura(it.id) ?: Int.MAX_VALUE }
                    .thenBy { it.id },
            )
            .take(cantidad)
            .map { m ->
                val aprobada = Avance(plan, avance.estados + (m.id to Estado.APROBADA))
                Prioridad(
                    materia = m,
                    traba = trabaDe(m.id),
                    abreYa = aprobada.cursables.count { it.id !in cursables && it.id != m.id },
                    enCaminoCritico = enCamino(m.id),
                )
            }
    }

    companion object {
        const val MAX_CUELLOS = 5
        const val MIN_CUELLO = 4
    }
}
