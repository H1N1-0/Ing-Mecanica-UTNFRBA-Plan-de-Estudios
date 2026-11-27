package ar.correlativas.mecanica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.CaminoCritico
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.Materia
import ar.correlativas.mecanica.data.Plan
import ar.correlativas.mecanica.data.estadoNodo

/** Camino crítico del avance actual; null mientras carga. */
val LocalCritico = compositionLocalOf<CaminoCritico?> { null }

fun textoAnios(n: Int) = if (n == 1) "1 año" else "$n años"

fun textoMaterias(n: Int) = if (n == 1) "1 materia" else "$n materias"

/** Insignia de reloj de arena para los cuellos de botella. */
@Composable
fun InsigniaCuello(tam: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(tam)
            .clip(CircleShape)
            .background(C.critico)
            .border(1.5.dp, C.fondo, CircleShape)
            .semantics { contentDescription = "Cuello de botella" },
        contentAlignment = Alignment.Center,
    ) { Ic(R.drawable.ic_cuello, C.fondo, tam = tam * 0.62f) }
}

/** Tarjeta del inicio: cuánto falta como mínimo y por dónde pasa el camino crítico. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TarjetaCaminoCritico(critico: CaminoCritico, onVerMapa: () -> Unit, modifier: Modifier = Modifier) {
    Tarjeta(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(C.critico.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Ic(R.drawable.ic_ruta, C.critico, tam = 18.dp) }
                Text(
                    "CAMINO CRÍTICO",
                    Modifier.padding(start = 8.dp).weight(1f),
                    color = C.critico,
                    fontFamily = Chakra,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.5.sp,
                )
            }
            val anios = critico.anios
            if (anios == 0) {
                val finales = critico.traba.size
                Text(
                    if (finales == 0) "¡No te queda nada por cursar ni rendir!" else "Ya cursaste todo: te quedan ${textoMaterias(finales)} por aprobar.",
                    Modifier.padding(top = 10.dp),
                    color = C.texto,
                    fontFamily = Chakra,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
                return@Column
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text("$anios", color = C.critico, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 44.sp)
                Text(
                    if (anios == 1) " año de cursada" else " años de cursada",
                    Modifier.padding(bottom = 6.dp),
                    color = C.texto,
                    fontFamily = Chakra,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                )
            }
            Text(
                "Con tu avance actual, te quedan como mínimo ${textoAnios(anios)} de cursada (cada paso de la cadena es una cursada).",
                color = C.texto2,
                fontFamily = Chakra,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
            )
            // La cadena: un eslabón por cursada.
            FlowRow(
                Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                critico.camino.forEachIndexed { i, m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            m.nombre,
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(C.critico.copy(alpha = 0.12f))
                                .border(1.dp, C.critico.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color = C.texto,
                            fontFamily = Chakra,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (i < critico.camino.lastIndex) {
                            Text("›", Modifier.padding(start = 4.dp), color = C.critico, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(C.critico)
                    .clickable(role = Role.Button, onClick = onVerMapa)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Ic(R.drawable.ic_mapa, C.fondo, tam = 16.dp)
                Text("Ver en el mapa", Modifier.padding(start = 6.dp), color = C.fondo, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

/** Tarjeta "Prioridad": las 3 materias que más conviene aprobar ahora. */
@Composable
fun TarjetaPrioridad(critico: CaminoCritico, avance: Avance, onAbrir: (Int) -> Unit, modifier: Modifier = Modifier) {
    val prioridades = critico.prioridades(3)
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.tarjeta)
            .border(1.5.dp, Brush.linearGradient(listOf(C.critico, C.oro)), RoundedCornerShape(20.dp)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(C.oro.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Ic(R.drawable.ic_bandera, C.oro, tam = 18.dp) }
                Column(Modifier.padding(start = 8.dp).weight(1f)) {
                    Text("PRIORIDAD", color = C.oro, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
                    Text("Lo que más conviene aprobar ahora", color = C.texto2, fontFamily = Chakra, fontSize = 12.5.sp)
                }
            }
            if (prioridades.isEmpty()) {
                Text(
                    "No hay materias pendientes que traben a otras.",
                    Modifier.padding(top = 10.dp),
                    color = C.texto2,
                    fontFamily = Chakra,
                    fontSize = 14.sp,
                )
            }
            prioridades.forEachIndexed { i, p ->
                FilaPrioridad(i + 1, p.materia, p.traba, p.abreYa, p.enCaminoCritico, critico.esCuello(p.materia.id), avance, onAbrir)
            }
        }
    }
}

@Composable
private fun FilaPrioridad(
    puesto: Int,
    m: Materia,
    traba: Int,
    abreYa: Int,
    enCamino: Boolean,
    cuello: Boolean,
    avance: Avance,
    onAbrir: (Int) -> Unit,
) {
    Row(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(C.tarjetaAlta.copy(alpha = if (C.oscuro) 0.55f else 0.7f))
            .clickable(onClickLabel = "Ver detalle") { onAbrir(m.id) }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (puesto == 1) C.degradeOro else Brush.linearGradient(listOf(C.borde, C.borde))),
            contentAlignment = Alignment.Center,
        ) {
            Text("$puesto", color = if (puesto == 1) C.sobreOro else C.texto, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        Box(Modifier.padding(start = 10.dp, top = 4.dp, end = 4.dp)) { Nodo(m, avance.estadoNodo(m), tam = 38.dp, animado = false) }
        Column(Modifier.padding(start = 8.dp).weight(1f)) {
            Text(
                m.nombre,
                color = C.texto,
                fontFamily = Chakra,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val accion = if (avance.estado(m.id) == Estado.SIN_CURSAR) "Cursala" else "Rendí el final"
            Text(
                buildString {
                    append(accion)
                    if (abreYa > 0) append(" · te abre ${textoMaterias(abreYa)} ya")
                },
                color = C.texto2,
                fontFamily = Chakra,
                fontSize = 12.5.sp,
            )
            if (enCamino || cuello) {
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (cuello) Pildora("Cuello de botella", C.critico, icono = R.drawable.ic_cuello)
                    if (enCamino) Pildora("Camino crítico", C.critico, icono = R.drawable.ic_ruta)
                }
            }
        }
        Column(Modifier.padding(start = 8.dp), horizontalAlignment = Alignment.End) {
            Text("$traba", color = C.critico, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 26.sp)
            Text("destraba", color = C.texto2, fontFamily = Chakra, fontSize = 11.sp)
        }
    }
}

/** Bloque del detalle: cuánto traba la materia y si está en el camino crítico. */
@Composable
fun BloqueCritico(materia: Materia, avance: Avance, modifier: Modifier = Modifier) {
    val critico = LocalCritico.current ?: return
    if (materia.id !in critico.grafo.ids || avance.estado(materia.id) == Estado.APROBADA) return
    val traba = critico.trabaDe(materia.id)
    val enCamino = critico.enCamino(materia.id)
    val cuello = critico.esCuello(materia.id)
    val holgura = critico.holgura(materia.id)
    val titulo = when {
        traba == 0 && materia.id == Plan.PROYECTO_FINAL -> "Es la meta: no traba a ninguna otra"
        traba == 0 -> "No traba a ninguna otra materia"
        else -> "Si no la aprobás, se ${if (traba == 1) "traba 1 materia" else "traban $traba materias"}"
    }
    val detalle = when {
        enCamino -> "Está en el camino crítico: si la atrasás, te atrasás en recibirte."
        holgura == 0 -> "No tiene margen: si la atrasás, te atrasás en recibirte."
        holgura != null -> "Tiene ${if (holgura == 1) "1 cursada" else "$holgura cursadas"} de margen sin atrasar tu recibida."
        else -> "Ya la cursaste: solo falta aprobarla."
    }
    val color = if (cuello || enCamino || holgura == 0) C.critico else C.texto2
    Row(
        modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Ic(if (cuello) R.drawable.ic_cuello else R.drawable.ic_ruta, color, tam = 20.dp)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(titulo, color = C.texto, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 19.sp)
            Text(detalle, color = C.texto2, fontFamily = Chakra, fontSize = 13.5.sp, lineHeight = 17.sp)
            if (cuello || enCamino) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (cuello) Pildora("Cuello de botella", C.critico, icono = R.drawable.ic_cuello)
                    if (enCamino) Pildora("Camino crítico", C.critico, icono = R.drawable.ic_ruta)
                }
            }
        }
    }
}
