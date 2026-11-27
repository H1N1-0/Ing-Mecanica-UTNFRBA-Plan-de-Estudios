package ar.correlativas.mecanica.ui

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.Avance
import ar.correlativas.mecanica.data.CaminoCritico
import ar.correlativas.mecanica.data.Frases
import ar.correlativas.mecanica.data.ResultadoPerfil
import ar.correlativas.mecanica.data.TestVocacional
import ar.correlativas.mecanica.data.EstadoNodo
import ar.correlativas.mecanica.data.Logro
import ar.correlativas.mecanica.data.Materia
import ar.correlativas.mecanica.data.Plan
import ar.correlativas.mecanica.data.RANGOS
import ar.correlativas.mecanica.data.estadoNodo
import ar.correlativas.mecanica.data.formatearHoras
import ar.correlativas.mecanica.data.porcentajeCarrera
import ar.correlativas.mecanica.data.rangoPara
import ar.correlativas.mecanica.data.siguienteRango
import kotlin.math.roundToInt

@Composable
fun PantallaInicio(
    avance: Avance,
    critico: CaminoCritico,
    logros: List<Logro>,
    onAbrir: (Int) -> Unit,
    onLista: (EstadoNodo?) -> Unit,
    onLogros: () -> Unit,
    onAjustes: () -> Unit,
    onTest: () -> Unit,
    onPerfil: () -> Unit,
    onVerCamino: () -> Unit,
    frases: Frases,
    test: TestVocacional,
    perfil: ResultadoPerfil?,
    /** 1 en el celular; 2 o 3 en tablet y en horizontal. */
    columnas: Int = 1,
    scroll: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
) {
    val plan = avance.plan
    // Una sola columna en el celular (igual que antes); en pantallas anchas las tarjetas se reparten.
    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(columnas),
        modifier = Modifier.fillMaxSize(),
        state = scroll,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalItemSpacing = 14.dp,
    ) {
        item(key = "cabecera", span = StaggeredGridItemSpan.FullLine) { Cabecera(onAjustes) }
        item(key = "jugador") { TarjetaJugador(avance) }
        item(key = "contadores") { Contadores(avance, onLista) }
        item(key = "prioridad") { TarjetaPrioridad(critico, avance, onAbrir) }
        item(key = "critico") { TarjetaCaminoCritico(critico, onVerCamino) }
        item(key = "frase") { FraseDelDia(frases) }
        item(key = "perfil") { TarjetaPerfil(test, perfil, onTest, onPerfil) }
        item(key = "disponibles", span = StaggeredGridItemSpan.FullLine) {
            Column {
                Seccion("Disponibles ahora", R.drawable.ic_rayo, C.disponible, "${avance.cursables.size}")
                Spacer(Modifier.height(8.dp))
                if (avance.cursables.isEmpty()) {
                    Vacio("No hay materias para cursar: marcá las que ya regularizaste.")
                } else {
                    FilaMaterias(avance.cursables, avance, onAbrir)
                }
            }
        }
        if (avance.rendibles.isNotEmpty()) {
            item(key = "finales", span = StaggeredGridItemSpan.FullLine) {
                Column {
                    Seccion("Finales para rendir", R.drawable.ic_llama, C.rendir, "${avance.rendibles.size}")
                    Spacer(Modifier.height(8.dp))
                    FilaMaterias(avance.rendibles, avance, onAbrir)
                }
            }
        }
        item(key = "logro") { ProximoLogro(avance, logros, perfil != null, onLogros) }
        item(key = "camino") { Camino(avance, plan) }
    }
}

@Composable
private fun Cabecera(onAjustes: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Ic(R.drawable.ic_engranaje, C.disponible, tam = 22.dp)
        Text(
            "MECÁNICA · UTN FRBA",
            Modifier.padding(start = 8.dp).weight(1f),
            color = C.texto2,
            fontFamily = Chakra,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            letterSpacing = 2.sp,
        )
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(C.tarjeta)
                .border(1.dp, C.borde, CircleShape)
                .semantics { contentDescription = "Ajustes" }
                .clickable(role = Role.Button, onClick = onAjustes),
            contentAlignment = Alignment.Center,
        ) { Ic(R.drawable.ic_ajustes, C.texto2, tam = 22.dp) }
    }
}

@Composable
private fun TarjetaJugador(avance: Avance) {
    val pct = avance.porcentajeCarrera
    val rango = rangoPara(pct)
    val siguiente = siguienteRango(rango)
    val pctAnimado by animateIntAsState((pct * 100).roundToInt(), tween(900), label = "pct")
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(C.degradeJugador)
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(24.dp))
            .padding(18.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
                    Anillo(pct.toFloat(), Modifier.fillMaxSize(), grosor = 13.dp, pista = Color.White.copy(alpha = 0.16f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("$pctAnimado", color = C.heroeTexto, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 40.sp)
                            Text("%", color = C.heroeTexto2, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Text("de la carrera", color = C.heroeTexto2, fontSize = 12.sp, fontFamily = Chakra)
                    }
                }
                Column(Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(
                        "RANGO ${rango.numero}/${RANGOS.size}",
                        color = C.heroeAcento,
                        fontFamily = Chakra,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp,
                    )
                    Text(
                        rango.nombre,
                        color = C.heroeTexto,
                        style = MaterialTheme.typography.headlineSmall,
                        lineHeight = 28.sp,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(rango.lema, color = C.heroeTexto2, fontSize = 13.sp, fontFamily = Chakra)
                }
            }
            if (siguiente != null) {
                val tramo = ((pct - rango.desde) / (siguiente.desde - rango.desde)).toFloat()
                val falta = ((siguiente.desde - pct) * 100).coerceAtLeast(0.0)
                Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ic(R.drawable.ic_medalla, C.heroeOro, tam = 16.dp)
                    Text(
                        "Próximo: ${siguiente.nombre}",
                        Modifier.padding(start = 6.dp).weight(1f),
                        color = C.heroeTexto,
                        fontFamily = Chakra,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                    )
                    Text("faltan ${falta.roundToInt().coerceAtLeast(1)}%", color = C.heroeTexto2, fontFamily = Chakra, fontSize = 13.sp)
                }
                Barra(tramo, C.heroeOro, Modifier.fillMaxWidth().padding(top = 6.dp), alto = 8.dp, pista = Color.White.copy(alpha = 0.18f))
            } else {
                Pildora("¡Carrera completa!", C.heroeOro, Modifier.padding(top = 12.dp), R.drawable.ic_corona)
            }
        }
    }
}

@Composable
private fun Contadores(avance: Avance, onLista: (EstadoNodo?) -> Unit) {
    val plan = avance.plan
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Contador(
                "Aprobadas", avance.aprobadasObligatorias.toString(), "/${plan.obligatorias.size}",
                R.drawable.ic_estrella, C.oro, avance.aprobadasObligatorias / plan.obligatorias.size.toFloat(),
                Modifier.weight(1f).clickable { onLista(EstadoNodo.APROBADA) },
            )
            Contador(
                "Electivas", formatearHoras(avance.horasElectivas), "/${formatearHoras(Plan.HORAS_ELECTIVAS_REQUERIDAS)} hs",
                R.drawable.ic_reloj, C.rosa, (avance.horasElectivas / Plan.HORAS_ELECTIVAS_REQUERIDAS).toFloat(),
                Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Contador(
                "Para rendir", avance.rendibles.size.toString(), null,
                R.drawable.ic_llama, C.rendir, null,
                Modifier.weight(1f).clickable { onLista(EstadoNodo.PARA_RENDIR) },
            )
            Contador(
                "Para cursar", avance.cursables.size.toString(), null,
                R.drawable.ic_rayo, C.disponible, null,
                Modifier.weight(1f).clickable { onLista(EstadoNodo.DISPONIBLE) },
            )
        }
    }
}

@Composable
private fun Contador(titulo: String, valor: String, de: String?, icono: Int, color: Color, progreso: Float?, modifier: Modifier) {
    Tarjeta(modifier) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(color.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) { Ic(icono, color, tam = 18.dp) }
                Text(titulo, Modifier.padding(start = 8.dp), color = C.texto2, fontFamily = Chakra, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom) {
                Text(valor, color = color, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 34.sp)
                if (de != null) Text(de, color = C.texto2, fontFamily = Chakra, fontSize = 15.sp, modifier = Modifier.padding(start = 3.dp, bottom = 5.dp))
            }
            if (progreso != null) Barra(progreso, color, Modifier.fillMaxWidth().padding(top = 4.dp), alto = 6.dp)
        }
    }
}

@Composable
fun Seccion(titulo: String, icono: Int, color: Color, extra: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Ic(icono, color, tam = 20.dp)
        Text(
            titulo,
            Modifier.padding(start = 8.dp).semantics { heading() },
            color = C.texto,
            style = MaterialTheme.typography.titleLarge,
        )
        if (extra != null) {
            Spacer(Modifier.width(8.dp))
            Pildora(extra, color)
        }
    }
}

@Composable
private fun Vacio(texto: String) {
    Text(texto, color = C.texto2, fontFamily = Chakra, fontSize = 14.sp)
}

@Composable
private fun FilaMaterias(materias: List<Materia>, avance: Avance, onAbrir: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(end = 8.dp)) {
        items(materias, key = { it.id }) { m ->
            val e = avance.estadoNodo(m)
            Tarjeta(Modifier.width(132.dp).clickable { onAbrir(m.id) }) {
                Column(Modifier.padding(12.dp)) {
                    Nodo(m, e, tam = 42.dp)
                    Text(
                        m.nombre,
                        Modifier.padding(top = 10.dp).height(40.dp),
                        color = C.texto,
                        fontFamily = Chakra,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        m.nivel?.let { "Nivel $it" } ?: "Electiva",
                        color = C.texto2,
                        fontFamily = Chakra,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProximoLogro(avance: Avance, logros: List<Logro>, testHecho: Boolean, onLogros: () -> Unit) {
    val conseguidos = logros.count { it.cumplido(avance, testHecho) }
    val proximo = logros
        .filterNot { it.cumplido(avance, testHecho) }
        .maxByOrNull { val (h, t) = it.progreso(avance, testHecho); h / t }
    Tarjeta(Modifier.fillMaxWidth().clickable(onClick = onLogros)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (proximo != null) {
                Insignia(proximo.icono, conseguido = false, tam = 52.dp)
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text("PRÓXIMO LOGRO · $conseguidos/${logros.size}", color = C.violeta, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.5.sp)
                    Text(proximo.titulo, color = C.texto, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text(proximo.descripcion, color = C.texto2, fontFamily = Chakra, fontSize = 13.sp, lineHeight = 16.sp)
                    val (h, t) = proximo.progreso(avance, testHecho)
                    Barra((h / t).toFloat(), C.violeta, Modifier.fillMaxWidth().padding(top = 8.dp), alto = 6.dp)
                }
            } else {
                Insignia(ar.correlativas.mecanica.data.IconoLogro.CORONA, conseguido = true, tam = 52.dp)
                Text("¡Conseguiste todos los logros!", Modifier.padding(start = 14.dp), color = C.oro, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun Camino(avance: Avance, plan: Plan) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Seccion("Tu camino", R.drawable.ic_mapa, C.violeta)
        Tarjeta(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                (1..5).forEach { n ->
                    val materias = plan.obligatorias.filter { it.nivel == n }
                    val hechas = materias.count { avance.estadoNodo(it) == EstadoNodo.APROBADA }
                    val completo = hechas == materias.size
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (completo) C.degradeOro else androidx.compose.ui.graphics.SolidColor(C.tarjetaAlta)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (completo) Ic(R.drawable.ic_trofeo, Color(0xFF3A2500), tam = 18.dp)
                            else Text("$n", color = C.texto, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Row {
                                Text("Nivel $n", color = C.texto, fontFamily = Chakra, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Text("$hechas/${materias.size}", color = if (completo) C.oro else C.texto2, fontFamily = Chakra, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            Barra(hechas / materias.size.toFloat(), if (completo) C.oro else C.disponible, Modifier.fillMaxWidth().padding(top = 5.dp), alto = 6.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FraseDelDia(frases: Frases) {
    val hoy = remember { java.time.LocalDate.now().toEpochDay() }
    var otra by rememberSaveable { mutableStateOf(0) }
    val frase = frases.delDia(hoy, otra)
    Tarjeta(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Ic(R.drawable.ic_comillas, C.violeta, tam = 20.dp)
                Text(
                    "FRASE DEL DÍA",
                    Modifier.padding(start = 8.dp).weight(1f),
                    color = C.violeta,
                    fontFamily = Chakra,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    letterSpacing = 1.5.sp,
                )
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .semantics { contentDescription = "Otra frase" }
                        .clickable(role = Role.Button) { otra++ },
                    contentAlignment = Alignment.Center,
                ) { Ic(R.drawable.ic_repetir, C.texto2, tam = 18.dp) }
            }
            androidx.compose.animation.AnimatedContent(frase, label = "frase") { f ->
                Column {
                    Text(
                        "«${f.texto}»",
                        Modifier.padding(top = 6.dp),
                        color = C.texto,
                        fontFamily = Chakra,
                        fontWeight = FontWeight.Medium,
                        fontSize = 17.sp,
                        lineHeight = 23.sp,
                    )
                    Text("— ${f.autor}", Modifier.padding(top = 8.dp), color = C.texto, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text(f.fuente, color = C.texto2, fontFamily = Chakra, fontSize = 12.5.sp, lineHeight = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun TarjetaPerfil(test: TestVocacional, perfil: ResultadoPerfil?, onTest: () -> Unit, onPerfil: () -> Unit) {
    val principal = perfil?.let { test[it.principal] }
    if (perfil == null || principal == null) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(C.violeta, C.rosa)))
                .clickable(role = Role.Button, onClick = onTest)
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Ic(R.drawable.ic_brujula, Color.White, tam = 30.dp) }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text("¿Qué ingeniero mecánico sos?", color = Color.White, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 22.sp)
                    Text("12 preguntas · 2 minutos · desbloqueá un logro", color = Color.White.copy(alpha = 0.85f), fontFamily = Chakra, fontSize = 13.5.sp)
                }
                Text("›", color = Color.White, fontSize = 28.sp)
            }
        }
        return
    }
    Tarjeta(Modifier.fillMaxWidth().clickable(onClick = onPerfil)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = colorPerfil(principal)
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                    Ic(iconoPerfil(principal), color, tam = 30.dp)
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    Text("TU PERFIL", color = color, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.5.sp)
                    Text(principal.nombre, color = C.texto, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Text("${perfil.porcentajes[principal.id] ?: 0}%", color = color, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            }
            Column(Modifier.padding(top = 8.dp)) {
                perfil.ordenados.drop(1).take(2).forEach { (id, pct) -> test[id]?.let { FilaPorcentaje(it, pct) } }
            }
        }
    }
}
