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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.EstadoNodo
import ar.correlativas.mecanica.data.Materia
import ar.correlativas.mecanica.data.Nivel
import ar.correlativas.mecanica.data.Plan
import ar.correlativas.mecanica.data.Requisitos
import ar.correlativas.mecanica.data.Tipo
import ar.correlativas.mecanica.data.estadoNodo
import ar.correlativas.mecanica.data.formatearHoras

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HojaDetalle(
    materia: Materia,
    avance: Avance,
    onCambio: (Estado) -> Unit,
    onAbrir: (Int) -> Unit,
    onCerrar: () -> Unit,
) {
    val hoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scroll = rememberScrollState()
    LaunchedEffect(materia.id) { scroll.scrollTo(0) }
    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = hoja,
        containerColor = C.fondoAlto,
        contentColor = C.texto,
        dragHandle = {
            Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(width = 42.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(C.borde))
        },
    ) {
        ContenidoDetalle(materia, avance, onCambio, onAbrir, Modifier.verticalScroll(scroll))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContenidoDetalle(
    materia: Materia,
    avance: Avance,
    onCambio: (Estado) -> Unit,
    onAbrir: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val e = avance.estadoNodo(materia)
    val color = colorEstado(e)
    Column(modifier.padding(horizontal = 18.dp).padding(bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Box(Modifier.padding(top = 6.dp, end = 8.dp)) { Nodo(materia, e, tam = 68.dp) }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(
                    when (materia.tipo) {
                        Tipo.OBLIGATORIA -> "NIVEL ${materia.nivel}" + if (materia.integradora) " · INTEGRADORA" else ""
                        Tipo.PPS -> "PRÁCTICA PROFESIONAL"
                        Tipo.ELECTIVA -> "ELECTIVA" + (materia.nivel?.let { " · NIVEL $it" } ?: "")
                    },
                    color = C.texto2,
                    fontFamily = Chakra,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    materia.nombre,
                    style = MaterialTheme.typography.headlineSmall,
                    lineHeight = 28.sp,
                    color = C.texto,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(top = 12.dp),
        ) {
            Pildora(textoEstado(e, materia.tipo == Tipo.PPS), color, icono = iconoEstado(e))
            if (materia.tipo == Tipo.ELECTIVA) {
                Pildora(materia.horasAnuales?.let { "${formatearHoras(it)} hs anuales" } ?: "Horas sin dato", C.rosa, icono = R.drawable.ic_reloj)
            }
            if (materia.requeridaTituloIntermedio) Pildora("Título intermedio", C.violeta, icono = R.drawable.ic_birrete)
            materia.codigoSiu?.let { Pildora("SIU $it", C.texto2) }
        }

        Banner(materia, avance, e)
        BloqueCritico(materia, avance)

        SelectorEstado(materia.tipo, avance.estado(materia.id), onCambio, Modifier.fillMaxWidth().padding(top = 14.dp))

        Titulo(if (materia.tipo == Tipo.PPS) "Para empezarla" else "Para cursar", R.drawable.ic_rayo, C.disponible)
        Requisitos(materia.cursar, avance, onAbrir)

        if (materia.tipo != Tipo.PPS) {
            Titulo("Para rendir el final", R.drawable.ic_llama, C.rendir)
            Condicion("Tenerla regularizada", avance.estado(materia.id) != Estado.SIN_CURSAR)
            when {
                materia.reglaRendir != null -> {
                    val faltan = avance.faltantesRendir(materia)
                    Condicion(
                        if (faltan.isEmpty()) "Todas las demás obligatorias aprobadas" else "Todas las demás obligatorias aprobadas (faltan ${faltan.size})",
                        faltan.isEmpty(),
                    )
                    Condicion(
                        "${formatearHoras(Plan.HORAS_ELECTIVAS_REQUERIDAS)} hs de electivas aprobadas (tenés ${formatearHoras(avance.horasElectivas)})",
                        avance.faltanHorasParaRendir(materia) == 0.0,
                    )
                    if (faltan.isNotEmpty()) Lista("Te faltan aprobar", Nivel.APROBADA, faltan.map { it.id }, avance, onAbrir)
                }
                materia.rendir != null -> Requisitos(materia.rendir, avance, onAbrir)
            }
        } else {
            avance.plan.reglas["pps"]?.let {
                Text(it, color = C.texto2, fontFamily = Chakra, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        val habilita = remember(avance, materia.id) { avance.habilita(materia.id) }
        Titulo("Desbloquea", R.drawable.ic_candado, C.oro, extra = habilita.size.takeIf { it > 0 }?.toString())
        if (habilita.isEmpty()) {
            Text("No es correlativa de ninguna materia.", color = C.texto2, fontFamily = Chakra, fontSize = 14.sp)
        } else {
            habilita.forEach { h ->
                val partes = buildList {
                    when (h.paraCursar) {
                        Nivel.REGULARIZADA -> add("cursar (regularizada)")
                        Nivel.APROBADA -> add("cursar (aprobada)")
                        null -> {}
                    }
                    if (h.paraRendir) add("rendir")
                }
                FilaMateria(h.materia, avance, "La pide para " + partes.joinToString(" y "), null, onAbrir)
            }
        }
    }
}

@Composable
private fun Banner(materia: Materia, avance: Avance, e: EstadoNodo) {
    val (titulo, texto) = when (e) {
        EstadoNodo.BLOQUEADA -> {
            val f = avance.faltantesCursar(materia).size
            "Bloqueada" to "Te ${if (f == 1) "falta 1 correlativa" else "faltan $f correlativas"} para ${if (materia.tipo == Tipo.PPS) "empezarla" else "cursarla"}."
        }
        EstadoNodo.DISPONIBLE -> "¡Desbloqueada!" to if (materia.tipo == Tipo.PPS) "Ya podés empezar la PPS." else "Cumplís todas las correlativas: podés cursarla."
        EstadoNodo.REGULARIZADA -> {
            val f = avance.faltantesRendir(materia).size
            val h = avance.faltanHorasParaRendir(materia)
            "Regularizada" to buildList {
                if (f > 0) add("te faltan $f para rendir el final")
                if (h > 0) add("faltan ${formatearHoras(h)} hs de electivas")
            }.joinToString(" y ").replaceFirstChar { it.uppercase() }.ifEmpty { "Cursada aprobada." } + "."
        }
        EstadoNodo.PARA_RENDIR -> "¡Final disponible!" to "Cumplís todo: ya podés rendir el final."
        EstadoNodo.APROBADA -> "¡Completada!" to if (materia.tipo == Tipo.PPS) "PPS acreditada." else "Materia aprobada. Bien ahí."
    }
    val color = colorEstado(e)
    Row(
        Modifier
            .padding(top = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Ic(iconoEstado(e), color, tam = 20.dp)
        }
        Column(Modifier.padding(start = 12.dp)) {
            Text(titulo, color = color, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Text(texto, color = C.texto, fontFamily = Chakra, fontSize = 14.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun Titulo(texto: String, icono: Int, color: Color, extra: String? = null) {
    Row(Modifier.padding(top = 24.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Ic(icono, color, tam = 18.dp)
        Text(texto, Modifier.padding(start = 8.dp).semantics { heading() }, color = C.texto, style = MaterialTheme.typography.titleLarge)
        if (extra != null) Pildora(extra, color, Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun Condicion(texto: String, cumple: Boolean) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Marca(cumple)
        Text(texto, Modifier.padding(start = 10.dp), color = C.texto, fontFamily = Chakra, fontSize = 15.sp)
    }
}

@Composable
private fun Marca(cumple: Boolean) {
    val color = if (cumple) C.rendir else C.peligro
    Box(Modifier.size(22.dp).clip(CircleShape).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        if (cumple) Ic(R.drawable.ic_check, color, tam = 14.dp)
        else Text("✕", color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Requisitos(r: Requisitos, avance: Avance, onAbrir: (Int) -> Unit) {
    if (r.vacio) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Marca(true)
            Text("Sin correlativas", Modifier.padding(start = 10.dp), color = C.texto2, fontFamily = Chakra, fontSize = 15.sp)
        }
        return
    }
    Lista("Regularizadas", Nivel.REGULARIZADA, r.regularizadas, avance, onAbrir)
    Lista("Aprobadas", Nivel.APROBADA, r.aprobadas, avance, onAbrir)
}

@Composable
private fun Lista(titulo: String, nivel: Nivel, ids: List<Int>, avance: Avance, onAbrir: (Int) -> Unit) {
    if (ids.isEmpty()) return
    val color = if (nivel == Nivel.APROBADA) C.oro else C.regular
    Text(
        titulo.uppercase(),
        Modifier.padding(top = 8.dp, bottom = 2.dp),
        color = color,
        fontFamily = Chakra,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
    )
    ids.mapNotNull { avance.plan[it] }.forEach { m ->
        val est = avance.estado(m.id)
        val cumple = if (nivel == Nivel.APROBADA) est == Estado.APROBADA else est != Estado.SIN_CURSAR
        FilaMateria(
            m, avance,
            when (est) {
                Estado.SIN_CURSAR -> "Sin cursar"
                Estado.REGULARIZADA -> "Regularizada"
                Estado.APROBADA -> "Aprobada"
            },
            cumple, onAbrir,
        )
    }
}

@Composable
private fun FilaMateria(m: Materia, avance: Avance, detalle: String, cumple: Boolean?, onAbrir: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onAbrir(m.id) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(top = 4.dp, end = 4.dp)) { Nodo(m, avance.estadoNodo(m), tam = 38.dp, animado = false) }
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(m.nombre, color = C.texto, fontFamily = Chakra, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 18.sp)
            Text(detalle, color = C.texto2, fontFamily = Chakra, fontSize = 13.sp)
        }
        if (cumple != null) Marca(cumple)
    }
}
