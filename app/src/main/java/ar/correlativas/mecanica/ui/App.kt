package ar.correlativas.mecanica.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.background
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.EstadoNodo
import ar.correlativas.mecanica.data.ModoTema

enum class Pestania(val titulo: String, val icono: Int) {
    INICIO("Inicio", R.drawable.ic_inicio),
    MAPA("Mapa", R.drawable.ic_mapa),
    LISTA("Lista", R.drawable.ic_lista),
    LOGROS("Logros", R.drawable.ic_trofeo),
}

/** Pantallas que se abren encima de las pestañas. */
enum class Superpuesta { AJUSTES, TEST, PERFIL }

@Composable
fun AppCorrelativas(vm: CorrelativasViewModel, pestaniaInicial: Pestania = Pestania.INICIO) {
    val avance by vm.avance.collectAsStateWithLifecycle()
    val preferencias by vm.preferencias.collectAsStateWithLifecycle()
    val desbloqueo by vm.desbloqueo.collectAsStateWithLifecycle()
    var pestania by rememberSaveable { mutableStateOf(pestaniaInicial) }
    var detalle by rememberSaveable { mutableStateOf<Int?>(null) }
    var filtroLista by rememberSaveable { mutableStateOf<EstadoNodo?>(null) }
    var superpuesta by rememberSaveable { mutableStateOf<Superpuesta?>(null) }
    // Resultado recién calculado (porcentajes, si es la primera vez = logro nuevo).
    var resultadoNuevo by remember { mutableStateOf<Pair<Map<String, Int>, Boolean>?>(null) }
    val perfil = preferencias?.perfil
    val testHecho = perfil != null

    Scaffold(
        containerColor = C.fondo,
        bottomBar = {
            NavigationBar(containerColor = C.fondoAlto, tonalElevation = 0.dp) {
                Pestania.entries.forEach { p ->
                    NavigationBarItem(
                        selected = p == pestania,
                        onClick = { pestania = p },
                        icon = { Ic(p.icono, if (p == pestania) (if (C.oscuro) C.fondo else Color.White) else C.texto2, tam = 22.dp) },
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
                        frases = vm.frases,
                        test = vm.test,
                        perfil = perfil,
                        onAbrir = { detalle = it },
                        onLista = { filtroLista = it; pestania = Pestania.LISTA },
                        onLogros = { pestania = Pestania.LOGROS },
                        onAjustes = { superpuesta = Superpuesta.AJUSTES },
                        onTest = { superpuesta = Superpuesta.TEST },
                        onPerfil = { resultadoNuevo = null; superpuesta = Superpuesta.PERFIL },
                    )
                    Pestania.MAPA -> PantallaMapa(a, vm.mapa, onAbrir = { detalle = it })
                    Pestania.LISTA -> PantallaLista(
                        avance = a,
                        filtro = filtroLista,
                        onFiltro = { filtroLista = it },
                        onCambio = vm::cambiar,
                        onAbrir = { detalle = it },
                    )
                    Pestania.LOGROS -> PantallaLogros(a, vm.logros, testHecho)
                }
            }
        }
    }

    val a = avance
    if (a != null) {
        AnimatedContent(
            targetState = superpuesta,
            transitionSpec = {
                (slideInHorizontally(tween(280)) { it / 4 } + fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(tween(220)) { it / 4 } + fadeOut(tween(220)))
            },
            label = "superpuesta",
        ) { s ->
            if (s == null) return@AnimatedContent
            Box(Modifier.fillMaxSize().background(C.fondo).windowInsetsPadding(WindowInsets.safeDrawing)) {
                when (s) {
                    Superpuesta.AJUSTES -> PantallaAjustes(
                        tema = preferencias?.tema ?: ModoTema.SISTEMA,
                        perfil = perfil,
                        test = vm.test,
                        onTema = vm::cambiarTema,
                        onTest = { superpuesta = Superpuesta.TEST },
                        onVerPerfil = { resultadoNuevo = null; superpuesta = Superpuesta.PERFIL },
                        onBorrar = vm::borrarTodo,
                        onVolver = { superpuesta = null },
                    )
                    Superpuesta.TEST -> PantallaTest(
                        test = vm.test,
                        onTerminar = { respuestas ->
                            val primeraVez = perfil == null
                            resultadoNuevo = vm.terminarTest(respuestas) to primeraVez
                            superpuesta = Superpuesta.PERFIL
                        },
                        onSalir = { superpuesta = null },
                    )
                    Superpuesta.PERFIL -> {
                        val porcentajes = resultadoNuevo?.first ?: perfil?.porcentajes
                        if (porcentajes == null) {
                            LaunchedEffect(Unit) { superpuesta = null }
                        } else {
                            PantallaPerfil(
                                test = vm.test,
                                porcentajes = porcentajes,
                                avance = a,
                                logroNuevo = resultadoNuevo?.second == true,
                                onRepetir = { superpuesta = Superpuesta.TEST },
                                onCerrar = { superpuesta = null; resultadoNuevo = null },
                                onAbrir = { detalle = it },
                            )
                        }
                    }
                }
            }
        }

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
}
