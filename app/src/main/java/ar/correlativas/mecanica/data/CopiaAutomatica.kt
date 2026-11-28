package ar.correlativas.mecanica.data

import android.content.Context
import java.io.File

/**
 * Copia de seguridad automática antes de importar la historia del SIU: el mismo JSON de [Backup],
 * guardado en el almacenamiento interno de la app (no hace falta ningún permiso). Hay una sola:
 * cada importación pisa la anterior. Desde Ajustes se puede volver a ella.
 */
object CopiaAutomatica {
    private fun archivo(context: Context) = File(File(context.filesDir, "copias"), "antes-de-importar-siu.json")

    fun existe(context: Context): Boolean = archivo(context).isFile

    /** Guarda la copia. Escribe en un temporal y lo renombra, así nunca queda a medio escribir. */
    fun guardar(context: Context, json: String) {
        val destino = archivo(context)
        destino.parentFile?.mkdirs()
        val temporal = File(destino.parentFile, destino.name + ".tmp")
        temporal.writeText(json, Charsets.UTF_8)
        if (!temporal.renameTo(destino)) {
            temporal.copyTo(destino, overwrite = true)
            temporal.delete()
        }
    }

    fun leer(context: Context): String? = archivo(context).takeIf { it.isFile }?.readText(Charsets.UTF_8)
}
