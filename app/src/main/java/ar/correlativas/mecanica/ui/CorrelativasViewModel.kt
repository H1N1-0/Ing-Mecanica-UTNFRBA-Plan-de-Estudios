package ar.correlativas.mecanica.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.Plan
import ar.correlativas.mecanica.data.Progreso
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CorrelativasViewModel(app: Application) : AndroidViewModel(app) {
    val plan: Plan = Plan.cargar(app)
    private val progreso = Progreso(app)

    /** null mientras DataStore no terminó de leer. */
    val avance: StateFlow<Avance?> = progreso.estados
        .map { Avance(plan, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun cambiar(id: Int, estado: Estado) {
        viewModelScope.launch { progreso.cambiar(id, estado) }
    }

    fun borrarTodo() {
        viewModelScope.launch { progreso.borrarTodo() }
    }
}
