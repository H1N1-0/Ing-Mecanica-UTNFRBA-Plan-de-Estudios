package ar.correlativas.mecanica.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.Estilo
import ar.correlativas.mecanica.data.MAX_NOMBRE
import ar.correlativas.mecanica.data.NOMBRE_PREDETERMINADO
import ar.correlativas.mecanica.data.ModoTema
import ar.correlativas.mecanica.data.ResultadoPerfil
import ar.correlativas.mecanica.data.TestVocacional

@Composable
fun PantallaAjustes(
    tema: ModoTema,
    estilo: Estilo,
    nombre: String,
    onEstilo: (Estilo) -> Unit,
    onNombre: (String) -> Unit,
    perfil: ResultadoPerfil?,
    test: TestVocacional,
    onTema: (ModoTema) -> Unit,
    onTest: () -> Unit,
    onVerPerfil: () -> Unit,
    onBorrar: () -> Unit,
    onVolver: () -> Unit,
    onEstadisticas: () -> Unit = {},
    copia: AccionesCopia? = null,
) {
    BackHandler(onBack = onVolver)
    var confirmar by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        BarraSuperior("Ajustes", onVolver)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Subtitulo("ESTILO")
            Estilo.entries.forEach { e -> OpcionEstilo(e, e == estilo, Modifier.fillMaxWidth()) { onEstilo(e) } }

            Subtitulo("TEMA")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ModoTema.entries.forEach { m ->
                    OpcionTema(m, m == tema, Modifier.weight(1f)) { onTema(m) }
                }
            }
            Text(
                if (tema == ModoTema.SISTEMA) "Sigue el modo claro u oscuro del celular. El widget también."
                else "La app y el widget usan siempre el tema ${tema.texto.lowercase()}.",
                color = C.texto2,
                fontFamily = C.fuente,
                fontSize = 13.5.sp,
            )

            Subtitulo("RÓTULO DE LA LÁMINA")
            var texto by rememberSaveable { mutableStateOf(nombre) }
            OutlinedTextField(
                value = texto,
                onValueChange = { if (it.length <= MAX_NOMBRE) { texto = it; onNombre(it) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tu nombre", fontFamily = C.fuente) },
                placeholder = { Text(NOMBRE_PREDETERMINADO, fontFamily = C.fuente) },
                singleLine = true,
                shape = C.formas.chica,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = C.tarjeta,
                    unfocusedContainerColor = C.tarjeta,
                    focusedBorderColor = C.disponible,
                    unfocusedBorderColor = C.borde,
                    cursorColor = C.disponible,
                ),
            )
            Text(
                "Opcional. Se muestra en el rótulo tipo lámina del estilo Plano técnico y se guarda solo en este celular. Si lo dejás vacío, dice «$NOMBRE_PREDETERMINADO».",
                color = C.texto2,
                fontFamily = C.fuente,
                fontSize = 13.5.sp,
            )

            Subtitulo("TU PERFIL")
            Tarjeta(Modifier.fillMaxWidth()) {
                val principal = perfil?.let { test[it.principal] }
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { if (principal != null) onVerPerfil() else onTest() }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val color = principal?.let { colorPerfil(it) } ?: C.violeta
                    Box(Modifier.size(42.dp).clip(C.formas.chica).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Ic(principal?.let { iconoPerfil(it) } ?: R.drawable.ic_brujula, color, tam = 24.dp)
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(principal?.nombre ?: "Todavía no hiciste el test", color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            if (principal != null) "Ver resultado completo" else "¿Qué ingeniero mecánico sos? 12 preguntas",
                            color = C.texto2, fontFamily = C.fuente, fontSize = 13.5.sp,
                        )
                    }
                    Text("›", color = C.apagado, fontSize = 22.sp)
                }
            }
            if (perfil != null) BotonSecundario("Repetir el test", R.drawable.ic_repetir, Modifier.fillMaxWidth(), onTest)

            Subtitulo("TUS NOTAS")
            Tarjeta(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onEstadisticas).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(42.dp).clip(C.formas.chica).background(C.disponible.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Ic(R.drawable.ic_grafico, C.disponible, tam = 22.dp)
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("Estadísticas", color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Promedio, finales y evolución por año", color = C.texto2, fontFamily = C.fuente, fontSize = 13.5.sp)
                    }
                    Text("›", color = C.apagado, fontSize = 22.sp)
                }
            }

            if (copia != null) {
                Subtitulo("COPIA DE SEGURIDAD")
                SeccionCopia(copia)
            }

            Subtitulo("DATOS")
            Tarjeta(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { confirmar = true }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(42.dp).clip(C.formas.chica).background(C.peligro.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Ic(R.drawable.ic_repetir, C.peligro, tam = 22.dp)
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("Reiniciar mi progreso", color = C.peligro, fontFamily = C.fuente, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Todas las materias vuelven a «sin cursar» y se borran las notas.", color = C.texto2, fontFamily = C.fuente, fontSize = 13.5.sp)
                    }
                }
            }

            Subtitulo("ACERCA DE")
            Text(
                "Las correlativas, las preguntas del test y las frases salen de correlativas.json, perfiles.json y frases.json, " +
                    "en la raíz del repositorio. Las frases indican su fuente y si es verificable o una atribución popular.\n\n" +
                    "Tipografías Chakra Petch, Barlow Semi Condensed y Share Tech Mono (SIL Open Font License). " +
                    "Los dibujos técnicos del estilo Plano técnico están hechos en código.",
                color = C.texto2,
                fontFamily = C.fuente,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
            )
            // Para comparar qué versión tiene instalada cada dispositivo.
            val context = LocalContext.current
            val version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
            }
            Text(
                "Versión ${version ?: "—"}",
                color = C.texto2,
                fontFamily = C.fuenteDatos,
                fontSize = 13.sp,
            )
        }
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            containerColor = C.tarjeta,
            title = { Text("¿Reiniciar la partida?") },
            text = { Text("Todas las materias vuelven a \"sin cursar\" y se borran las notas y los aplazos. No se puede deshacer: si querés, exportá antes una copia.") },
            confirmButton = { TextButton(onClick = { onBorrar(); confirmar = false }) { Text("Reiniciar", color = C.peligro) } },
            dismissButton = { TextButton(onClick = { confirmar = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Subtitulo(texto: String) {
    Text(
        texto,
        Modifier.padding(top = 10.dp).semantics { heading() },
        color = C.texto2,
        fontFamily = C.fuente,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 2.sp,
    )
}

/** Tarjeta de tema con una vista previa en miniatura de la app. */
@Composable
private fun OpcionTema(modo: ModoTema, elegido: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val forma = C.formas.media
    Column(
        modifier
            .clip(forma)
            .background(C.tarjeta)
            .border(if (elegido) 2.dp else 1.dp, if (elegido) C.disponible else C.borde, forma)
            .semantics { selected = elegido }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(92.dp).clip(C.formas.chica)) {
            val estilo = C.estilo
            when (modo) {
                ModoTema.CLARO -> Miniatura(estilo, paletaPara(estilo, false), Modifier.fillMaxSize())
                ModoTema.OSCURO -> Miniatura(estilo, paletaPara(estilo, true), Modifier.fillMaxSize())
                ModoTema.SISTEMA -> Row(Modifier.fillMaxSize()) {
                    Miniatura(estilo, paletaPara(estilo, false), Modifier.weight(1f).fillMaxSize())
                    Miniatura(estilo, paletaPara(estilo, true), Modifier.weight(1f).fillMaxSize())
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Ic(
                when (modo) {
                    ModoTema.CLARO -> R.drawable.ic_sol
                    ModoTema.OSCURO -> R.drawable.ic_luna
                    ModoTema.SISTEMA -> R.drawable.ic_mitad
                },
                if (elegido) C.disponible else C.texto2,
                tam = 16.dp,
            )
            Text(
                when (modo) {
                    ModoTema.CLARO -> "Claro"
                    ModoTema.OSCURO -> "Oscuro"
                    ModoTema.SISTEMA -> "Sistema"
                },
                Modifier.padding(start = 5.dp),
                color = if (elegido) C.disponible else C.texto,
                fontFamily = C.fuente,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

/** Paleta de un estilo para las vistas previas (Material usa los colores dinámicos si hay). */
@Composable
private fun paletaPara(estilo: Estilo, oscuro: Boolean): Paleta {
    val context = LocalContext.current
    return remember(estilo, oscuro) {
        if (estilo == Estilo.MATERIAL) paletaMaterial(esquemaMaterial(context, oscuro), oscuro) else paletaFija(estilo, oscuro)
    }
}

/** Tarjeta de estilo: vista previa en claro y oscuro, nombre y descripción. */
@Composable
private fun OpcionEstilo(estilo: Estilo, elegido: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val forma = C.formas.media
    Row(
        modifier
            .clip(forma)
            .background(C.tarjeta)
            .border(if (elegido) 2.dp else 1.dp, if (elegido) C.disponible else C.borde.copy(alpha = 0.6f), forma)
            .semantics { selected = elegido }
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.width(112.dp).height(76.dp).clip(C.formas.chica)) {
            Miniatura(estilo, paletaPara(estilo, false), Modifier.weight(1f).fillMaxSize())
            Miniatura(estilo, paletaPara(estilo, true), Modifier.weight(1f).fillMaxSize())
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                estilo.texto,
                color = if (elegido) C.disponible else C.texto,
                fontFamily = C.fuente,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(estilo.descripcion, color = C.texto2, fontFamily = C.fuente, fontSize = 13.sp, lineHeight = 17.sp)
        }
        Box(
            Modifier.padding(start = 8.dp).size(22.dp).clip(CircleShape)
                .border(2.dp, if (elegido) C.disponible else C.borde, CircleShape)
                .padding(4.dp).clip(CircleShape).background(if (elegido) C.disponible else Color.Transparent),
        )
    }
}

/** Vista previa en miniatura de la app con un estilo y una paleta. */
@Composable
private fun Miniatura(estilo: Estilo, p: Paleta, modifier: Modifier) {
    val forma = when (estilo) {
        Estilo.PLANO -> RoundedCornerShape(1.dp)
        Estilo.GAMIFICADO -> RoundedCornerShape(5.dp)
        Estilo.MATERIAL -> RoundedCornerShape(8.dp)
    }
    Column(
        modifier
            .background(p.fondo)
            .then(
                if (estilo == Estilo.PLANO) Modifier.drawBehind {
                    val paso = 5.dp.toPx()
                    var x = 0f
                    while (x < size.width) { drawLine(p.tinta.copy(alpha = 0.12f), Offset(x, 0f), Offset(x, size.height), 0.5f); x += paso }
                    var y = 0f
                    while (y < size.height) { drawLine(p.tinta.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), 0.5f); y += paso }
                } else Modifier,
            )
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.fillMaxWidth().height(24.dp).clip(forma)
                .background(if (estilo == Estilo.GAMIFICADO) Brush.linearGradient(p.degradeJugador) else Brush.linearGradient(listOf(p.tarjeta, p.tarjeta)))
                .then(if (estilo == Estilo.PLANO) Modifier.border(0.6.dp, p.tinta.copy(alpha = 0.7f), forma) else Modifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            // El indicador de progreso de cada estilo, en chiquito.
            Box(
                Modifier.padding(start = 4.dp).size(14.dp).clip(CircleShape)
                    .border(2.dp, if (estilo == Estilo.GAMIFICADO) p.heroeAcento else p.disponible, CircleShape),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(p.oro, p.disponible).forEach { c ->
                Box(
                    Modifier.weight(1f).height(16.dp).clip(forma).background(p.tarjeta)
                        .then(if (estilo != Estilo.MATERIAL) Modifier.border(0.5.dp, if (estilo == Estilo.PLANO) p.tinta.copy(alpha = 0.6f) else p.borde, forma) else Modifier),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Box(Modifier.padding(start = 3.dp).size(7.dp).clip(CircleShape).background(c))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(p.oro, p.regular, p.disponible, p.bloqueada).forEach { c ->
                Box(Modifier.size(9.dp).clip(CircleShape).background(c))
            }
        }
    }
}
