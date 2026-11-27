package ar.correlativas.mecanica.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.EstadoNodo

enum class Pestania(val titulo: String, val icono: Int) {
    INICIO("Inicio", R.drawable.ic_inicio),
    MAPA("Mapa", R.drawable.ic_mapa),
    LISTA("Lista", R.drawable.ic_lista),
    LOGROS("Logros", R.drawable.ic_trofeo),
}

@Composable
fun AppCorrelativas(vm: CorrelativasViewModel, pestaniaInicial: Pestania = Pestania.INICIO) {
    val avance by vm.avance.collectAsStateWithLifecycle()
    val desbloqueo by vm.desbloqueo.collectAsStateWithLifecycle()
    var pestania by rememberSaveable { mutableStateOf(pestaniaInicial) }
    var detalle by rememberSaveable { mutableStateOf<Int?>(null) }
    var filtroLista by rememberSaveable { mutableStateOf<EstadoNodo?>(null) }
    var confirmarBorrado by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = C.fondo,
        bottomBar = {
            NavigationBar(containerColor = C.fondoAlto, tonalElevation = 0.dp) {
                Pestania.entries.forEach { p ->
                    NavigationBarItem(
                        selected = p == pestania,
                        onClick = { pestania = p },
                        icon = { Ic(p.icono, if (p == pestania) C.fondo else C.texto2, tam = 22.dp) },
                        label = { Text(p.titulo) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = C.disponible,
                            selectedTextColor = C.disponible,
                            unselectedTextColor = C.texto2,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val a = avance
            if (a == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = C.disponible)
                return@Box
            }
            Crossfade(pestania, animationSpec = tween(220), label = "pestania") { p ->
                when (p) {
                    Pestania.INICIO -> PantallaInicio(
                        avance = a,
                        logros = vm.logros,
                        onAbrir = { detalle = it },
                        onLista = { filtroLista = it; pestania = Pestania.LISTA },
                        onLogros = { pestania = Pestania.LOGROS },
                        onBorrar = { confirmarBorrado = true },
                    )
                    Pestania.MAPA -> PantallaMapa(a, vm.mapa, onAbrir = { detalle = it })
                    Pestania.LISTA -> PantallaLista(
                        avance = a,
                        filtro = filtroLista,
                        onFiltro = { filtroLista = it },
                        onCambio = vm::cambiar,
                        onAbrir = { detalle = it },
                    )
                    Pestania.LOGROS -> PantallaLogros(a, vm.logros)
                }
            }
        }
    }

    val a = avance
    if (a != null) {
        detalle?.let { id ->
            a.plan[id]?.let { m ->
                HojaDetalle(
                    materia = m,
                    avance = a,
                    onCambio = { vm.cambiar(m.id, it) },
                    onAbrir = { detalle = it },
                    onCerrar = { detalle = null },
                )
            }
        }
        desbloqueo?.let { d ->
            Celebracion(d, a, onCerrar = vm::cerrarDesbloqueo, onAbrir = { vm.cerrarDesbloqueo(); detalle = it })
        }
    }

    if (confirmarBorrado) {
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            containerColor = C.tarjeta,
            title = { Text("¿Reiniciar la partida?") },
            text = { Text("Todas las materias vuelven a \"sin cursar\". No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = { vm.borrarTodo(); confirmarBorrado = false }) { Text("Reiniciar", color = C.peligro) }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") } },
        )
    }
}
