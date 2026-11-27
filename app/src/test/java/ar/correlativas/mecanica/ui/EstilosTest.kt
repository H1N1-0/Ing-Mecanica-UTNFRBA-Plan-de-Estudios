package ar.correlativas.mecanica.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import ar.correlativas.mecanica.data.Area
import ar.correlativas.mecanica.data.Estilo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tokens de los tres estilos: contraste suficiente en claro y oscuro, y rasgos propios de cada uno. */
class EstilosTest {
    private fun contraste(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb) / minOf(la, lb).toDouble()
    }

    private fun combos() = Estilo.entries.flatMap { e -> listOf(false, true).map { o -> Triple(e, o, paletaFija(e, o)) } }

    @Test fun textoLegibleEnLosSeisCombos() {
        combos().forEach { (e, o, p) ->
            val donde = "$e ${if (o) "oscuro" else "claro"}"
            assertTrue("$donde texto/fondo", contraste(p.texto, p.fondo) >= 7.0)
            assertTrue("$donde texto/tarjeta", contraste(p.texto, p.tarjeta) >= 7.0)
            assertTrue("$donde texto2/tarjeta", contraste(p.texto2, p.tarjeta) >= 4.5)
            assertTrue("$donde héroe", contraste(p.heroeTexto, p.degradeJugador.first()) >= 4.5)
        }
    }

    @Test fun coloresDeEstadoSeDistinguenDelFondo() {
        combos().forEach { (e, o, p) ->
            val donde = "$e ${if (o) "oscuro" else "claro"}"
            listOf(p.disponible, p.regular, p.rendir, p.oro, p.critico).forEach { c ->
                assertTrue("$donde estado ${c.value.toString(16)}", contraste(c, p.tarjeta) >= 3.0)
            }
            assertTrue("$donde texto sobre acento", contraste(p.sobreAcento, p.disponible) >= 4.5)
            assertTrue("$donde texto sobre oro", contraste(p.sobreOro, p.oro) >= 3.0)
        }
    }

    @Test fun cadaEstiloTieneSusTokens() {
        val plano = temaVisual(Estilo.PLANO, paletaFija(Estilo.PLANO, true))
        val gami = temaVisual(Estilo.GAMIFICADO, paletaFija(Estilo.GAMIFICADO, true))
        val material = temaVisual(Estilo.MATERIAL, paletaFija(Estilo.MATERIAL, true))
        assertEquals(Indicador.MANOMETRO, plano.indicador)
        assertEquals(Festejo.ENGRANAJES, plano.movimiento.festejo)
        assertEquals(Mono, plano.fuenteDatos)
        assertEquals(Indicador.ANILLO_DEGRADE, gami.indicador)
        assertEquals(Festejo.CONFETI, gami.movimiento.festejo)
        assertTrue(gami.brillo)
        assertEquals(Indicador.ANILLO_TONAL, material.indicador)
        assertEquals(Festejo.SUAVE, material.movimiento.festejo)
        assertNotEquals(plano.formas.grande, material.formas.grande)
        assertNotEquals(gami.fuente, plano.fuente)
    }

    @Test fun lasAreasTienenColoresDistintos() {
        Estilo.entries.forEach { e ->
            listOf(false, true).forEach { o ->
                C.tema = temaVisual(e, paletaFija(e, o))
                val colores = Area.entries.map { colorArea(it) }
                assertEquals("$e $o", Area.entries.size, colores.toSet().size)
                colores.forEach { assertTrue("$e $o área legible", contraste(it, C.tarjeta) >= 3.0) }
            }
        }
    }
}
