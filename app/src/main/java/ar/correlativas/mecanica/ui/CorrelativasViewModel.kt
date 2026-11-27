package ar.correlativas.mecanica.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ar.correlativas.mecanica.data.Ajustes
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.CaminoCritico
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.Frase
import ar.correlativas.mecanica.data.Frases
import ar.correlativas.mecanica.data.GrafoPlan
import ar.correlativas.mecanica.data.Logro
import ar.correlativas.mecanica.data.Mapa
import ar.correlativas.mecanica.data.Materia
import ar.correlativas.mecanica.data.ModoTema
import ar.correlativas.mecanica.data.Plan
import ar.correlativas.mecanica.data.Preferencias
import ar.correlativas.mecanica.data.Progreso
import ar.correlativas.mecanica.data.TestVocacional
import ar.correlativas.mecanica.data.logros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
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
    /** Frase para acompañar una materia aprobada. */
    val frase: Frase? = null,
) {
    val nivelesCompletos: List<Int> get() = logros.mapNotNull { it.nivel }
    val vacio get() = materias.isEmpty() && finales.isEmpty() && logros.isEmpty() && frase == null
}

/** Qué cambió entre dos avances: materias y finales nuevos, y logros conseguidos. */
fun calcularDesbloqueo(
    materia: Materia,
    antes: Avance,
    despues: Avance,
    todosLosLogros: List<Logro>,
    testHecho: Boolean = false,
): Desbloqueo {
    val cursablesAntes = antes.cursables.map { it.id }.toSet()
    val rendiblesAntes = antes.rendibles.map { it.id }.toSet()
    return Desbloqueo(
        materia = materia,
        materias = despues.cursables.filter { it.id !in cursablesAntes && it.id != materia.id },
        finales = despues.rendibles.filter { it.id !in rendiblesAntes && it.id != materia.id },
        logros = todosLosLogros.filter { it.cumplido(despues, testHecho) && !it.cumplido(antes, testHecho) },
    )
}

class CorrelativasViewModel(app: Application) : AndroidViewModel(app) {
    val plan: Plan = Plan.cargar(app)
    val mapa: Mapa = Mapa(plan)
    val grafo: GrafoPlan = GrafoPlan(plan)
    val logros: List<Logro> = logros(plan)
    val frases: Frases = Frases.cargar(app)
    val test: TestVocacional = TestVocacional.cargar(app)
    private val progreso = Progreso(app)
    private val ajustes = Ajustes(app)

    /** null mientras DataStore no terminó de leer. */
    val avance: StateFlow<Avance?> = progreso.estados
        .distinctUntilChanged()
        .map { Avance(plan, it) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Camino crítico: se recalcula solo cada vez que cambia el avance. */
    val critico: StateFlow<CaminoCritico?> = avance
        .map { a -> a?.let { CaminoCritico(it, grafo) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val preferencias: StateFlow<Preferencias?> = ajustes.preferencias
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
            val testHecho = preferencias.value?.perfil != null
            val frase = if (estado == Estado.APROBADA) {
                frases.paraMateria(id, semilla = id * 31L + System.currentTimeMillis() / 60_000)
            } else null
            val d = calcularDesbloqueo(materia, antes, despues, logros, testHecho).copy(frase = frase)
            if (!d.vacio) _desbloqueo.value = d
        }
    }

    fun cerrarDesbloqueo() {
        _desbloqueo.value = null
    }

    fun cambiarTema(tema: ModoTema) {
        viewModelScope.launch { ajustes.cambiarTema(tema) }
    }

    /** Guarda el resultado del test. Devuelve los porcentajes calculados. */
    fun terminarTest(respuestas: List<Int>): Map<String, Int> {
        val porcentajes = test.calcular(respuestas)
        viewModelScope.launch { ajustes.guardarPerfil(porcentajes, System.currentTimeMillis()) }
        return porcentajes
    }

    fun borrarTodo() {
        viewModelScope.launch { progreso.borrarTodo() }
    }
}
