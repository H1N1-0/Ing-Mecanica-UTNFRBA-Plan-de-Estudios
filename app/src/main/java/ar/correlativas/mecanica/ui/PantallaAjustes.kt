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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.ModoTema
import ar.correlativas.mecanica.data.ResultadoPerfil
import ar.correlativas.mecanica.data.TestVocacional

@Composable
fun PantallaAjustes(
    tema: ModoTema,
    perfil: ResultadoPerfil?,
    test: TestVocacional,
    onTema: (ModoTema) -> Unit,
    onTest: () -> Unit,
    onVerPerfil: () -> Unit,
    onBorrar: () -> Unit,
    onVolver: () -> Unit,
) {
    BackHandler(onBack = onVolver)
    var confirmar by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(C.fondo)) {
        BarraSuperior("Ajustes", onVolver)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
                fontFamily = Chakra,
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
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Ic(principal?.let { iconoPerfil(it) } ?: R.drawable.ic_brujula, color, tam = 24.dp)
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(principal?.nombre ?: "Todavía no hiciste el test", color = C.texto, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(
                            if (principal != null) "Ver resultado completo" else "¿Qué ingeniero mecánico sos? 12 preguntas",
                            color = C.texto2, fontFamily = Chakra, fontSize = 13.5.sp,
                        )
                    }
                    Text("›", color = C.apagado, fontSize = 22.sp)
                }
            }
            if (perfil != null) BotonSecundario("Repetir el test", R.drawable.ic_repetir, Modifier.fillMaxWidth(), onTest)

            Subtitulo("DATOS")
            Tarjeta(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button) { confirmar = true }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(C.peligro.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                        Ic(R.drawable.ic_repetir, C.peligro, tam = 22.dp)
                    }
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text("Reiniciar mi progreso", color = C.peligro, fontFamily = Chakra, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Todas las materias vuelven a «sin cursar».", color = C.texto2, fontFamily = Chakra, fontSize = 13.5.sp)
                    }
                }
            }

            Subtitulo("ACERCA DE")
            Text(
                "Las correlativas, las preguntas del test y las frases salen de correlativas.json, perfiles.json y frases.json, " +
                    "en la raíz del repositorio. Las frases indican su fuente y si es verificable o una atribución popular.\n\n" +
                    "Tipografía Chakra Petch (SIL Open Font License).",
                color = C.texto2,
                fontFamily = Chakra,
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
            )
        }
    }

    if (confirmar) {
        AlertDialog(
            onDismissRequest = { confirmar = false },
            containerColor = C.tarjeta,
            title = { Text("¿Reiniciar la partida?") },
            text = { Text("Todas las materias vuelven a \"sin cursar\". No se puede deshacer.") },
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
        fontFamily = Chakra,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 2.sp,
    )
}

/** Tarjeta de tema con una vista previa en miniatura de la app. */
@Composable
private fun OpcionTema(modo: ModoTema, elegido: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val forma = RoundedCornerShape(16.dp)
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
        Box(Modifier.fillMaxWidth().height(92.dp).clip(RoundedCornerShape(10.dp))) {
            when (modo) {
                ModoTema.CLARO -> Miniatura(PaletaClara, Modifier.fillMaxSize())
                ModoTema.OSCURO -> Miniatura(PaletaOscura, Modifier.fillMaxSize())
                ModoTema.SISTEMA -> Row(Modifier.fillMaxSize()) {
                    Miniatura(PaletaClara, Modifier.weight(1f).fillMaxSize())
                    Miniatura(PaletaOscura, Modifier.weight(1f).fillMaxSize())
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
                fontFamily = Chakra,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun Miniatura(p: Paleta, modifier: Modifier) {
    Column(modifier.background(p.fondo).padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().height(26.dp).clip(RoundedCornerShape(5.dp)).background(Brush.linearGradient(p.degradeJugador)))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(p.oro, p.disponible).forEach { c ->
                Box(Modifier.weight(1f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(p.tarjeta).border(0.5.dp, p.borde, RoundedCornerShape(4.dp)), contentAlignment = Alignment.CenterStart) {
                    Box(Modifier.padding(start = 3.dp).size(8.dp).clip(CircleShape).background(c))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(p.oro, p.regular, p.disponible, p.bloqueada).forEach { c ->
                Box(Modifier.size(10.dp).clip(CircleShape).background(c))
            }
        }
        Box(Modifier.width(30.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(p.texto2.copy(alpha = 0.5f)))
    }
}
