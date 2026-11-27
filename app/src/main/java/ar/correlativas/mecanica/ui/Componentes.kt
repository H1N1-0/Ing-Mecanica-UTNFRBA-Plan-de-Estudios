package ar.correlativas.mecanica.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.data.Estado
import ar.correlativas.mecanica.data.Nivel
import ar.correlativas.mecanica.data.Tipo

/** Fondo cuadriculado como el rótulo de la web. */
fun Modifier.grilla(color: Color, paso: Dp = 16.dp): Modifier = drawBehind {
    val p = paso.toPx()
    var x = 0f
    while (x < size.width) { drawLine(color, Offset(x, 0f), Offset(x, size.height), 1f); x += p }
    var y = 0f
    while (y < size.height) { drawLine(color, Offset(0f, y), Offset(size.width, y), 1f); y += p }
}

/** Número de la materia en un círculo, relleno según el estado. */
@Composable
fun Globo(numero: String, estado: Estado, modifier: Modifier = Modifier, tam: Dp = 34.dp) {
    val p = LocalPaleta.current
    val (fondo, texto, borde) = when (estado) {
        Estado.SIN_CURSAR -> Triple(p.superficie, p.tinta, p.lineaFuerte)
        Estado.REGULARIZADA -> Triple(p.reg, p.sobreReg, p.reg)
        Estado.APROBADA -> Triple(p.apr, p.sobreApr, p.apr)
    }
    Box(
        modifier
            .size(tam)
            .clip(CircleShape)
            .background(fondo)
            .border(1.5.dp, borde, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            numero,
            color = texto,
            fontFamily = Condensada,
            fontWeight = FontWeight.Bold,
            fontSize = if (numero.length > 2) 12.sp else 15.sp,
        )
    }
}

/** Trazo de referencia: punteado = regularizada, lleno = aprobada (como en la web). */
@Composable
fun LineaNivel(nivel: Nivel, modifier: Modifier = Modifier) {
    val p = LocalPaleta.current
    Canvas(modifier.size(width = 20.dp, height = 4.dp)) {
        val y = size.height / 2
        if (nivel == Nivel.REGULARIZADA) {
            drawLine(
                p.reg, Offset(0f, y), Offset(size.width, y), 2.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())),
            )
        } else {
            drawLine(p.apr, Offset(0f, y), Offset(size.width, y), 3.dp.toPx())
        }
    }
}

@Composable
fun Etiqueta(texto: String, color: Color, fondo: Color, modifier: Modifier = Modifier) {
    Text(
        texto,
        modifier
            .clip(RoundedCornerShape(5.dp))
            .background(fondo)
            .padding(horizontal = 6.dp, vertical = 1.dp),
        color = color,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

/** Selector Sin cursar / Regularizada / Aprobada (o Pendiente / Acreditada para la PPS). */
@Composable
fun SelectorEstado(
    tipo: Tipo,
    estado: Estado,
    onCambio: (Estado) -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = LocalPaleta.current
    val opciones = if (tipo == Tipo.PPS) {
        listOf(Estado.SIN_CURSAR to "Pendiente", Estado.APROBADA to "Acreditada")
    } else {
        listOf(Estado.SIN_CURSAR to "Sin cursar", Estado.REGULARIZADA to "Regularizada", Estado.APROBADA to "Aprobada")
    }
    val forma = RoundedCornerShape(9.dp)
    Row(
        modifier
            .height(40.dp)
            .clip(forma)
            .border(1.5.dp, p.linea, forma)
            .background(p.superficie),
        horizontalArrangement = Arrangement.Start,
    ) {
        opciones.forEachIndexed { i, (valor, texto) ->
            val elegido = valor == estado
            val (fondo, color) = when {
                !elegido -> Color.Transparent to p.tinta2
                valor == Estado.REGULARIZADA -> p.reg to p.sobreReg
                valor == Estado.APROBADA -> p.apr to p.sobreApr
                else -> p.tinta to p.papel
            }
            if (i > 0) Box(Modifier.width(1.dp).fillMaxHeight().background(p.linea))
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(fondo)
                    .semantics { selected = elegido; contentDescription = texto }
                    .clickable(role = Role.RadioButton) { onCambio(valor) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    texto,
                    color = color,
                    fontSize = 14.sp,
                    fontWeight = if (elegido) FontWeight.Bold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
