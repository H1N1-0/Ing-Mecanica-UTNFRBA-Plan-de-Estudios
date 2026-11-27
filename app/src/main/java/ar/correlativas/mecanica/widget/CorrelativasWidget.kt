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
import ar.correlativas.mecanica.data.Estilo
import ar.correlativas.mecanica.ui.Paleta
import ar.correlativas.mecanica.ui.esquemaMaterial
import ar.correlativas.mecanica.ui.paletaFija
import ar.correlativas.mecanica.ui.paletaMaterial
import androidx.compose.ui.graphics.lerp
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

/**
 * Colores, fuentes y fondo del widget para un estilo y un tema. Salen de las mismas paletas que la
 * app (Tema.kt), así el widget sigue al estilo elegido. En "según el sistema" cada color tiene
 * versión de día y de noche.
 */
class ColoresWidget(
    /** Drawable del fondo y, si hay, el color con que se tiñe (los de Gamificado ya traen degradé). */
    val fondo: Int,
    val tinteFondo: ColorProvider?,
    /** Margen de lámina (solo Plano técnico). */
    val marco: ColorProvider?,
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
    val fuente: FontFamily,
    /** Números y rótulos (monoespaciada en Plano técnico). */
    val fuenteDatos: FontFamily,
    val radio: Int,
) {
    companion object {
        fun para(modo: ModoTema, estilo: Estilo, context: Context): ColoresWidget {
            fun paleta(oscuro: Boolean) =
                if (estilo == Estilo.MATERIAL) paletaMaterial(esquemaMaterial(context, oscuro), oscuro) else paletaFija(estilo, oscuro)
            val claro = paleta(false)
            val oscuro = paleta(true)
            fun c(elegir: (Paleta) -> Color): ColorProvider = when (modo) {
                ModoTema.CLARO -> ColorProvider(elegir(claro))
                ModoTema.OSCURO -> ColorProvider(elegir(oscuro))
                ModoTema.SISTEMA -> ColorDiaNoche(day = elegir(claro), night = elegir(oscuro))
            }
            return ColoresWidget(
                fondo = when (estilo) {
                    Estilo.GAMIFICADO -> when (modo) {
                        ModoTema.CLARO -> R.drawable.widget_fondo_claro
                        ModoTema.OSCURO -> R.drawable.widget_fondo
                        ModoTema.SISTEMA -> R.drawable.widget_fondo_sistema
                    }
                    Estilo.PLANO -> R.drawable.widget_forma_plano
                    Estilo.MATERIAL -> R.drawable.widget_forma
                },
                tinteFondo = when (estilo) {
                    Estilo.GAMIFICADO -> null
                    Estilo.PLANO -> c { it.fondo }
                    Estilo.MATERIAL -> c { it.fondoAlto }
                },
                marco = if (estilo == Estilo.PLANO) c { it.tinta.copy(alpha = 0.55f) } else null,
                texto = c { it.texto },
                texto2 = c { it.texto2 },
                apagado = c { it.apagado },
                disponible = c { it.disponible },
                rendir = c { it.rendir },
                oro = c { it.oro },
                rosa = c { it.rosa },
                // El violeta gamificado oscuro es muy profundo para texto chico sobre el degradé: se aclara.
                violeta = c { if (estilo == Estilo.GAMIFICADO && it.oscuro) lerp(it.violeta, Color.White, 0.35f) else it.violeta },
                pista = c { it.pista },
                linea = c { it.texto.copy(alpha = 0.13f) },
                insignia = c { it.oro.copy(alpha = 0.2f) },
                fuente = when (estilo) {
                    Estilo.MATERIAL -> FontFamily.SansSerif
                    else -> FontFamily("sans-serif-condensed")
                },
                fuenteDatos = when (estilo) {
                    Estilo.PLANO -> FontFamily.Monospace
                    Estilo.MATERIAL -> FontFamily.SansSerif
                    Estilo.GAMIFICADO -> FontFamily("sans-serif-condensed")
                },
                radio = when (estilo) {
                    Estilo.PLANO -> 6
                    else -> 24
                },
            )
        }
    }
}

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
            ContenidoWidget(Avance(plan, estados), ColoresWidget.para(prefs.tema, prefs.estilo, context), frase)
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

    val fondo = GlanceModifier
        .fillMaxSize()
        .background(ImageProvider(colores.fondo), colorFilter = colores.tinteFondo?.let { ColorFilter.tint(it) })
        .cornerRadius(colores.radio.dp)
    Box(fondo) {
        val marco = colores.marco
        val tinte = colores.tinteFondo
        if (marco != null && tinte != null) {
            // Margen de la lámina (Plano técnico): un recuadro de 1 dp hecho con cajas de color liso,
            // que se ve igual en cualquier launcher.
            Box(GlanceModifier.fillMaxSize().padding(5.dp)) {
                Box(GlanceModifier.fillMaxSize().background(marco).padding(1.dp)) {
                    Box(GlanceModifier.fillMaxSize().background(tinte)) {
                        Cuerpo(avance, colores, frase, conFrase, nCursar, nRendir, abrir)
                    }
                }
            }
        } else {
            Cuerpo(avance, colores, frase, conFrase, nCursar, nRendir, abrir)
        }
    }
}

@Composable
private fun Cuerpo(
    avance: Avance,
    colores: ColoresWidget,
    frase: Frase?,
    conFrase: Boolean,
    nCursar: Int,
    nRendir: Int,
    abrir: androidx.glance.action.Action,
) {
    Column(
        GlanceModifier
            .fillMaxSize()
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
                            style = TextStyle(color = colores.texto, fontSize = 13.sp, fontStyle = FontStyle.Italic, fontFamily = colores.fuente),
                        )
                        Text(
                            "— ${frase.autor}",
                            maxLines = 1,
                            style = TextStyle(color = colores.violeta, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = colores.fuente),
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
                style = TextStyle(color = colores.disponible, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = colores.fuenteDatos),
            )
            Text(
                rango.nombre,
                maxLines = 1,
                style = TextStyle(color = colores.texto, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = colores.fuente),
            )
        }
        Text(
            "${(pct * 100).roundToInt()}%",
            style = TextStyle(color = colores.texto, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = colores.fuenteDatos),
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
        Text(texto, maxLines = 1, style = TextStyle(color = colores.texto, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = colores.fuente))
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
                style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = colores.fuente),
            )
        }
        if (materias.isEmpty()) {
            Text(vacio, style = TextStyle(color = colores.apagado, fontSize = 13.sp, fontFamily = colores.fuente))
            return@Column
        }
        val visibles = materias.take(mostrar.coerceAtMost(8))
        visibles.forEachIndexed { i, m ->
            val ultima = i == visibles.lastIndex && materias.size > visibles.size
            Row(GlanceModifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    prefijo(m),
                    modifier = GlanceModifier.width(30.dp),
                    style = TextStyle(color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = colores.fuenteDatos),
                )
                Text(
                    m.nombre,
                    maxLines = 1,
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = colores.texto, fontSize = 14.sp, fontFamily = colores.fuente),
                )
                if (ultima) {
                    Text(
                        " +${materias.size - visibles.size}",
                        style = TextStyle(color = colores.texto2, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = colores.fuente),
                    )
                }
            }
        }
        if (visibles.isEmpty()) {
            Text("${materias.size} materias · abrí la app", style = TextStyle(color = colores.texto2, fontSize = 13.sp, fontFamily = colores.fuente))
        }
    }
}

private fun prefijo(m: Materia) = when (m.tipo) {
    Tipo.OBLIGATORIA -> m.id.toString()
    Tipo.PPS -> "PPS"
    Tipo.ELECTIVA -> "E"
}
