package ar.correlativas.mecanica.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ar.correlativas.mecanica.data.MAX_NOMBRE
import ar.correlativas.mecanica.data.NOMBRE_PREDETERMINADO

/**
 * Pregunta del primer uso: "¿Cómo te llamás?". Es opcional: "Ahora no" deja el rótulo en
 * "Estudiante". El nombre se guarda solo en el celular y se cambia después desde Ajustes.
 */
@Composable
fun PreguntaNombre(onGuardar: (String) -> Unit, onOmitir: () -> Unit) {
    var texto by rememberSaveable { mutableStateOf("") }
    val guardar = { if (texto.isBlank()) onOmitir() else onGuardar(texto) }
    AlertDialog(
        onDismissRequest = onOmitir,
        containerColor = C.tarjeta,
        shape = C.formas.grande,
        title = { Text("¿Cómo te llamás?", color = C.texto, fontFamily = C.fuente, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Es opcional. Va en el rótulo del inicio y queda guardado solo en este celular. " +
                        "Lo podés cambiar cuando quieras desde Ajustes.",
                    color = C.texto2,
                    fontFamily = C.fuente,
                    fontSize = 14.sp,
                )
                OutlinedTextField(
                    value = texto,
                    onValueChange = { if (it.length <= MAX_NOMBRE) texto = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    placeholder = { Text(NOMBRE_PREDETERMINADO, fontFamily = C.fuente) },
                    singleLine = true,
                    shape = C.formas.chica,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { guardar() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = C.fondoAlto,
                        unfocusedContainerColor = C.fondoAlto,
                        focusedBorderColor = C.disponible,
                        unfocusedBorderColor = C.borde,
                        cursorColor = C.disponible,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = guardar) { Text("Guardar", color = C.disponible, fontFamily = C.fuente, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onOmitir) { Text("Ahora no", color = C.texto2, fontFamily = C.fuente) }
        },
    )
}
