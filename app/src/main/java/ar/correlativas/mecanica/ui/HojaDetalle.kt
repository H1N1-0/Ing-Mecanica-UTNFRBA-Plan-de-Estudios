package ar.correlativas.mecanica.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.Materia
import ar.correlativas.mecanica.data.Nivel
import ar.correlativas.mecanica.data.Plan
import ar.correlativas.mecanica.data.Requisitos
import ar.correlativas.mecanica.data.Tipo
import ar.correlativas.mecanica.data.formatearHoras

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HojaDetalle(
    materia: Materia,
    avance: Avance,
    onCambio: (Estado) -> Unit,
    onAbrir: (Int) -> Unit,
    onCerrar: () -> Unit,
) {
    val p = LocalPaleta.current
    val plan = avance.plan
    val hoja = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scroll = rememberScrollState()
    LaunchedEffect(materia.id) { scroll.scrollTo(0) }

    ModalBottomSheet(
        onDismissRequest = onCerrar,
        sheetState = hoja,
        containerColor = p.superficie,
        contentColor = p.tinta,
    ) {
        Column(
            Modifier
                .verticalScroll(scroll)
                .padding(horizontal = 18.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Globo(numeroDe(materia), avance.estado(materia.id), tam = 42.dp)
                Text(
                    materia.nombre,
                    style = MaterialTheme.typography.headlineMedium,
                    lineHeight = 32.sp,
                    modifier = Modifier.padding(start = 12.dp).weight(1f).semantics { heading() },
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) {
                EstadoCorrelativas(materia, avance)
                when (materia.tipo) {
                    Tipo.OBLIGATORIA -> Etiqueta("Nivel ${materia.nivel}", p.tinta2, p.neutroFondo)
                    Tipo.PPS -> Etiqueta("Práctica Profesional Supervisada", p.tinta2, p.neutroFondo)
                    Tipo.ELECTIVA -> Etiqueta("Electiva", p.tinta2, p.neutroFondo)
                }
                etiquetas(materia).forEach { Etiqueta(it, p.tinta2, p.neutroFondo) }
                materia.codigoSiu?.let { Etiqueta("SIU $it", p.tinta2, p.neutroFondo) }
            }

            SelectorEstado(
                tipo = materia.tipo,
                estado = avance.estado(materia.id),
                onCambio = onCambio,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            )

            Titulo(if (materia.tipo == Tipo.PPS) "Para empezarla" else "Para cursar")
            BloqueRequisitos(materia.cursar, avance, onAbrir)

            if (materia.tipo != Tipo.PPS) {
                Titulo("Para rendir el final")
                Nota("Tenerla regularizada", cumple = avance.estado(materia.id) != Estado.SIN_CURSAR)
                when {
                    materia.reglaRendir != null -> {
                        val faltan = avance.faltantesRendir(materia)
                        Nota(
                            if (faltan.isEmpty()) "Todas las demás obligatorias aprobadas"
                            else "Todas las demás obligatorias aprobadas (te faltan ${faltan.size})",
                            cumple = faltan.isEmpty(),
                        )
                        Nota(
                            "${formatearHoras(Plan.HORAS_ELECTIVAS_REQUERIDAS)} hs anuales de electivas aprobadas " +
                                "(tenés ${formatearHoras(avance.horasElectivas)})",
                            cumple = avance.faltanHorasParaRendir(materia) == 0.0,
                        )
                        if (faltan.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            Grupo("Te faltan aprobar", Nivel.APROBADA, faltan.map { it.id }, avance, onAbrir)
                        }
                    }
                    materia.rendir != null -> BloqueRequisitos(materia.rendir, avance, onAbrir)
                }
            } else {
                Text(
                    avance.plan.reglas["pps"] ?: "",
                    color = p.tinta2,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Titulo("Habilita")
            val habilita = avance.habilita(materia.id)
            if (habilita.isEmpty()) {
                Text("No es correlativa de ninguna materia.", color = p.tinta2, fontSize = 15.sp)
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
                    FilaReferencia(
                        materia = h.materia,
                        avance = avance,
                        detalle = "Te la pide para " + partes.joinToString(" y "),
                        indicador = h.paraCursar ?: Nivel.APROBADA,
                        cumple = null,
                        onAbrir = onAbrir,
                    )
                }
            }
            TextButton(onClick = onCerrar, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) {
                Text("Cerrar")
            }
        }
    }
}

@Composable
private fun Titulo(texto: String) {
    val p = LocalPaleta.current
    Column(Modifier.padding(top = 22.dp, bottom = 6.dp)) {
        Text(texto, style = MaterialTheme.typography.titleLarge, color = p.tinta, modifier = Modifier.semantics { heading() })
        HorizontalDivider(color = p.lineaFuerte, thickness = 1.5.dp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun Nota(texto: String, cumple: Boolean) {
    val p = LocalPaleta.current
    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Marca(cumple)
        Text(texto, fontSize = 15.sp, color = p.tinta, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun Marca(cumple: Boolean?) {
    val p = LocalPaleta.current
    when (cumple) {
        true -> Text("✓", color = p.apr, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.width(18.dp))
        false -> Text("✗", color = p.peligro, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.width(18.dp))
        null -> {}
    }
}

@Composable
private fun BloqueRequisitos(r: Requisitos, avance: Avance, onAbrir: (Int) -> Unit) {
    val p = LocalPaleta.current
    if (r.vacio) {
        Text("Sin correlativas.", color = p.tinta2, fontSize = 15.sp)
        return
    }
    Grupo("Regularizadas", Nivel.REGULARIZADA, r.regularizadas, avance, onAbrir)
    Grupo("Aprobadas", Nivel.APROBADA, r.aprobadas, avance, onAbrir)
}

@Composable
private fun Grupo(titulo: String, nivel: Nivel, ids: List<Int>, avance: Avance, onAbrir: (Int) -> Unit) {
    if (ids.isEmpty()) return
    val p = LocalPaleta.current
    Row(Modifier.padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        LineaNivel(nivel)
        Text(titulo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = p.tinta2, modifier = Modifier.padding(start = 8.dp))
    }
    ids.mapNotNull { avance.plan[it] }.forEach { m ->
        val e = avance.estado(m.id)
        val cumple = if (nivel == Nivel.APROBADA) e == Estado.APROBADA else e != Estado.SIN_CURSAR
        FilaReferencia(
            materia = m,
            avance = avance,
            detalle = when (e) {
                Estado.SIN_CURSAR -> "Sin cursar"
                Estado.REGULARIZADA -> "Regularizada"
                Estado.APROBADA -> "Aprobada"
            },
            indicador = nivel,
            cumple = cumple,
            onAbrir = onAbrir,
        )
    }
}

@Composable
private fun FilaReferencia(
    materia: Materia,
    avance: Avance,
    detalle: String,
    indicador: Nivel,
    cumple: Boolean?,
    onAbrir: (Int) -> Unit,
) {
    val p = LocalPaleta.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onAbrir(materia.id) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Marca(cumple)
        if (cumple != null) Spacer(Modifier.width(8.dp))
        Globo(numeroDe(materia), avance.estado(materia.id), tam = 30.dp)
        Column(Modifier.padding(start = 10.dp).weight(1f)) {
            Text(materia.nombre, fontSize = 15.5.sp, fontWeight = FontWeight.Medium, color = p.tinta)
            Text(detalle, fontSize = 13.sp, color = p.tinta2)
        }
        if (cumple == null) {
            LineaNivel(indicador, Modifier.padding(start = 8.dp))
        } else {
            Text(
                "›",
                fontSize = 22.sp,
                color = p.apagado,
                modifier = Modifier.padding(start = 8.dp).background(Color.Transparent),
            )
        }
    }
}
