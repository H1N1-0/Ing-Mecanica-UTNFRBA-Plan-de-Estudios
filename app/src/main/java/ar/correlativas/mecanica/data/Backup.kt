package ar.correlativas.mecanica.data

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * Copia de seguridad: progreso, notas, test vocacional y ajustes en un JSON.
 *
 * El progreso va con el MISMO formato con que se guarda en el celular (`CodecEstados`: "1:APROBADA,…"),
 * así que exportar e importar no transforma nada. Todo es local: archivo elegido por el usuario o QR.
 *
 * Versiones:
 *  - 1: estados SIN_CURSAR, REGULARIZADA y APROBADA. QR con prefijo "CMEC1:".
 *  - 2: suma PROMOCIONADA (con su nota en `notas.finales`). QR "CMEC2:". Se subió la versión para
 *    que una app vieja rechace la copia ("actualizala") en vez de perder las promociones en silencio.
 *  Una copia 1 se migra a 2 sin cambios: tiene los mismos campos y ningún estado que haya cambiado.
 */
data class DatosBackup(
    val estados: Map<Int, Estado>,
    val libreta: Libreta,
    val perfil: ResultadoPerfil?,
    val tema: ModoTema,
    val estilo: Estilo,
    val nombre: String,
    val nombrePreguntado: Boolean,
    val exportado: LocalDate,
) {
    /** Resumen para la confirmación antes de reemplazar. */
    val aprobadas get() = estados.count { it.value.aprobada }
    val promocionadas get() = estados.count { it.value == Estado.PROMOCIONADA }
    val regularizadas get() = estados.count { it.value == Estado.REGULARIZADA }
    val notas get() = libreta.finales.count { it.value.nota != null }
}

sealed interface LecturaBackup {
    /** [avisos]: lo que se descartó por no ser válido (por ejemplo, una materia que no existe). */
    data class Ok(val datos: DatosBackup, val avisos: List<String>) : LecturaBackup
    data class Error(val motivo: String) : LecturaBackup
}

object Backup {
    const val FORMATO = "correlativas-mecanica"
    const val VERSION = 2

    /** Prefijo del QR: identifica el contenido y su versión. */
    const val PREFIJO_QR = "CMEC2:"

    /** Prefijos de QR que se siguen leyendo (el actual y los de copias viejas). */
    private val PREFIJOS_QR = listOf(PREFIJO_QR, "CMEC1:")

    fun aJsonObjeto(d: DatosBackup): JSONObject = JSONObject().apply {
        put("formato", FORMATO)
        put("version", VERSION)
        put("exportado", d.exportado.toString())
        put("progreso", CodecEstados.codificar(d.estados))
        put("notas", CodecLibreta.aJson(d.libreta))
        d.perfil?.let { p ->
            put("test", JSONObject().apply {
                put("porcentajes", p.codificar())
                put("fecha", p.fecha)
                put("veces", p.veces)
            })
        }
        put("ajustes", JSONObject().apply {
            put("tema", d.tema.name)
            put("estilo", d.estilo.name)
            put("nombre", d.nombre)
            put("nombre_preguntado", d.nombrePreguntado)
        })
    }

    /** JSON legible para el archivo. */
    fun aJson(d: DatosBackup): String = aJsonObjeto(d).toString(2)

    fun deJson(texto: String, plan: Plan): LecturaBackup {
        val o = runCatching { JSONObject(texto.trim()) }.getOrNull()
            ?: return LecturaBackup.Error("El archivo no es un JSON válido.")
        if (o.optString("formato") != FORMATO) return LecturaBackup.Error("No es una copia de seguridad de esta app.")
        val version = o.optInt("version", 0)
        if (version < 1) return LecturaBackup.Error("La copia no tiene versión.")
        if (version > VERSION) return LecturaBackup.Error("La copia es de una versión más nueva de la app: actualizala primero.")
        migrar(o, version)

        val avisos = mutableListOf<String>()
        val crudo = o.optString("progreso")
        val leidos = CodecEstados.decodificar(crudo)
        val estados = leidos.filterKeys { plan[it] != null }
        val pares = crudo.split(',').count { it.isNotBlank() }
        if (pares > estados.size) avisos += "Se ignoraron ${pares - estados.size} materias que no existen en el plan o tienen un estado inválido."

        val libretaCruda = CodecLibreta.deJson(o.optJSONObject("notas"))
        val libreta = Libreta(
            finales = libretaCruda.finales.filterKeys { plan[it] != null && plan[it]?.tipo != Tipo.PPS },
            aplazos = libretaCruda.aplazos.filter { plan[it.materia] != null && plan[it.materia]?.tipo != Tipo.PPS },
        )
        val descartadas = (libretaCruda.finales.size - libreta.finales.size) + (libretaCruda.aplazos.size - libreta.aplazos.size)
        if (descartadas > 0) avisos += "Se ignoraron $descartadas notas de materias que no existen."
        // La promoción lleva nota: si falta (copia editada a mano), se importa igual y se avisa.
        val promoSinNota = estados.count { (id, e) -> e == Estado.PROMOCIONADA && libreta.finales[id]?.nota == null }
        if (promoSinNota > 0) {
            avisos += if (promoSinNota == 1) "Una materia promocionada no tiene nota: cargala desde su detalle."
            else "$promoSinNota materias promocionadas no tienen nota: cargalas desde su detalle."
        }

        val perfil = o.optJSONObject("test")?.let { t ->
            ResultadoPerfil.decodificar(t.optString("porcentajes"), t.optLong("fecha", 0L), t.optInt("veces", 0))
        }
        val a = o.optJSONObject("ajustes") ?: JSONObject()
        return LecturaBackup.Ok(
            DatosBackup(
                estados = estados,
                libreta = libreta,
                perfil = perfil,
                tema = ModoTema.entries.firstOrNull { it.name == a.optString("tema") } ?: ModoTema.SISTEMA,
                estilo = Estilo.de(a.optString("estilo")),
                nombre = a.optString("nombre").trim().take(MAX_NOMBRE),
                nombrePreguntado = a.optBoolean("nombre_preguntado", true),
                exportado = CodecLibreta.fecha(o.optString("exportado")) ?: LocalDate.MIN,
            ),
            avisos,
        )
    }

    /**
     * Lleva una copia de una versión vieja al formato actual, en el mismo objeto.
     * 1 → 2: no hay nada que transformar (mismos campos; PROMOCIONADA no existía).
     * Si un cambio futuro mueve o renombra campos, la conversión va acá, versión por versión.
     */
    private fun migrar(o: JSONObject, desde: Int) {
        var v = desde
        if (v == 1) v = 2
        o.put("version", v)
    }

    /**
     * Texto para el QR: el JSON compacto, comprimido (deflate) y en Base64 URL, con un prefijo.
     * Así entra cómodo en un QR aunque tengas todas las notas cargadas.
     */
    fun aQr(d: DatosBackup): String {
        val bytes = aJsonObjeto(d).toString().toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        deflater.setInput(bytes)
        deflater.finish()
        val salida = ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (!deflater.finished()) salida.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return PREFIJO_QR + Base64.getUrlEncoder().withoutPadding().encodeToString(salida.toByteArray())
    }

    fun deQr(texto: String, plan: Plan): LecturaBackup {
        val limpio = texto.trim()
        val prefijo = PREFIJOS_QR.firstOrNull { limpio.startsWith(it) }
        if (prefijo == null) {
            return if (limpio.startsWith("CMEC")) LecturaBackup.Error("El QR es de una versión más nueva de la app: actualizala primero.")
            else LecturaBackup.Error("Ese QR no es una copia de seguridad de esta app.")
        }
        val json = runCatching {
            val comprimido = Base64.getUrlDecoder().decode(limpio.removePrefix(prefijo))
            val inflater = Inflater(true)
            inflater.setInput(comprimido)
            val salida = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buffer)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                salida.write(buffer, 0, n)
            }
            inflater.end()
            salida.toString(Charsets.UTF_8.name())
        }.getOrNull() ?: return LecturaBackup.Error("El QR está incompleto o dañado.")
        return deJson(json, plan)
    }

    /** Nombre sugerido para el archivo exportado. */
    fun nombreArchivo(fecha: LocalDate) = "correlativas-backup-$fecha.json"
}
