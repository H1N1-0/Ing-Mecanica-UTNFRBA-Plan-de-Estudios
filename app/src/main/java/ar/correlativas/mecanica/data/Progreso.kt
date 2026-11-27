package ar.correlativas.mecanica.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ar.correlativas.mecanica.widget.CorrelativasWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "progreso")
private val ESTADOS = stringPreferencesKey("estados")

/** Progreso del usuario guardado en el celular con DataStore. */
class Progreso(context: Context) {
    private val context = context.applicationContext

    val estados: Flow<Map<Int, Estado>> =
        this.context.dataStore.data.map { CodecEstados.decodificar(it[ESTADOS]) }

    suspend fun cambiar(id: Int, estado: Estado) {
        context.dataStore.edit { prefs ->
            val actual = CodecEstados.decodificar(prefs[ESTADOS]).toMutableMap()
            actual[id] = estado
            prefs[ESTADOS] = CodecEstados.codificar(actual)
        }
        CorrelativasWidget().updateAll(context)
    }

    /**
     * Reemplaza todo el progreso (importar una copia). Se guarda con el MISMO formato de siempre
     * (`CodecEstados`), en la misma clave.
     */
    suspend fun reemplazar(estados: Map<Int, Estado>) {
        context.dataStore.edit { it[ESTADOS] = CodecEstados.codificar(estados) }
        CorrelativasWidget().updateAll(context)
    }

    suspend fun borrarTodo() {
        context.dataStore.edit { it.remove(ESTADOS) }
        CorrelativasWidget().updateAll(context)
    }
}
