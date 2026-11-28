package ar.correlativas.mecanica.data

import ar.correlativas.mecanica.widget.textoMateria
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Código SIU y sigla ("abrev") de cada materia en correlativas.json, búsqueda y textos compactos. */
class AbreviaturasTest {
    private val texto = listOf(File("../correlativas.json"), File("correlativas.json")).first { it.exists() }.readText()
    private val plan = Plan.parse(texto)

    /**
     * Problemas de identificación en el JSON del plan: materias sin "abrev" o sin "codigo_siu"
     * (faltante, vacío o que no es texto), siglas repetidas (sin distinguir mayúsculas, como busca
     * la app), códigos repetidos y siglas de más de 6 letras.
     */
    private fun problemas(json: String): List<String> {
        val raiz = JSONObject(json)
        val materias = buildList {
            raiz.getJSONArray("obligatorias").let { a -> repeat(a.length()) { add(a.getJSONObject(it)) } }
            add(raiz.getJSONObject("pps"))
            raiz.getJSONArray("electivas").let { a -> repeat(a.length()) { add(a.getJSONObject(it)) } }
        }
        val errores = mutableListOf<String>()
        fun campo(o: JSONObject, nombre: String): String? =
            (o.opt(nombre) as? String)?.trim()?.takeIf { it.isNotEmpty() }
                .also { if (it == null) errores += "La materia ${o.getInt("id")} no tiene \"$nombre\"" }
        val abrevs = materias.mapNotNull { o -> campo(o, "abrev")?.let { o.getInt("id") to it } }
        val codigos = materias.mapNotNull { o -> campo(o, "codigo_siu")?.let { o.getInt("id") to it } }
        abrevs.groupBy({ it.second.lowercase() }, { it.first }).filterValues { it.size > 1 }
            .forEach { (a, ids) -> errores += "Abreviatura \"$a\" repetida en $ids" }
        codigos.groupBy({ it.second }, { it.first }).filterValues { it.size > 1 }
            .forEach { (c, ids) -> errores += "Código SIU $c repetido en $ids" }
        abrevs.filter { it.second.length > 6 }.forEach { (id, a) -> errores += "La abreviatura de $id ($a) tiene más de 6 letras" }
        return errores
    }

    @Test fun cadaMateriaTieneAbrevYCodigoSiuUnicos() {
        assertEquals(emptyList<String>(), problemas(texto))
    }

    @Test fun laValidacionDetectaFaltantesYRepetidos() {
        val raiz = JSONObject(texto)
        val obl = raiz.getJSONArray("obligatorias")
        obl.getJSONObject(1).put("abrev", "am1")                    // misma sigla que la 1 (en minúscula)
        obl.getJSONObject(2).put("codigo_siu", obl.getJSONObject(0).getString("codigo_siu"))
        obl.getJSONObject(3).remove("abrev")
        raiz.getJSONObject("pps").put("codigo_siu", "")
        val p = problemas(raiz.toString())
        assertTrue(p.toString(), p.any { "\"am1\" repetida" in it })
        assertTrue(p.toString(), p.any { "Código SIU 950702 repetido" in it })
        assertTrue(p.toString(), p.any { "materia 4 no tiene \"abrev\"" in it })
        assertTrue(p.toString(), p.any { "materia 41 no tiene \"codigo_siu\"" in it })
    }

    @Test fun elPlanLeeLaSiglaYElCodigo() {
        assertEquals("AM1", plan[1]!!.abrev)
        assertEquals("950702", plan[1]!!.codigoSiu)
        assertEquals("PF", plan[Plan.PROYECTO_FINAL]!!.abrev)
        assertEquals("PPS", plan.pps.abrev)
        assertEquals("2311699", plan.pps.codigoSiu)
        assertTrue(plan.todas.all { it.abrev.isNotBlank() && it.abrev.length <= 6 })
    }

    private fun buscar(q: String) = plan.todas.filter { it.coincide(normalizar(q.trim())) }.map { it.id }.toSet()

    @Test fun buscaPorSiglaSinImportarMayusculasNiEspacios() {
        assertEquals(setOf(1), buscar("AM1"))
        assertEquals(setOf(1), buscar("am1"))
        assertEquals(setOf(1), buscar("am 1"))
        assertTrue(buscar("am").containsAll(listOf(1, 12)))
        assertEquals(setOf(5), buscar("iys"))
        assertEquals(setOf(17), buscar("termo"))
    }

    @Test fun buscaPorNombreNumeroYCodigoSiu() {
        assertTrue(buscar("algebra").contains(3))    // sin tilde
        assertTrue(buscar("Análisis").containsAll(listOf(1, 12)))
        assertEquals(setOf(40), buscar("940894"))
        assertTrue(40 in buscar("40"))
        assertEquals(plan.todas.size, buscar("").size)
        assertTrue(buscar("zzz").isEmpty())
    }

    @Test fun elWidgetUsaLaSiglaSiElNombreNoEntra() {
        val largo = plan[14]!! // Ingeniería Ambiental y Seguridad Industrial
        assertEquals("IASI", textoMateria(largo, 220f))
        assertEquals(largo.nombre, textoMateria(largo, 500f))
        assertEquals("Física I", textoMateria(plan[4]!!, 220f))
        assertFalse(textoMateria(plan[110]!!, 300f) == plan[110]!!.nombre)
    }
}
