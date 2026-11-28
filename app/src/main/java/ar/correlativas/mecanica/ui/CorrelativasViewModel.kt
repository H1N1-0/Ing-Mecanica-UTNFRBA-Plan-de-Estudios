package ar.correlativas.mecanica.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import ar.correlativas.mecanica.data.Ajustes
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.Backup
import ar.correlativas.mecanica.data.CambioSiu
import ar.correlativas.mecanica.data.CopiaAutomatica
import ar.correlativas.mecanica.data.HistoriaSiu
import ar.correlativas.mecanica.data.ImportacionSiu
import ar.correlativas.mecanica.data.LecturaHistoria
import ar.correlativas.mecanica.data.DatosBackup
import ar.correlativas.mecanica.data.Estadisticas
import ar.correlativas.mecanica.data.LecturaBackup
import ar.correlativas.mecanica.data.Libreta
import ar.correlativas.mecanica.data.NotaFinal
import ar.correlativas.mecanica.data.Notas
import ar.correlativas.mecanica.data.Tipo
import java.time.LocalDate
import kotlinx.coroutines.flow.combine
import ar.correlativas.mecanica.data.CaminoCritico
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.Estilo
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
import ar.correlativas.mecanica.data.aprobada
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
import kotlinx.coroutines.withContext

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
    private val notas = Notas(app)

    /** Notas de finales y aplazos (DataStore "notas", aparte del progreso). */
    val libreta: StateFlow<Libreta> = notas.libreta
        .stateIn(viewModelScope, SharingStarted.Eagerly, Libreta())

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

    /** Promedios y demás: se recalculan cuando cambia el avance o una nota, fuera del hilo principal. */
    val estadisticas: StateFlow<Estadisticas?> = combine(avance, libreta) { a, l -> a?.let { Estadisticas(it, l) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Materia recién aprobada a la que se le ofrece cargar la nota (después de la celebración). */
    private val _pedirNota = MutableStateFlow<Int?>(null)
    val pedirNota: StateFlow<Int?> = _pedirNota.asStateFlow()

    val preferencias: StateFlow<Preferencias?> = ajustes.preferencias
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _desbloqueo = MutableStateFlow<Desbloqueo?>(null)
    val desbloqueo: StateFlow<Desbloqueo?> = _desbloqueo.asStateFlow()

    /**
     * Materia que se está por promocionar: la promoción lleva nota obligatoria, así que el estado
     * recién cambia cuando se guarda la nota ([promocionar]). Cancelar la deja como estaba.
     */
    private val _pedirPromocion = MutableStateFlow<Int?>(null)
    val pedirPromocion: StateFlow<Int?> = _pedirPromocion.asStateFlow()

    fun cambiar(id: Int, estado: Estado) {
        val materia = plan[id] ?: return
        if (estado == Estado.PROMOCIONADA) {
            if (materia.tipo != Tipo.PPS && avance.value?.estado(id) != Estado.PROMOCIONADA) _pedirPromocion.value = id
            return
        }
        viewModelScope.launch { aplicar(materia, estado) }
    }

    /** Guarda la nota (obligatoria) y recién ahí marca la materia como promocionada. */
    fun promocionar(id: Int, nf: NotaFinal) {
        val materia = plan[id] ?: return
        if (nf.nota == null) return
        _pedirPromocion.value = null
        viewModelScope.launch {
            notas.guardarFinal(id, nf)
            aplicar(materia, Estado.PROMOCIONADA)
        }
    }

    fun cancelarPromocion() {
        _pedirPromocion.value = null
    }

    private suspend fun aplicar(materia: Materia, estado: Estado) {
        val id = materia.id
        val antes = Avance(plan, progreso.estados.first())
        if (antes.estado(id) == estado) return
        progreso.cambiar(id, estado)
        val despues = Avance(plan, progreso.estados.first())
        val testHecho = preferencias.value?.perfil != null
        val frase = if (estado.aprobada) {
            frases.paraMateria(id, semilla = id * 31L + System.currentTimeMillis() / 60_000)
        } else null
        val d = calcularDesbloqueo(materia, antes, despues, logros, testHecho).copy(frase = frase)
        if (!d.vacio) _desbloqueo.value = d
        // Al aprobar con final, se ofrece cargar la nota (es opcional). La PPS no se rinde.
        // Al promocionar no hace falta: la nota se pidió antes.
        if (estado == Estado.APROBADA && materia.tipo != Tipo.PPS && libreta.value.finales[id]?.nota == null) {
            _pedirNota.value = id
        }
    }

    fun cerrarDesbloqueo() {
        _desbloqueo.value = null
    }

    fun cambiarTema(tema: ModoTema) {
        viewModelScope.launch { ajustes.cambiarTema(tema) }
    }

    fun cambiarEstilo(estilo: Estilo) {
        viewModelScope.launch { ajustes.cambiarEstilo(estilo) }
    }

    fun cambiarNombre(nombre: String) {
        viewModelScope.launch { ajustes.cambiarNombre(nombre) }
    }

    fun omitirNombre() {
        viewModelScope.launch { ajustes.omitirNombre() }
    }

    /** Guarda el resultado del test. Devuelve los porcentajes calculados. */
    fun terminarTest(respuestas: List<Int>): Map<String, Int> {
        val porcentajes = test.calcular(respuestas)
        viewModelScope.launch { ajustes.guardarPerfil(porcentajes, System.currentTimeMillis()) }
        return porcentajes
    }

    fun borrarTodo() {
        viewModelScope.launch {
            progreso.borrarTodo()
            notas.borrarTodo()
        }
    }

    // ------------------------------------------------------------ notas

    /** Abre el diálogo de nota de una materia (desde el detalle) o lo cierra (null). */
    fun editarNota(id: Int?) {
        _pedirNota.value = id
    }

    fun guardarFinal(id: Int, nf: NotaFinal?) {
        _pedirNota.value = null
        viewModelScope.launch { notas.guardarFinal(id, nf) }
    }

    fun agregarAplazo(id: Int, nota: Int, fecha: LocalDate?) {
        viewModelScope.launch { notas.agregarAplazo(id, nota, fecha) }
    }

    fun borrarAplazo(id: Long) {
        viewModelScope.launch { notas.borrarAplazo(id) }
    }

    // ------------------------------------------------------------ copia de seguridad

    /** Junta todo lo guardado (progreso, notas, test y ajustes) para exportarlo. */
    suspend fun datosBackup(): DatosBackup {
        val prefs = ajustes.preferencias.first()
        return DatosBackup(
            estados = progreso.estados.first(),
            libreta = notas.libreta.first(),
            perfil = prefs.perfil,
            tema = prefs.tema,
            estilo = prefs.estilo,
            nombre = prefs.nombre,
            nombrePreguntado = prefs.nombrePreguntado,
            exportado = LocalDate.now(),
        )
    }

    fun leerBackupJson(texto: String): LecturaBackup = Backup.deJson(texto, plan)

    fun leerBackupQr(texto: String): LecturaBackup = Backup.deQr(texto, plan)

    /** Reemplaza todo lo guardado por la copia (ya confirmada por el usuario). */
    fun importar(d: DatosBackup) {
        viewModelScope.launch {
            progreso.reemplazar(d.estados)
            notas.reemplazar(d.libreta)
            ajustes.importar(d)
        }
    }

    // ------------------------------------------------------------ historia académica del SIU

    private val _hayCopiaAutomatica = MutableStateFlow(CopiaAutomatica.existe(app))
    /** Hay una copia guardada antes de la última importación del SIU (para deshacerla). */
    val hayCopiaAutomatica: StateFlow<Boolean> = _hayCopiaAutomatica.asStateFlow()

    /** Importación lista para la vista previa (vive acá para sobrevivir a la rotación). */
    private val _importacionSiu = MutableStateFlow<ImportacionSiu?>(null)
    val importacionSiu: StateFlow<ImportacionSiu?> = _importacionSiu.asStateFlow()

    /** Resultado de la última acción de la importación, para mostrarlo en Ajustes. */
    private val _mensajeSiu = MutableStateFlow<String?>(null)
    val mensajeSiu: StateFlow<String?> = _mensajeSiu.asStateFlow()

    /**
     * Lee el PDF y arma la vista previa comparando con el progreso y las notas de ahora.
     * Devuelve el motivo si no se pudo (no es una historia del SIU, PDF dañado…): no cambia nada.
     */
    suspend fun prepararSiu(bytes: ByteArray): String? {
        val lectura = withContext(Dispatchers.Default) { HistoriaSiu.leer(bytes) }
        val filas = when (lectura) {
            is LecturaHistoria.Error -> return lectura.motivo
            is LecturaHistoria.Ok -> lectura.filas
        }
        if (filas.isEmpty()) return "La historia académica no tiene materias para importar."
        val actual = Avance(plan, progreso.estados.first())
        val l = notas.libreta.first()
        _importacionSiu.value = withContext(Dispatchers.Default) { ImportacionSiu.armar(filas, actual, l) }
        return null
    }

    fun cancelarSiu() {
        _importacionSiu.value = null
    }

    fun limpiarMensajeSiu() {
        _mensajeSiu.value = null
    }

    /**
     * Aplica los cambios elegidos. Antes guarda una copia automática de todo; si no se puede
     * guardar, no cambia nada. Corre en el scope del ViewModel: rotar no lo corta a la mitad.
     */
    fun aplicarSiu(elegidos: List<CambioSiu>) {
        _importacionSiu.value = null
        viewModelScope.launch {
            val app = getApplication<Application>()
            val copia = runCatching {
                val json = Backup.aJson(datosBackup())
                withContext(Dispatchers.IO) { CopiaAutomatica.guardar(app, json) }
            }
            if (copia.isFailure) {
                _mensajeSiu.value = "No se pudo guardar la copia automática, así que no se cambió nada."
                return@launch
            }
            _hayCopiaAutomatica.value = true
            val (estados, nuevaLibreta) = ImportacionSiu.aplicar(elegidos, progreso.estados.first(), notas.libreta.first())
            progreso.reemplazar(estados)
            notas.reemplazar(nuevaLibreta)
            _mensajeSiu.value = "Listo: ${if (elegidos.size == 1) "1 materia actualizada" else "${elegidos.size} materias actualizadas"}. " +
                "Si algo no quedó bien, podés deshacerlo."
        }
    }

    /** La copia automática, para confirmarla y restaurarla con [importar]. */
    suspend fun leerCopiaAutomatica(): LecturaBackup {
        val texto = withContext(Dispatchers.IO) { runCatching { CopiaAutomatica.leer(getApplication()) }.getOrNull() }
            ?: return LecturaBackup.Error("No hay una copia automática guardada.")
        return Backup.deJson(texto, plan)
    }
}
