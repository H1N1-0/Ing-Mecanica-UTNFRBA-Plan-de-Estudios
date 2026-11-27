package ar.correlativas.mecanica.ui

import android.annotation.SuppressLint
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.IconoLogro
import ar.correlativas.mecanica.data.Perfil
import ar.correlativas.mecanica.data.TestVocacional
import ar.correlativas.mecanica.data.estadoNodo
import ar.correlativas.mecanica.data.formatearHoras
import kotlinx.coroutines.delay

/** Ícono de un perfil a partir del nombre que trae perfiles.json. */
@SuppressLint("DiscouragedApi")
@Composable
fun iconoPerfil(p: Perfil): Int {
    val ctx = LocalContext.current
    return remember(p.icono) {
        ctx.resources.getIdentifier("ic_${p.icono}", "drawable", ctx.packageName).takeIf { it != 0 } ?: R.drawable.ic_engranaje
    }
}

fun colorPerfil(p: Perfil) = Color(p.color)

@Composable
fun BarraSuperior(titulo: String, onVolver: () -> Unit, extra: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .semantics { contentDescription = "Volver" }
                .clickable(role = Role.Button, onClick = onVolver),
            contentAlignment = Alignment.Center,
        ) { Ic(R.drawable.ic_flecha, C.texto, tam = 24.dp) }
        Text(titulo, Modifier.padding(start = 4.dp).weight(1f).semantics { heading() }, color = C.texto, style = MaterialTheme.typography.titleLarge)
        extra()
    }
}

// ---------------------------------------------------------------- preguntas

@Composable
fun PantallaTest(test: TestVocacional, onTerminar: (List<Int>) -> Unit, onSalir: () -> Unit) {
    var indice by rememberSaveable { mutableIntStateOf(0) }
    val respuestas = remember { mutableStateListOf<Int>().apply { repeat(test.preguntas.size) { add(-1) } } }
    var avanzando by remember { mutableStateOf(false) }
    val total = test.preguntas.size

    fun volver() { if (indice > 0) indice-- else onSalir() }
    BackHandler { volver() }

    LaunchedEffect(avanzando) {
        if (avanzando) {
            delay(280)
            if (indice < total - 1) indice++ else onTerminar(respuestas.toList())
            avanzando = false
        }
    }

    Column(Modifier.fillMaxSize().background(C.fondo)) {
        BarraSuperior(test.titulo, onVolver = ::volver)
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "PREGUNTA ${indice + 1} DE $total",
                    Modifier.weight(1f),
                    color = C.violeta,
                    fontFamily = Chakra,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp,
                )
                Ic(R.drawable.ic_brujula, C.violeta, tam = 18.dp)
            }
            Barra((indice + 1) / total.toFloat(), C.violeta, Modifier.fillMaxWidth().padding(top = 8.dp), alto = 8.dp)
        }
        AnimatedContent(
            targetState = indice,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(300)) { it * dir / 3 } + fadeIn(tween(300))) togetherWith
                    (slideOutHorizontally(tween(250)) { -it * dir / 3 } + fadeOut(tween(200)))
            },
            label = "pregunta",
            modifier = Modifier.fillMaxSize(),
        ) { i ->
            val pregunta = test.preguntas[i]
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    pregunta.texto,
                    Modifier.padding(top = 8.dp, bottom = 10.dp).semantics { heading() },
                    color = C.texto,
                    style = MaterialTheme.typography.headlineSmall,
                    lineHeight = 30.sp,
                )
                pregunta.opciones.forEachIndexed { j, op ->
                    val elegida = respuestas[i] == j
                    val color = listOf(C.disponible, C.violeta, C.rosa, C.oro, C.rendir)[j % 5]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (elegida) color.copy(alpha = 0.16f) else C.tarjeta)
                            .border(if (elegida) 2.dp else 1.dp, if (elegida) color else C.borde, RoundedCornerShape(16.dp))
                            .clickable(enabled = !avanzando, role = Role.RadioButton) {
                                respuestas[i] = j
                                avanzando = true
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(if (elegida) color else color.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${'A' + j}",
                                color = if (elegida) (if (C.oscuro) C.fondo else Color.White) else color,
                                fontFamily = Chakra,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                        Text(
                            op.texto,
                            Modifier.padding(start = 14.dp).weight(1f),
                            color = C.texto,
                            fontFamily = Chakra,
                            fontWeight = FontWeight.Medium,
                            fontSize = 16.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- resultado

@Composable
fun PantallaPerfil(
    test: TestVocacional,
    porcentajes: Map<String, Int>,
    avance: Avance,
    logroNuevo: Boolean,
    onRepetir: () -> Unit,
    onCerrar: () -> Unit,
    onAbrir: (Int) -> Unit,
) {
    BackHandler(onBack = onCerrar)
    val ordenados = porcentajes.toList().sortedByDescending { it.second }.mapNotNull { (id, pct) -> test[id]?.let { it to pct } }
    val (principal, pctPrincipal) = ordenados.first()
    val color = colorPerfil(principal)

    LazyColumn(
        Modifier.fillMaxSize().background(C.fondo),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { BarraSuperior("Tu perfil", onVolver = onCerrar) }
        if (logroNuevo) {
            item {
                Row(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(C.oro.copy(alpha = 0.14f))
                        .border(1.dp, C.oro.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Insignia(IconoLogro.BRUJULA, true, tam = 44.dp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("¡LOGRO DESBLOQUEADO!", color = C.oro, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
                        Text("Conocete", color = C.texto, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                }
            }
        }
        item {
            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(color.copy(alpha = 0.95f), Color(0xFF1B1F5E))))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(92.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)).border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) { Ic(iconoPerfil(principal), Color.White, tam = 48.dp) }
                Text("SOS", Modifier.padding(top = 12.dp), color = C.heroeTexto2, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 13.sp, letterSpacing = 3.sp)
                Text(
                    principal.nombre,
                    Modifier.semantics { heading() },
                    color = C.heroeTexto,
                    fontFamily = Chakra,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    lineHeight = 32.sp,
                    textAlign = TextAlign.Center,
                )
                Text("$pctPrincipal% de afinidad", color = C.heroeTexto2, fontFamily = Chakra, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                Text(
                    principal.descripcion,
                    Modifier.padding(top = 12.dp),
                    color = C.heroeTexto,
                    fontFamily = Chakra,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        item {
            Bloque("En qué trabaja", R.drawable.ic_engranaje, color) {
                principal.trabajo.forEach { t ->
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 1.dp).size(20.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Ic(R.drawable.ic_check, color, tam = 13.dp)
                        }
                        Text(t, Modifier.padding(start = 10.dp), color = C.texto, fontFamily = Chakra, fontSize = 15.sp, lineHeight = 19.sp)
                    }
                }
            }
        }
        item {
            Bloque("Referente", R.drawable.ic_medalla, C.oro) {
                Text(principal.referente.nombre, color = C.texto, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(principal.referente.descripcion, Modifier.padding(top = 4.dp), color = C.texto2, fontFamily = Chakra, fontSize = 14.5.sp, lineHeight = 19.sp)
            }
        }
        item {
            Bloque("Electivas para vos", R.drawable.ic_ramas, C.rosa) {
                principal.electivas.mapNotNull { avance.plan[it] }.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onAbrir(m.id) }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.padding(top = 4.dp, end = 4.dp)) { Nodo(m, avance.estadoNodo(m), tam = 38.dp, animado = false) }
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(m.nombre, color = C.texto, fontFamily = Chakra, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp)
                            Text(
                                listOfNotNull(m.nivel?.let { "Nivel $it" }, m.horasAnuales?.let { "${formatearHoras(it)} hs" }, textoEstado(avance.estadoNodo(m))).joinToString(" · "),
                                color = C.texto2,
                                fontFamily = Chakra,
                                fontSize = 13.sp,
                            )
                        }
                        Text("›", color = C.apagado, fontSize = 22.sp)
                    }
                }
            }
        }
        item {
            Bloque("Tu mezcla", R.drawable.ic_grafico, C.violeta) {
                ordenados.forEach { (p, pct) -> FilaPorcentaje(p, pct) }
            }
        }
        item {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BotonSecundario("Repetir test", R.drawable.ic_repetir, Modifier.weight(1f), onRepetir)
                BotonPrimario("Listo", Modifier.weight(1f), onCerrar)
            }
        }
    }
}

@Composable
fun FilaPorcentaje(p: Perfil, pct: Int) {
    val color = colorPerfil(p)
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
            Ic(iconoPerfil(p), color, tam = 17.dp)
        }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Row {
                Text(p.nombre, Modifier.weight(1f), color = C.texto, fontFamily = Chakra, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("$pct%", color = C.texto, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            Barra(pct / 100f, color, Modifier.fillMaxWidth().padding(top = 4.dp), alto = 6.dp)
        }
    }
}

@Composable
private fun Bloque(titulo: String, icono: Int, color: Color, content: @Composable () -> Unit) {
    Tarjeta(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Ic(icono, color, tam = 18.dp)
                Text(titulo, Modifier.padding(start = 8.dp).semantics { heading() }, color = C.texto, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
fun BotonPrimario(texto: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.horizontalGradient(listOf(C.disponible, C.violeta)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(texto, color = if (C.oscuro) C.fondo else Color.White, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun BotonSecundario(texto: String, icono: Int?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(C.tarjeta)
            .border(1.dp, C.borde, RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icono != null) {
            Ic(icono, C.texto, tam = 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(texto, color = C.texto, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
