package ar.correlativas.mecanica.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import ar.correlativas.mecanica.data.Materia

/**
 * Nombre de la materia para vistas compactas (tarjetas angostas, filas de prioridad): el nombre
 * completo si entra en [maxLineas] con el ancho disponible; si no, la sigla (AM1, IASI). Se decide
 * midiendo antes de dibujar, así no parpadea. TalkBack lee siempre el nombre completo.
 */
@Composable
fun NombreOAbrev(materia: Materia, estilo: TextStyle, modifier: Modifier = Modifier, maxLineas: Int = 2) {
    val medidor = rememberTextMeasurer(cacheSize = 4)
    BoxWithConstraints(modifier) {
        val ancho = constraints.maxWidth
        val entra = remember(materia.id, ancho, estilo, maxLineas) {
            ancho == Constraints.Infinity || !medidor.measure(
                materia.nombre, estilo, maxLines = maxLineas, constraints = Constraints(maxWidth = ancho),
            ).hasVisualOverflow
        }
        Text(
            if (entra) materia.nombre else materia.abrev,
            Modifier.semantics { contentDescription = materia.nombre },
            style = estilo,
            maxLines = maxLineas,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
