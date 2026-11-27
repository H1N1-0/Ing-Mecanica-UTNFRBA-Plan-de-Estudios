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

data class Preferencias(val tema: ModoTema, val perfil: ResultadoPerfil?)

/** DataStore separado del progreso: el progreso de materias sigue en "progreso", sin cambios. */
private val Context.ajustesStore by preferencesDataStore(name = "ajustes")
private val TEMA = stringPreferencesKey("tema")
private val PERFIL = stringPreferencesKey("perfil")
private val PERFIL_FECHA = longPreferencesKey("perfil_fecha")
private val PERFIL_VECES = intPreferencesKey("perfil_veces")

class Ajustes(context: Context) {
    private val context = context.applicationContext

    val preferencias: Flow<Preferencias> = this.context.ajustesStore.data.map { p ->
        Preferencias(
            tema = ModoTema.entries.firstOrNull { it.name == p[TEMA] } ?: ModoTema.SISTEMA,
            perfil = ResultadoPerfil.decodificar(p[PERFIL], p[PERFIL_FECHA] ?: 0L, p[PERFIL_VECES] ?: 0),
        )
    }

    suspend fun cambiarTema(tema: ModoTema) {
        context.ajustesStore.edit { it[TEMA] = tema.name }
        CorrelativasWidget().updateAll(context)
    }

    suspend fun guardarPerfil(porcentajes: Map<String, Int>, fecha: Long) {
        context.ajustesStore.edit {
            it[PERFIL] = ResultadoPerfil(porcentajes, fecha, 0).codificar()
            it[PERFIL_FECHA] = fecha
            it[PERFIL_VECES] = (it[PERFIL_VECES] ?: 0) + 1
        }
    }
}
