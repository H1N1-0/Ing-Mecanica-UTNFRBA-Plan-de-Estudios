package ar.correlativas.mecanica.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.Logro
import ar.correlativas.mecanica.data.Mapa
import ar.correlativas.mecanica.data.Materia
import ar.correlativas.mecanica.data.Plan
import ar.correlativas.mecanica.data.Progreso
import ar.correlativas.mecanica.data.logros
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Lo que pasó al cambiar una materia, para la animación de desbloqueo. */
data class Desbloqueo(
    val materia: Materia,
    val materias: List<Materia>,
    val finales: List<Materia>,
    val logros: List<Logro>,
) {
    val nivelesCompletos: List<Int> get() = logros.mapNotNull { it.nivel }
    val vacio get() = materias.isEmpty() && finales.isEmpty() && logros.isEmpty()
}

/** Qué cambió entre dos avances: materias y finales nuevos, y logros conseguidos. */
fun calcularDesbloqueo(materia: Materia, antes: Avance, despues: Avance, todosLosLogros: List<Logro>): Desbloqueo {
    val cursablesAntes = antes.cursables.map { it.id }.toSet()
    val rendiblesAntes = antes.rendibles.map { it.id }.toSet()
    return Desbloqueo(
        materia = materia,
        materias = despues.cursables.filter { it.id !in cursablesAntes && it.id != materia.id },
        finales = despues.rendibles.filter { it.id !in rendiblesAntes && it.id != materia.id },
        logros = todosLosLogros.filter { it.cumplido(despues) && !it.cumplido(antes) },
    )
}

class CorrelativasViewModel(app: Application) : AndroidViewModel(app) {
    val plan: Plan = Plan.cargar(app)
    val mapa: Mapa = Mapa(plan)
    val logros: List<Logro> = logros(plan)
    private val progreso = Progreso(app)

    /** null mientras DataStore no terminó de leer. */
    val avance: StateFlow<Avance?> = progreso.estados
        .map { Avance(plan, it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _desbloqueo = MutableStateFlow<Desbloqueo?>(null)
    val desbloqueo: StateFlow<Desbloqueo?> = _desbloqueo.asStateFlow()

    fun cambiar(id: Int, estado: Estado) {
        val materia = plan[id] ?: return
        viewModelScope.launch {
            val antes = Avance(plan, progreso.estados.first())
            if (antes.estado(id) == estado) return@launch
            progreso.cambiar(id, estado)
            val despues = Avance(plan, progreso.estados.first())
            val d = calcularDesbloqueo(materia, antes, despues, logros)
            if (!d.vacio) _desbloqueo.value = d
        }
    }

    fun cerrarDesbloqueo() {
        _desbloqueo.value = null
    }

    fun borrarTodo() {
        viewModelScope.launch { progreso.borrarTodo() }
    }
}
