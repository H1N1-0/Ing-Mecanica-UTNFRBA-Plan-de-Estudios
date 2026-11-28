package ar.correlativas.mecanica.data

import java.text.Normalizer

private val MARCAS = Regex("\\p{M}")

/** Sin tildes y en minúsculas, para comparar búsquedas ("Álgebra" → "algebra"). */
fun normalizar(s: String): String = Normalizer.normalize(s, Normalizer.Form.NFD).replace(MARCAS, "").lowercase()

/**
 * Si la materia coincide con una búsqueda ya normalizada: por abreviatura ("am1", "iys", también
 * con espacios: "am 1"), por nombre (sin tildes ni mayúsculas), por número de materia o por código
 * SIU (desde 3 dígitos). Búsqueda vacía: coinciden todas.
 */
fun Materia.coincide(q: String): Boolean {
    if (q.isEmpty()) return true
    val junto = q.replace(" ", "")
    return normalizar(abrev).contains(junto) ||
        normalizar(nombre).contains(q) ||
        id.toString() == q ||
        (junto.length >= 3 && junto.all { it.isDigit() } && codigoSiu?.startsWith(junto) == true)
}
