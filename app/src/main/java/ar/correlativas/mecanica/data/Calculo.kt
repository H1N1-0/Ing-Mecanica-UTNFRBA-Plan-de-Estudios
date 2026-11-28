package ar.correlativas.mecanica.data

/**
 * Estado de una materia. PROMOCIONADA es aprobada sin final (por promoción): para las correlativas
 * (cursar y rendir), las horas de electivas y el avance cuenta exactamente igual que APROBADA, y
 * lleva nota obligatoria. Se guarda por nombre en `CodecEstados` ("12:PROMOCIONADA"), con el mismo
 * formato de siempre: agregar un valor no cambia cómo se leen los estados ya guardados.
 */
enum class Estado { SIN_CURSAR, REGULARIZADA, APROBADA, PROMOCIONADA }

/** Aprobada por final o por promoción: así la ven las correlativas. */
val Estado.aprobada: Boolean get() = this == Estado.APROBADA || this == Estado.PROMOCIONADA

/** Qué nivel de una correlativa pide una materia. */
enum class Nivel { REGULARIZADA, APROBADA }

data class Faltante(val id: Int, val pide: Nivel)

data class Habilitacion(val materia: Materia, val paraCursar: Nivel?, val paraRendir: Boolean)

/**
 * Reglas de correlativas.json:
 *  - cursar: las "regularizadas" tienen que estar regularizadas o aprobadas; las "aprobadas", aprobadas.
 *  - rendir: tener la materia regularizada y cumplir "rendir".
 *  - Proyecto Final se rinde con todas las obligatorias aprobadas y 10 hs anuales de electivas aprobadas.
 *  - PPS: se empieza con los mismos requisitos que para cursar Proyecto Final.
 *  - Electivas: horas_anuales null no suma.
 *  - Promocionada cuenta en todo como aprobada (ver [Estado]).
 */
class Avance(val plan: Plan, val estados: Map<Int, Estado>) {

    fun estado(id: Int): Estado = estados[id] ?: Estado.SIN_CURSAR

    private fun cumple(id: Int, pide: Nivel) = when (pide) {
        Nivel.REGULARIZADA -> estado(id) != Estado.SIN_CURSAR
        Nivel.APROBADA -> estado(id).aprobada
    }

    private fun faltantes(r: Requisitos): List<Faltante> =
        r.regularizadas.filterNot { cumple(it, Nivel.REGULARIZADA) }.map { Faltante(it, Nivel.REGULARIZADA) } +
            r.aprobadas.filterNot { cumple(it, Nivel.APROBADA) }.map { Faltante(it, Nivel.APROBADA) }

    val horasElectivas: Double = plan.electivas
        .filter { estado(it.id).aprobada }
        .sumOf { it.horasAnuales ?: 0.0 }

    val aprobadasObligatorias: Int = plan.obligatorias.count { estado(it.id).aprobada }

    fun faltantesCursar(m: Materia): List<Faltante> = faltantes(m.cursar)

    /** Lo que falta para rendir, sin contar la condición de tenerla regularizada. */
    fun faltantesRendir(m: Materia): List<Faltante> = when {
        m.reglaRendir != null -> plan.obligatorias
            .filter { it.id != m.id && !estado(it.id).aprobada }
            .map { Faltante(it.id, Nivel.APROBADA) }
        m.rendir != null -> faltantes(m.rendir)
        else -> emptyList()
    }

    fun faltanHorasParaRendir(m: Materia): Double =
        if (m.reglaRendir != null) (Plan.HORAS_ELECTIVAS_REQUERIDAS - horasElectivas).coerceAtLeast(0.0) else 0.0

    fun puedeCursar(m: Materia): Boolean = estado(m.id) == Estado.SIN_CURSAR && faltantesCursar(m).isEmpty()

    fun puedeRendir(m: Materia): Boolean =
        m.tipo != Tipo.PPS &&
            estado(m.id) == Estado.REGULARIZADA &&
            faltantesRendir(m).isEmpty() &&
            faltanHorasParaRendir(m) == 0.0

    val cursables: List<Materia> = plan.todas.filter { puedeCursar(it) }
    val rendibles: List<Materia> = plan.todas.filter { puedeRendir(it) }

    /** Materias en las que [id] aparece como correlativa. */
    fun habilita(id: Int): List<Habilitacion> {
        val origen = plan[id] ?: return emptyList()
        return plan.todas.mapNotNull { m ->
            if (m.id == id) return@mapNotNull null
            val paraCursar = when (id) {
                in m.cursar.aprobadas -> Nivel.APROBADA
                in m.cursar.regularizadas -> Nivel.REGULARIZADA
                else -> null
            }
            val paraRendir = when {
                m.reglaRendir != null -> origen.tipo == Tipo.OBLIGATORIA
                m.rendir != null -> id in m.rendir.regularizadas || id in m.rendir.aprobadas
                else -> false
            }
            if (paraCursar == null && !paraRendir) null else Habilitacion(m, paraCursar, paraRendir)
        }
    }
}

object CodecEstados {
    fun codificar(estados: Map<Int, Estado>): String = estados
        .filterValues { it != Estado.SIN_CURSAR }
        .entries.sortedBy { it.key }
        .joinToString(",") { "${it.key}:${it.value.name}" }

    fun decodificar(texto: String?): Map<Int, Estado> = texto.orEmpty()
        .split(',')
        .mapNotNull { par ->
            val (id, estado) = par.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
            val e = Estado.entries.firstOrNull { it.name == estado } ?: return@mapNotNull null
            id.toIntOrNull()?.let { it to e }
        }
        .toMap()
}

fun formatearHoras(h: Double): String =
    if (h % 1.0 == 0.0) h.toInt().toString() else h.toString().replace('.', ',')
