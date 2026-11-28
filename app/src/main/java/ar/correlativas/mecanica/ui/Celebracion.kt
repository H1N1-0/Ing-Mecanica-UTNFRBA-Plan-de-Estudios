package ar.correlativas.mecanica.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.height
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.Materia
import ar.correlativas.mecanica.data.estadoNodo
import kotlin.math.sin
import kotlin.random.Random

/**
 * "¡Desbloqueaste X materias!" tras un cambio. Con confeti cuando se completa
 * un nivel o se consigue un logro.
 */
@Composable
fun Celebracion(d: Desbloqueo, avance: Avance, onCerrar: () -> Unit, onAbrir: (Int) -> Unit) {
    val haptico = LocalHapticFeedback.current
    // Cómo se festeja depende del estilo: confeti (Gamificado, con logros), engranajes que giran
    // (Plano técnico, siempre) o una entrada suave (Material).
    val mov = C.movimiento
    val confeti = mov.festejo == Festejo.CONFETI && d.logros.isNotEmpty()
    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val entrada = remember(d) { Animatable(0f) }
        LaunchedEffect(d) {
            haptico.performHapticFeedback(HapticFeedbackType.LongPress)
            entrada.animateTo(
                1f,
                if (mov.rebote) spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
                else tween(mov.normal, easing = FastOutSlowInEasing),
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)).clickable(onClick = onCerrar, indication = null, interactionSource = null), contentAlignment = Alignment.Center) {
            if (confeti) Confeti(Modifier.fillMaxSize())
            Column(
                Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    .graphicsLayer {
                        val v = entrada.value
                        alpha = v.coerceIn(0f, 1f)
                        scaleX = 0.85f + 0.15f * v
                        scaleY = 0.85f + 0.15f * v
                    }
                    .clip(C.formas.grande)
                    .background(C.fondoAlto)
                    .border(1.5.dp, Brush.linearGradient(listOf(C.disponible, C.violeta, C.oro)), C.formas.grande)
                    .clickable(enabled = false) {}
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (mov.festejo == Festejo.ENGRANAJES) EngranajesGirando(Modifier.fillMaxWidth().height(110.dp))
                Encabezado(d, avance)
                if (d.materias.isNotEmpty()) {
                    Subtitulo("Materias nuevas para cursar", C.disponible, R.drawable.ic_rayo)
                    d.materias.forEachIndexed { i, m -> Entrada(m, avance, i, onAbrir) }
                }
                if (d.finales.isNotEmpty()) {
                    Subtitulo("Finales que ya podés rendir", C.rendir, R.drawable.ic_llama)
                    d.finales.forEachIndexed { i, m -> Entrada(m, avance, d.materias.size + i, onAbrir) }
                }
                if (d.logros.isNotEmpty()) {
                    Subtitulo(if (d.logros.size == 1) "Logro conseguido" else "Logros conseguidos", C.oro, R.drawable.ic_trofeo)
                    d.logros.forEach { l ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Insignia(l.icono, true, tam = 46.dp)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(l.titulo, color = C.oro, fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(l.descripcion, color = C.texto2, fontFamily = C.fuente, fontSize = 13.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                }
                d.frase?.let { f ->
                    Column(
                        Modifier
                            .padding(top = 18.dp)
                            .fillMaxWidth()
                            .clip(C.formas.media)
                            .background(C.violeta.copy(alpha = 0.10f))
                            .border(1.dp, C.violeta.copy(alpha = 0.3f), C.formas.media)
                            .padding(14.dp),
                    ) {
                        Text(
                            f.texto,
                            Modifier.padding(top = 6.dp),
                            color = C.texto,
                            fontFamily = C.fuente,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                        )
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Medallon(f.autor, f.area, tam = 40.dp)
                            Text(f.autor, Modifier.padding(start = 12.dp), color = colorArea(f.area), fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
                Box(
                    Modifier
                        .padding(top = 18.dp)
                        .fillMaxWidth()
                        .clip(C.formas.media)
                        .background(if (mov.festejo == Festejo.CONFETI) Brush.horizontalGradient(listOf(C.disponible, C.violeta)) else Brush.linearGradient(listOf(C.disponible, C.disponible)))
                        .clickable(role = Role.Button, onClick = onCerrar)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("¡Vamos!", color = C.sobreAcento, fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
        }
    }
}

@Composable
private fun Encabezado(d: Desbloqueo, avance: Avance) {
    val nivel = d.nivelesCompletos.maxOrNull()
    val (icono, color) = when {
        nivel != null -> R.drawable.ic_trofeo to C.oro
        d.materias.isNotEmpty() -> R.drawable.ic_rayo to C.disponible
        d.finales.isNotEmpty() -> R.drawable.ic_llama to C.rendir
        d.logros.isNotEmpty() -> R.drawable.ic_medalla to C.oro
        else -> R.drawable.ic_estrella to C.oro
    }
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(86.dp)) {
        Box(Modifier.size(86.dp).clip(CircleShape).background(color.copy(alpha = 0.14f)))
        Box(Modifier.size(64.dp).clip(CircleShape).background(color.copy(alpha = 0.22f)).border(2.dp, color, CircleShape), contentAlignment = Alignment.Center) {
            Ic(icono, color, tam = 34.dp)
        }
    }
    val n = d.materias.size
    Text(
        when {
            nivel != null -> "¡Nivel $nivel completo!"
            n == 1 -> "¡Desbloqueaste 1 materia nueva!"
            n > 1 -> "¡Desbloqueaste $n materias nuevas!"
            d.finales.isNotEmpty() -> "¡Nuevo final disponible!"
            d.logros.isNotEmpty() -> "¡Logro conseguido!"
            d.materia.id == avance.plan.pps.id -> "¡PPS acreditada!"
            else -> "¡Materia aprobada!"
        },
        Modifier.padding(top = 12.dp).semantics { heading() },
        color = C.texto,
        fontFamily = C.fuente,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 28.sp,
        textAlign = TextAlign.Center,
    )
    val accion = when (avance.estado(d.materia.id)) {
        Estado.APROBADA -> if (d.materia.id == avance.plan.pps.id) "Acreditaste" else "Aprobaste"
        Estado.PROMOCIONADA -> "Promocionaste"
        Estado.REGULARIZADA -> "Regularizaste"
        Estado.CURSANDO -> "Empezaste a cursar"
        Estado.SIN_CURSAR -> "Cambiaste"
    }
    Text(
        "$accion ${d.materia.nombre}",
        Modifier.padding(top = 4.dp),
        color = C.texto2,
        fontFamily = C.fuente,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun Subtitulo(texto: String, color: Color, icono: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Ic(icono, color, tam = 16.dp)
        Text(texto.uppercase(), Modifier.padding(start = 6.dp), color = color, fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
    }
}

@Composable
private fun Entrada(m: Materia, avance: Avance, indice: Int, onAbrir: (Int) -> Unit) {
    val anim = remember(m.id) { Animatable(0f) }
    LaunchedEffect(m.id) {
        kotlinx.coroutines.delay(250L + 90L * indice)
        anim.animateTo(1f, tween(350))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = anim.value
                translationX = (1 - anim.value) * 40.dp.toPx()
            }
            .clip(C.formas.chica)
            .background(C.tarjeta)
            .clickable { onAbrir(m.id) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(top = 4.dp, end = 4.dp)) { Nodo(m, avance.estadoNodo(m), tam = 40.dp) }
        Column(Modifier.padding(start = 10.dp)) {
            Text(m.nombre, color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp)
            Text(m.nivel?.let { "Nivel $it" } ?: "Electiva", color = C.texto2, fontFamily = C.fuente, fontSize = 12.sp)
        }
    }
    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
}

private data class Particula(val x: Float, val vel: Float, val fase: Float, val giro: Float, val color: Color, val ancho: Float, val retraso: Float)

@Composable
private fun Confeti(modifier: Modifier) {
    val colores = listOf(C.oro, C.disponible, C.violeta, C.rosa, C.rendir, C.regular)
    val particulas = remember {
        val r = Random(7)
        List(90) {
            Particula(r.nextFloat(), 0.6f + r.nextFloat() * 0.6f, r.nextFloat() * 6.28f, r.nextFloat() * 720f - 360f,
                colores[r.nextInt(colores.size)], 6f + r.nextFloat() * 6f, r.nextFloat() * 0.35f)
        }
    }
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) { t.animateTo(1f, tween(3200, easing = LinearEasing)) }
    Canvas(modifier) {
        val v = t.value
        particulas.forEach { p ->
            val local = ((v - p.retraso) / (1 - p.retraso)).coerceIn(0f, 1f)
            if (local <= 0f || local >= 1f) return@forEach
            val y = -30f + local * p.vel * size.height * 1.3f
            val x = p.x * size.width + sin(p.fase + local * 9f) * 24.dp.toPx()
            val alfa = if (local > 0.8f) (1 - local) / 0.2f else 1f
            rotate(p.giro * local, Offset(x, y)) {
                drawRect(p.color.copy(alpha = alfa), Offset(x - p.ancho, y - p.ancho * 0.4f), Size(p.ancho * 2 * density / 2, p.ancho * 0.8f * density / 2))
            }
        }
    }
}
