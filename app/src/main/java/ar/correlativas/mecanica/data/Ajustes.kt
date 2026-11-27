package ar.correlativas.mecanica.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.updateAll
import ar.correlativas.mecanica.widget.CorrelativasWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ModoTema(val texto: String) { SISTEMA("Según el sistema"), CLARO("Claro"), OSCURO("Oscuro") }

/** Estilo visual de la app y el widget; se combina con [ModoTema]. Se guarda por nombre. */
enum class Estilo(val texto: String, val descripcion: String) {
    PLANO("Plano técnico", "Lámina de dibujo técnico: blueprint o papel, cuadrícula y cotas"),
    GAMIFICADO("Gamificado", "Colores vivos, brillos y celebraciones"),
    MATERIAL("Material You", "Estilo Google, con los colores de tu fondo de pantalla");

    companion object {
        val PREDETERMINADO = PLANO

        fun de(nombre: String?): Estilo = entries.firstOrNull { it.name == nombre } ?: PREDETERMINADO
    }
}

data class Preferencias(
    val tema: ModoTema,
    val perfil: ResultadoPerfil?,
    val estilo: Estilo = Estilo.PREDETERMINADO,
    /** Nombre para el rótulo de la lámina (estilo Plano técnico); vacío si no lo cargó. */
    val nombre: String = "",
)

/** DataStore separado del progreso: el progreso de materias sigue en "progreso", sin cambios. */
private val Context.ajustesStore by preferencesDataStore(name = "ajustes")
private val TEMA = stringPreferencesKey("tema")
private val ESTILO = stringPreferencesKey("estilo")
private val NOMBRE = stringPreferencesKey("nombre")
private val PERFIL = stringPreferencesKey("perfil")
private val PERFIL_FECHA = longPreferencesKey("perfil_fecha")
private val PERFIL_VECES = intPreferencesKey("perfil_veces")

const val MAX_NOMBRE = 40

class Ajustes(context: Context) {
    private val context = context.applicationContext

    val preferencias: Flow<Preferencias> = this.context.ajustesStore.data.map { p ->
        Preferencias(
            tema = ModoTema.entries.firstOrNull { it.name == p[TEMA] } ?: ModoTema.SISTEMA,
            perfil = ResultadoPerfil.decodificar(p[PERFIL], p[PERFIL_FECHA] ?: 0L, p[PERFIL_VECES] ?: 0),
            estilo = Estilo.de(p[ESTILO]),
            nombre = p[NOMBRE].orEmpty(),
        )
    }

    suspend fun cambiarTema(tema: ModoTema) {
        context.ajustesStore.edit { it[TEMA] = tema.name }
        CorrelativasWidget().updateAll(context)
    }

    suspend fun cambiarEstilo(estilo: Estilo) {
        context.ajustesStore.edit { it[ESTILO] = estilo.name }
        CorrelativasWidget().updateAll(context)
    }

    suspend fun cambiarNombre(nombre: String) {
        context.ajustesStore.edit { it[NOMBRE] = nombre.trim().take(MAX_NOMBRE) }
    }

    suspend fun guardarPerfil(porcentajes: Map<String, Int>, fecha: Long) {
        context.ajustesStore.edit {
            it[PERFIL] = ResultadoPerfil(porcentajes, fecha, 0).codificar()
            it[PERFIL_FECHA] = fecha
            it[PERFIL_VECES] = (it[PERFIL_VECES] ?: 0) + 1
        }
    }
}
