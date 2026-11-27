package ar.correlativas.mecanica.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ar.correlativas.mecanica.MainActivity
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.Ajustes
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.Frase
import ar.correlativas.mecanica.data.Frases
import ar.correlativas.mecanica.data.Materia
import ar.correlativas.mecanica.data.ModoTema
import ar.correlativas.mecanica.data.Plan
import ar.correlativas.mecanica.data.Progreso
import ar.correlativas.mecanica.data.RANGOS
import ar.correlativas.mecanica.data.Tipo
import ar.correlativas.mecanica.data.formatearHoras
import ar.correlativas.mecanica.data.porcentajeCarrera
import ar.correlativas.mecanica.data.rangoPara
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import androidx.glance.color.ColorProvider as ColorDiaNoche

class CorrelativasWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CorrelativasWidget()
}

/** Colores del widget para un tema. En "según el sistema" cada color tiene versión de día y de noche. */
class ColoresWidget(
    val fondo: Int,
    val texto: ColorProvider,
    val texto2: ColorProvider,
    val apagado: ColorProvider,
    val disponible: ColorProvider,
    val rendir: ColorProvider,
    val oro: ColorProvider,
    val rosa: ColorProvider,
    val violeta: ColorProvider,
    val pista: ColorProvider,
    val linea: ColorProvider,
    val insignia: ColorProvider,
) {
    companion object {
        private class Par(val claro: Color, val oscuro: Color)

        private val texto = Par(Color(0xFF141A38), Color(0xFFEEF1FF))
        private val texto2 = Par(Color(0xFF515B84), Color(0xFFA3ACD9))
        private val apagado = Par(Color(0xFF8790B5), Color(0xFF69729E))
        private val disponible = Par(Color(0xFF0791B0), Color(0xFF22E1F0))
        private val rendir = Par(Color(0xFF138A43), Color(0xFF7CFF9B))
        private val oro = Par(Color(0xFFC98600), Color(0xFFFFCB3D))
        private val rosa = Par(Color(0xFFD1206F), Color(0xFFFF4D9D))
        private val violeta = Par(Color(0xFF6A3BE4), Color(0xFFB79BFF))
        private val pista = Par(Color(0x1A141A38), Color(0x26FFFFFF))
        private val linea = Par(Color(0x1F141A38), Color(0x22FFFFFF))
        private val insignia = Par(Color(0x26C98600), Color(0x33FFCB3D))

        fun para(modo: ModoTema): ColoresWidget {
            fun c(p: Par): ColorProvider = when (modo) {
                ModoTema.CLARO -> ColorProvider(p.claro)
                ModoTema.OSCURO -> ColorProvider(p.oscuro)
                ModoTema.SISTEMA -> ColorDiaNoche(day = p.claro, night = p.oscuro)
            }
            return ColoresWidget(
                fondo = when (modo) {
                    ModoTema.CLARO -> R.drawable.widget_fondo_claro
                    ModoTema.OSCURO -> R.drawable.widget_fondo
                    ModoTema.SISTEMA -> R.drawable.widget_fondo_sistema
                },
                texto = c(texto), texto2 = c(texto2), apagado = c(apagado), disponible = c(disponible),
                rendir = c(rendir), oro = c(oro), rosa = c(rosa), violeta = c(violeta), pista = c(pista),
                linea = c(linea), insignia = c(insignia),
            )
        }
    }
}

private val Fuente = FontFamily("sans-serif-condensed")

class CorrelativasWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val plan = Plan.cargar(context)
        val frases = Frases.cargar(context)
        val progreso = Progreso(context)
        val ajustes = Ajustes(context)
        val inicial = progreso.estados.first()
        val prefsIniciales = ajustes.preferencias.first()
        provideContent {
            val estados by progreso.estados.collectAsState(initial = inicial)
            val prefs by ajustes.preferencias.collectAsState(initial = prefsIniciales)
            val frase = frases.paraWidget(System.currentTimeMillis() / 3_600_000L)
            ContenidoWidget(Avance(plan, estados), ColoresWidget.para(prefs.tema), frase)
        }
    }
}

@Composable
fun ContenidoWidget(avance: Avance, colores: ColoresWidget, frase: Frase?) {
    val abrir = actionStartActivity<MainActivity>()
    val alto = LocalSize.current.height.value
    // La frase se muestra si el widget es alto; las materias ocupan el resto (~20dp por línea).
    val conFrase = frase != null && alto >= 280f
    val altoFrase = if (conFrase) 74f else 0f
    val lineas = ((alto - 140f - 40f - altoFrase) / 20f).toInt().coerceAtLeast(0)
    val (nCursar, nRendir) = repartir(lineas, avance.cursables.size, avance.rendibles.size)

    Column(
        GlanceModifier
            .fillMaxSize()
            .background(ImageProvider(colores.fondo))
            .cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .clickable(abrir),
    ) {
        Column(GlanceModifier.fillMaxWidth()) {
            Encabezado(avance, colores)
        }
        Seccion(colores, R.drawable.ic_rayo, colores.disponible, "PODÉS CURSAR", avance.cursables, nCursar, "Nada por ahora")
        Seccion(colores, R.drawable.ic_llama, colores.rendir, "PODÉS RENDIR", avance.rendibles, nRendir, "Regularizá materias para rendir")
        if (conFrase && frase != null) {
            Spacer(GlanceModifier.defaultWeight())
            Column(GlanceModifier.fillMaxWidth()) {
                Box(GlanceModifier.fillMaxWidth().height(1.dp).background(colores.linea)) {}
                Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                    Icono(R.drawable.ic_comillas, colores.violeta, 14)
                    Spacer(GlanceModifier.width(6.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(
                            frase.texto,
                            maxLines = 3,
                            style = TextStyle(color = colores.texto, fontSize = 13.sp, fontStyle = FontStyle.Italic, fontFamily = Fuente),
                        )
                        Text(
                            "— ${frase.autor}",
                            maxLines = 1,
                            style = TextStyle(color = colores.violeta, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Fuente),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Encabezado(avance: Avance, colores: ColoresWidget) {
    val pct = avance.porcentajeCarrera
    val rango = rangoPara(pct)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier.size(36.dp).background(colores.insignia).cornerRadius(18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icono(R.drawable.ic_medalla, colores.oro, 20)
        }
        Column(GlanceModifier.defaultWeight().padding(start = 10.dp)) {
            Text(
                "RANGO ${rango.numero}/${RANGOS.size}",
                style = TextStyle(color = colores.disponible, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = Fuente),
            )
            Text(
                rango.nombre,
                maxLines = 1,
                style = TextStyle(color = colores.texto, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Fuente),
            )
        }
        Text(
            "${(pct * 100).roundToInt()}%",
            style = TextStyle(color = colores.texto, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = Fuente),
        )
    }
    Spacer(GlanceModifier.height(8.dp))
    LinearProgressIndicator(
        progress = pct.toFloat(),
        modifier = GlanceModifier.fillMaxWidth().height(7.dp).cornerRadius(4.dp),
        color = colores.disponible,
        backgroundColor = colores.pista,
    )
    Spacer(GlanceModifier.height(10.dp))
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Dato(colores, R.drawable.ic_estrella, colores.oro, "${avance.aprobadasObligatorias}/${avance.plan.obligatorias.size}", GlanceModifier.defaultWeight())
        Dato(
            colores, R.drawable.ic_reloj, colores.rosa,
            "${formatearHoras(avance.horasElectivas)}/${formatearHoras(Plan.HORAS_ELECTIVAS_REQUERIDAS)} hs",
            GlanceModifier.defaultWeight(),
        )
        Dato(colores, R.drawable.ic_llama, colores.rendir, "${avance.rendibles.size} finales", GlanceModifier.defaultWeight())
    }
    Spacer(GlanceModifier.height(10.dp))
    Box(GlanceModifier.fillMaxWidth().height(1.dp).background(colores.linea)) {}
}

/** Reparte las líneas disponibles entre cursar y rendir, sin dejar lugar vacío. */
internal fun repartir(lineas: Int, cursar: Int, rendir: Int): Pair<Int, Int> {
    var c = minOf(cursar, (lineas + 1) / 2)
    var r = minOf(rendir, lineas - c)
    c = minOf(cursar, lineas - r)
    r = minOf(rendir, lineas - c)
    return c to r
}

@Composable
private fun Icono(res: Int, color: ColorProvider, tam: Int) {
    Image(
        ImageProvider(res),
        contentDescription = null,
        modifier = GlanceModifier.size(tam.dp),
        colorFilter = ColorFilter.tint(color),
    )
}

@Composable
private fun Dato(colores: ColoresWidget, icono: Int, color: ColorProvider, texto: String, modifier: GlanceModifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icono(icono, color, 15)
        Spacer(GlanceModifier.width(5.dp))
        Text(texto, maxLines = 1, style = TextStyle(color = colores.texto, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = Fuente))
    }
}

@Composable
private fun Seccion(
    colores: ColoresWidget,
    icono: Int,
    color: ColorProvider,
    titulo: String,
    materias: List<Materia>,
    mostrar: Int,
    vacio: String,
) {
    // Glance admite hasta 10 hijos por Column: cada sección va en su propia columna.
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icono(icono, color, 14)
            Spacer(GlanceModifier.width(6.dp))
            Text(
                "$titulo · ${materias.size}",
                style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Fuente),
            )
        }
        if (materias.isEmpty()) {
            Text(vacio, style = TextStyle(color = colores.apagado, fontSize = 13.sp, fontFamily = Fuente))
            return@Column
        }
        val visibles = materias.take(mostrar.coerceAtMost(8))
        visibles.forEachIndexed { i, m ->
            val ultima = i == visibles.lastIndex && materias.size > visibles.size
            Row(GlanceModifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    prefijo(m),
                    modifier = GlanceModifier.width(30.dp),
                    style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Fuente),
                )
                Text(
                    m.nombre,
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = colores.texto, fontSize = 14.sp, fontFamily = Fuente),
                )
                if (ultima) {
                    Text(
                        " +${materias.size - visibles.size}",
                        style = TextStyle(color = colores.texto2, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Fuente),
                    )
                }
            }
        }
        if (visibles.isEmpty()) {
            Text("${materias.size} materias · abrí la app", style = TextStyle(color = colores.texto2, fontSize = 13.sp, fontFamily = Fuente))
        }
    }
}

private fun prefijo(m: Materia) = when (m.tipo) {
    Tipo.OBLIGATORIA -> m.id.toString()
    Tipo.PPS -> "PPS"
    Tipo.ELECTIVA -> "E"
}
