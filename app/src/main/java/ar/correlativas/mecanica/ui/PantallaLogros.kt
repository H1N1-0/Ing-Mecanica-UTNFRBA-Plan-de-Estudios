package ar.correlativas.mecanica.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.IconoLogro
import ar.correlativas.mecanica.data.Logro
import ar.correlativas.mecanica.data.formatearHoras
import kotlin.math.cos
import kotlin.math.sin

fun iconoLogro(i: IconoLogro): Int = when (i) {
    IconoLogro.PASO -> R.drawable.ic_rayo
    IconoLogro.FINAL -> R.drawable.ic_estrella
    IconoLogro.NIVEL -> R.drawable.ic_trofeo
    IconoLogro.DECENA -> R.drawable.ic_medalla
    IconoLogro.MITAD -> R.drawable.ic_mitad
    IconoLogro.IDIOMA -> R.drawable.ic_idioma
    IconoLogro.ENGRANAJE -> R.drawable.ic_engranaje
    IconoLogro.ELECTIVA -> R.drawable.ic_ramas
    IconoLogro.RELOJ -> R.drawable.ic_reloj
    IconoLogro.TITULO -> R.drawable.ic_birrete
    IconoLogro.PPS -> R.drawable.ic_casco
    IconoLogro.PROYECTO -> R.drawable.ic_bandera
    IconoLogro.CORONA -> R.drawable.ic_corona
    IconoLogro.BRUJULA -> R.drawable.ic_brujula
}

/** Insignia hexagonal: dorada si está conseguida, gris si no. */
@Composable
fun Insignia(icono: IconoLogro, conseguido: Boolean, tam: Dp = 64.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(tam), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.minDimension / 2
            val hex = Path().apply {
                for (i in 0 until 6) {
                    val a = Math.toRadians((60.0 * i) - 90.0)
                    val p = Offset(center.x + r * 0.96f * cos(a).toFloat(), center.y + r * 0.96f * sin(a).toFloat())
                    if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
                }
                close()
            }
            if (conseguido) {
                drawCircle(Color(0xFFFFCB3D).copy(alpha = 0.25f), radius = r * 1.08f)
                drawPath(hex, Brush.linearGradient(listOf(Color(0xFFFFE27A), Color(0xFFFFCB3D), Color(0xFFE39A00)), Offset(0f, 0f), Offset(size.width, size.height)))
                drawPath(hex, Color.White.copy(alpha = 0.5f), style = Stroke(r * 0.05f))
            } else {
                drawPath(hex, C.tarjetaAlta)
                drawPath(hex, C.borde, style = Stroke(r * 0.05f))
            }
        }
        Ic(iconoLogro(icono), if (conseguido) C.sobreOro else C.apagado, tam = tam * 0.44f)
    }
}

@Composable
fun PantallaLogros(avance: Avance, logros: List<Logro>, testHecho: Boolean) {
    val conseguidos = logros.count { it.cumplido(avance, testHecho) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Anillo(conseguidos / logros.size.toFloat(), Modifier.fillMaxSize(), grosor = 7.dp, colores = listOf(C.oro, C.oroOscuro, C.oro))
                    Ic(R.drawable.ic_trofeo, C.oro, tam = 26.dp)
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text("Logros", style = MaterialTheme.typography.headlineMedium, color = C.texto, modifier = Modifier.semantics { heading() })
                    Text("$conseguidos de ${logros.size} conseguidos", color = C.texto2, fontFamily = Chakra, fontSize = 15.sp)
                }
            }
        }
        items(logros, key = { it.id }, contentType = { "logro" }) { l ->
            val ok = l.cumplido(avance, testHecho)
            val (h, t) = l.progreso(avance, testHecho)
            Tarjeta(Modifier.fillMaxWidth(), fondo = if (ok) C.tarjetaAlta else C.tarjeta) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = 196.dp).padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Insignia(l.icono, ok, tam = 64.dp)
                    Text(
                        l.titulo,
                        Modifier.padding(top = 10.dp),
                        color = if (ok) C.oro else C.texto,
                        fontFamily = Chakra,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        l.descripcion,
                        Modifier.padding(top = 2.dp),
                        color = C.texto2,
                        fontFamily = Chakra,
                        fontSize = 12.5.sp,
                        lineHeight = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                    if (ok) {
                        Pildora("Conseguido", C.oro, Modifier.padding(top = 8.dp), R.drawable.ic_check)
                    } else {
                        Barra((h / t).toFloat(), C.violeta, Modifier.fillMaxWidth().padding(top = 10.dp), alto = 6.dp)
                        Text(
                            when {
                                l.id == "ingeniero" || l.id == "titulo-intermedio" -> "${(h / t * 100).toInt()}%"
                                t == 1.0 -> "Pendiente"
                                else -> "${formatearHoras(h)} de ${formatearHoras(t)}"
                            },
                            color = C.apagado,
                            fontFamily = Chakra,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
    }
}
