package ar.correlativas.mecanica.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import ar.correlativas.mecanica.MainActivity
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.Materia
import ar.correlativas.mecanica.data.Plan
import ar.correlativas.mecanica.data.Progreso
import ar.correlativas.mecanica.data.Tipo
import ar.correlativas.mecanica.data.formatearHoras
import kotlinx.coroutines.flow.first

class CorrelativasWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CorrelativasWidget()
}

/** Colores de la web, en versión clara y oscura. */
private object Colores {
    val papel = ColorProvider(day = Color(0xFFF3F5F8), night = Color(0xFF0C2340))
    val tinta = ColorProvider(day = Color(0xFF18222F), night = Color(0xFFE8F1FB))
    val tinta2 = ColorProvider(day = Color(0xFF506074), night = Color(0xFFA7BCD4))
    val linea = ColorProvider(day = Color(0xFFC9D2DC), night = Color(0xFF2A4B71))
    val acento = ColorProvider(day = Color(0xFF1F5FAD), night = Color(0xFF8DCBFF))
    val apr = ColorProvider(day = Color(0xFF1A7743), night = Color(0xFF7EDFA6))
    val reg = ColorProvider(day = Color(0xFF9E5B00), night = Color(0xFFFFC063))
    val pista = ColorProvider(day = Color(0xFFE7ECF1), night = Color(0xFF1C3A5E))
}

class CorrelativasWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val plan = Plan.cargar(context)
        val progreso = Progreso(context)
        val inicial = progreso.estados.first()
        provideContent {
            val estados by progreso.estados.collectAsState(initial = inicial)
            Contenido(Avance(plan, estados))
        }
    }

    @Composable
    private fun Contenido(avance: Avance) {
        val abrir = actionStartActivity<MainActivity>()
        val plan = avance.plan
        Column(
            GlanceModifier
                .fillMaxSize()
                .background(Colores.papel)
                .cornerRadius(16.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .clickable(abrir),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Correlativas",
                    style = TextStyle(color = Colores.tinta, fontSize = 17.sp, fontWeight = FontWeight.Bold),
                )
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    "${avance.cursables.size} cursar · ${avance.rendibles.size} rendir",
                    style = TextStyle(color = Colores.tinta2, fontSize = 13.sp),
                )
            }
            Spacer(GlanceModifier.height(8.dp))
            Medidor(
                "Aprobadas",
                "${avance.aprobadasObligatorias} de ${plan.obligatorias.size}",
                avance.aprobadasObligatorias / plan.obligatorias.size.toFloat(),
                Colores.apr,
            )
            Spacer(GlanceModifier.height(6.dp))
            Medidor(
                "Electivas",
                "${formatearHoras(avance.horasElectivas)} de ${formatearHoras(Plan.HORAS_ELECTIVAS_REQUERIDAS)} hs",
                (avance.horasElectivas / Plan.HORAS_ELECTIVAS_REQUERIDAS).toFloat().coerceAtMost(1f),
                Colores.reg,
            )
            Spacer(GlanceModifier.height(10.dp))
            Box(GlanceModifier.fillMaxWidth().height(1.dp).background(Colores.linea)) {}
            Lista(avance, abrir)
        }
    }

    @Composable
    private fun Medidor(titulo: String, valor: String, progreso: Float, color: ColorProvider) {
        Column(GlanceModifier.fillMaxWidth()) {
            Row(GlanceModifier.fillMaxWidth()) {
                Text(titulo, style = TextStyle(color = Colores.tinta2, fontSize = 12.sp))
                Spacer(GlanceModifier.defaultWeight())
                Text(valor, style = TextStyle(color = Colores.tinta, fontSize = 12.sp, fontWeight = FontWeight.Medium))
            }
            LinearProgressIndicator(
                progress = progreso,
                modifier = GlanceModifier.fillMaxWidth().height(5.dp).padding(top = 2.dp),
                color = color,
                backgroundColor = Colores.pista,
            )
        }
    }

    private sealed interface Fila {
        data class Titulo(val texto: String, val color: ColorProvider) : Fila
        data class Item(val materia: Materia) : Fila
        data class Vacio(val texto: String) : Fila
    }

    @Composable
    private fun Lista(avance: Avance, abrir: Action) {
        val filas = buildList {
            add(Fila.Titulo("Podés cursar (${avance.cursables.size})", Colores.acento))
            if (avance.cursables.isEmpty()) add(Fila.Vacio("Nada por ahora"))
            avance.cursables.forEach { add(Fila.Item(it)) }
            add(Fila.Titulo("Podés rendir (${avance.rendibles.size})", Colores.apr))
            if (avance.rendibles.isEmpty()) add(Fila.Vacio("Regularizá materias para rendir finales"))
            avance.rendibles.forEach { add(Fila.Item(it)) }
        }
        LazyColumn(GlanceModifier.fillMaxWidth()) {
            items(filas) { fila ->
                val mod = GlanceModifier.fillMaxWidth().clickable(abrir)
                when (fila) {
                    is Fila.Titulo -> Text(
                        fila.texto,
                        modifier = mod.padding(top = 8.dp, bottom = 2.dp),
                        style = TextStyle(color = fila.color, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    )
                    is Fila.Vacio -> Text(
                        fila.texto,
                        modifier = mod.padding(vertical = 2.dp),
                        style = TextStyle(color = Colores.tinta2, fontSize = 13.sp),
                    )
                    is Fila.Item -> Row(mod.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            prefijo(fila.materia),
                            modifier = GlanceModifier.width(32.dp),
                            style = TextStyle(color = Colores.tinta2, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        )
                        Text(
                            fila.materia.nombre,
                            maxLines = 1,
                            style = TextStyle(color = Colores.tinta, fontSize = 14.sp),
                        )
                    }
                }
            }
        }
    }

    private fun prefijo(m: Materia) = when (m.tipo) {
        Tipo.OBLIGATORIA -> m.id.toString()
        Tipo.PPS -> "PPS"
        Tipo.ELECTIVA -> "E"
    }
}
