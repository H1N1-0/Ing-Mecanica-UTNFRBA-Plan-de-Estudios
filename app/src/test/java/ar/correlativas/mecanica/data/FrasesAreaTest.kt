package ar.correlativas.mecanica.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FrasesAreaTest {
    private val texto = listOf(File("../frases.json"), File("frases.json")).first { it.exists() }.readText()
    private val frases = Frases.parse(texto)

    @Test fun todasLasFrasesTienenUnAreaValida() {
        val crudas = JSONObject(texto).getJSONArray("frases")
        val claves = Area.entries.map { it.clave }.toSet()
        for (i in 0 until crudas.length()) {
            val area = crudas.getJSONObject(i).optString("area")
            assertTrue("frase ${crudas.getJSONObject(i).getInt("id")} sin área válida: '$area'", area in claves)
        }
        assertEquals(crudas.length(), frases.todas.size)
    }

    @Test fun unAutorTieneSiempreLaMismaArea() {
        frases.todas.groupBy { it.autor }.forEach { (autor, fs) ->
            assertEquals(autor, 1, fs.map { it.area }.distinct().size)
        }
    }

    @Test fun todasLasAreasSeUsan() {
        assertEquals(Area.entries.toSet(), frases.todas.map { it.area }.toSet())
    }

    @Test fun areaDesconocidaCaeEnHumanidades() {
        assertEquals(Area.HUMANIDADES, Area.de(null))
        assertEquals(Area.HUMANIDADES, Area.de("astrologia"))
        assertEquals(Area.ENERGIA, Area.de("energia"))
    }

    @Test fun iniciales() {
        assertEquals("IN", iniciales("Isaac Newton"))
        assertEquals("WT", iniciales("William Thomson (Lord Kelvin)"))
        assertEquals("TE", iniciales("Thomas A. Edison"))
        assertEquals("GB", iniciales("George E. P. Box"))
        assertEquals("GH", iniciales("G. H. Hardy"))
        assertEquals("JL", iniciales("Joseph-Louis Lagrange"))
        assertEquals("AS", iniciales("Antoine de Saint-Exupéry"))
        assertEquals("SÉ", iniciales("Séneca"))
        assertEquals("?", iniciales("  "))
        // Todas las iniciales del archivo son de 2 letras.
        frases.todas.forEach { assertEquals(it.autor, 2, iniciales(it.autor).length) }
    }

    @Test fun rotuloSinNombreDiceEstudiante() {
        assertEquals("Estudiante", nombreParaRotulo(null))
        assertEquals("Estudiante", nombreParaRotulo(""))
        assertEquals("Estudiante", nombreParaRotulo("   "))
        assertEquals("Ana", nombreParaRotulo("  Ana "))
        assertEquals("Estudiante", Preferencias(ModoTema.SISTEMA, null).nombreRotulo)
    }

    @Test fun estiloPorNombre() {
        assertEquals(Estilo.PLANO, Estilo.PREDETERMINADO)
        assertEquals(Estilo.PLANO, Estilo.de(null))
        assertEquals(Estilo.PLANO, Estilo.de("CUALQUIERA"))
        Estilo.entries.forEach { assertEquals(it, Estilo.de(it.name)) }
    }
}
