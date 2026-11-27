package ar.correlativas.mecanica.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.correlativas.mecanica.R
import ar.correlativas.mecanica.data.EstadoNodo
import ar.correlativas.mecanica.data.Estilo
import ar.correlativas.mecanica.data.ModoTema

/** Cada pestaña tiene su dibujo técnico de fondo (estilo Plano técnico). */
enum class Pestania(val titulo: String, val icono: Int, val dibujo: Dibujo) {
    INICIO("Inicio", R.drawable.ic_inicio, Dibujo.ENGRANAJES),
    MAPA("Mapa", R.drawable.ic_mapa, Dibujo.PISTON),
    LISTA("Lista", R.drawable.ic_lista, Dibujo.BOMBA),
    LOGROS("Logros", R.drawable.ic_trofeo, Dibujo.TURBINA),
}

/** Pantallas que se abren encima de las pestañas. */
enum class Superpuesta { AJUSTES, TEST, PERFIL }

/**
 * Disposición según el tamaño de pantalla (WindowSizeClass):
 *  - Celular en vertical (ancho compacto): barra inferior y detalle en hoja, como siempre.
 *  - Tablet y horizontal (ancho medio o expandido): barra lateral, lista y detalle lado a lado,
 *    mapa con el detalle al costado e inicio en dos o tres columnas.
 */
data class Disposicion(val amplia: Boolean, val columnasInicio: Int) {
    companion object {
        val CELULAR = Disposicion(amplia = false, columnasInicio = 1)

        fun para(ventana: WindowSizeClass, anchoDp: Float): Disposicion =
            if (ventana.widthSizeClass == WindowWidthSizeClass.Compact) CELULAR
            else Disposicion(
                amplia = true,
                // Sacando la barra lateral, una columna cada ~360 dp (2 en tablet vertical, 3 en horizontal).
                columnasInicio = ((anchoDp - ANCHO_RIEL) / 360f).toInt().coerceIn(1, 3),
            )

        const val ANCHO_RIEL = 80f
    }
}

/** Pestañas que en pantalla ancha muestran el detalle en un panel al costado. */
private val CON_PANEL = setOf(Pestania.LISTA, Pestania.MAPA)

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun AppCorrelativas(
    vm: CorrelativasViewModel,
    ventana: WindowSizeClass,
    pestaniaInicial: Pestania = Pestania.INICIO,
) {
    val avance by vm.avance.collectAsStateWithLifecycle()
    val critico by vm.critico.collectAsStateWithLifecycle()
    val preferencias by vm.preferencias.collectAsStateWithLifecycle()
    val desbloqueo by vm.desbloqueo.collectAsStateWithLifecycle()
    // Todo el estado de navegación se guarda arriba de todo, antes de elegir la disposición,
    // para que girar la pantalla (o pasar de celular a tablet) no pierda dónde estabas.
    var pestania by rememberSaveable { mutableStateOf(pestaniaInicial) }
    var detalle by rememberSaveable { mutableStateOf<Int?>(null) }
    var filtroLista by rememberSaveable { mutableStateOf<EstadoNodo?>(null) }
    var superpuesta by rememberSaveable { mutableStateOf<Superpuesta?>(null) }
    val scrollInicio = rememberLazyStaggeredGridState()
    val scrollLista = rememberLazyListState()
    val vistaMapa = rememberVistaMapa()
    // Resultado recién calculado (porcentajes, si es la primera vez = logro nuevo).
    var resultadoNuevo by remember { mutableStateOf<Pair<Map<String, Int>, Boolean>?>(null) }
    val perfil = preferencias?.perfil
    val testHecho = perfil != null

    val anchoDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val disposicion = Disposicion.para(ventana, anchoDp)
    val irA: (Pestania) -> Unit = { p ->
        // En pantalla ancha el detalle vive en el panel de Lista/Mapa: al ir a otra pestaña se cierra.
        if (disposicion.amplia && p !in CON_PANEL) detalle = null
        pestania = p
    }
    val enPanel = disposicion.amplia && pestania in CON_PANEL && superpuesta == null

    val contenido: @Composable () -> Unit = {
        val a = avance
        val c = critico
        Box(Modifier.fillMaxSize()) {
            if (a == null || c == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = C.disponible)
                return@Box
            }
            Crossfade(pestania, animationSpec = tween(C.movimiento.rapida), label = "pestania") { p ->
                Fondo(p.dibujo) {
                when (p) {
                    Pestania.INICIO -> PantallaInicio(
                        avance = a,
                        nombre = preferencias?.nombre.orEmpty(),
                        critico = c,
                        logros = vm.logros,
                        frases = vm.frases,
                        test = vm.test,
                        perfil = perfil,
                        onAbrir = { detalle = it },
                        onLista = { filtroLista = it; irA(Pestania.LISTA) },
                        onLogros = { irA(Pestania.LOGROS) },
                        onAjustes = { superpuesta = Superpuesta.AJUSTES },
                        onTest = { superpuesta = Superpuesta.TEST },
                        onPerfil = { resultadoNuevo = null; superpuesta = Superpuesta.PERFIL },
                        onVerCamino = { vistaMapa.mostrarCritico(); irA(Pestania.MAPA) },
                        columnas = disposicion.columnasInicio,
                        scroll = scrollInicio,
                    )
                    Pestania.MAPA -> if (enPanel) {
                        Row(Modifier.fillMaxSize()) {
                            Box(Modifier.weight(1f)) {
                                PantallaMapa(a, vm.mapa, c, onAbrir = { detalle = it }, vista = vistaMapa, seleccionada = detalle)
                            }
                            AnimatedVisibility(
                                visible = detalle != null,
                                enter = expandHorizontally(tween(260)) + fadeIn(tween(260)),
                                exit = shrinkHorizontally(tween(220)) + fadeOut(tween(220)),
                            ) {
                                // Recuerda la última materia mientras el panel se cierra.
                                var ultima by remember { mutableStateOf(detalle) }
                                if (detalle != null) ultima = detalle
                                Row {
                                    VerticalDivider(color = C.borde)
                                    PanelDetalle(
                                        id = ultima,
                                        avance = a,
                                        onCambio = vm::cambiar,
                                        onAbrir = { detalle = it },
                                        onCerrar = { detalle = null },
                                        modifier = Modifier.width(400.dp),
                                    )
                                }
                            }
                        }
                    } else {
                        PantallaMapa(a, vm.mapa, c, onAbrir = { detalle = it }, vista = vistaMapa)
                    }
                    Pestania.LISTA -> if (enPanel) {
                        BoxWithConstraints(Modifier.fillMaxSize()) {
                        // La lista con un ancho cómodo (360–460 dp); el detalle ocupa el resto.
                        val anchoLista = (maxWidth * 0.45f).coerceIn(360.dp, 460.dp)
                        Row(Modifier.fillMaxSize()) {
                            PantallaLista(
                                avance = a,
                                filtro = filtroLista,
                                onFiltro = { filtroLista = it },
                                onCambio = vm::cambiar,
                                onAbrir = { detalle = it },
                                modifier = Modifier.width(anchoLista),
                                seleccionada = detalle,
                                scroll = scrollLista,
                            )
                            VerticalDivider(color = C.borde)
                            PanelDetalle(
                                id = detalle,
                                avance = a,
                                onCambio = vm::cambiar,
                                onAbrir = { detalle = it },
                                onCerrar = null,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        }
                    } else {
                        PantallaLista(
                            avance = a,
                            filtro = filtroLista,
                            onFiltro = { filtroLista = it },
                            onCambio = vm::cambiar,
                            onAbrir = { detalle = it },
                            scroll = scrollLista,
                        )
                    }
                    Pestania.LOGROS -> PantallaLogros(a, vm.logros, testHecho)
                }
                }
            }
        }
    }

    CompositionLocalProvider(LocalCritico provides critico) {
        if (disposicion.amplia) {
            // Tablet y horizontal: barra de navegación lateral.
            Row(Modifier.fillMaxSize().background(C.fondo)) {
                NavigationRail(
                    containerColor = C.fondoAlto,
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
                    header = {
                        Box(
                            Modifier.padding(top = 8.dp, bottom = 12.dp).size(44.dp).clip(C.formas.media).background(C.tarjetaAlta),
                            contentAlignment = Alignment.Center,
                        ) { Ic(R.drawable.ic_engranaje, C.disponible, tam = 24.dp) }
                    },
                ) {
                    Spacer(Modifier.weight(1f))
                    Pestania.entries.forEach { p ->
                        NavigationRailItem(
                            selected = p == pestania,
                            onClick = { irA(p) },
                            icon = { Ic(p.icono, if (p == pestania) C.sobreAcento else C.texto2, tam = 22.dp) },
                            label = { Text(p.titulo) },
                            colors = NavigationRailItemDefaults.colors(
                                indicatorColor = C.disponible,
                                selectedTextColor = C.disponible,
                                unselectedTextColor = C.texto2,
                            ),
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                VerticalDivider(color = C.borde)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical)),
                ) { contenido() }
            }
        } else {
            Scaffold(
                containerColor = C.fondo,
                bottomBar = {
                    NavigationBar(containerColor = C.fondoAlto, tonalElevation = 0.dp) {
                        Pestania.entries.forEach { p ->
                            NavigationBarItem(
                                selected = p == pestania,
                                onClick = { irA(p) },
                                icon = { Ic(p.icono, if (p == pestania) C.sobreAcento else C.texto2, tam = 22.dp) },
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
                Box(Modifier.fillMaxSize().padding(padding)) { contenido() }
            }
        }

        Superpuestas(
            vm = vm,
            superpuesta = superpuesta,
            onSuperpuesta = { superpuesta = it },
            perfil = perfil,
            resultadoNuevo = resultadoNuevo,
            onResultado = { resultadoNuevo = it },
            onAbrir = { detalle = it },
        )

        val a = avance
        if (a != null) {
            if (!enPanel) {
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
            }
            desbloqueo?.let { d ->
                Celebracion(d, a, onCerrar = vm::cerrarDesbloqueo, onAbrir = { vm.cerrarDesbloqueo(); detalle = it })
            }
            // Primer uso: se pregunta el nombre una sola vez (es opcional y queda solo en el celular).
            val prefs = preferencias
            if (prefs != null && !prefs.nombrePreguntado && superpuesta == null && desbloqueo == null) {
                PreguntaNombre(onGuardar = vm::cambiarNombre, onOmitir = vm::omitirNombre)
            }
        }
    }
}

/** Detalle de una materia en un panel fijo (tablet), en vez de la hoja que sube desde abajo. */
@Composable
private fun PanelDetalle(
    id: Int?,
    avance: ar.correlativas.mecanica.data.Avance,
    onCambio: (Int, ar.correlativas.mecanica.data.Estado) -> Unit,
    onAbrir: (Int) -> Unit,
    onCerrar: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val materia = id?.let { avance.plan[it] }
    Box(modifier.fillMaxHeight().background(C.fondoAlto)) {
        if (materia == null) {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(C.tarjetaAlta), contentAlignment = Alignment.Center) {
                    Ic(R.drawable.ic_lista, C.disponible, tam = 34.dp)
                }
                Text(
                    "Tocá una materia",
                    Modifier.padding(top = 14.dp),
                    color = C.texto,
                    fontFamily = C.fuente,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                )
                Text(
                    "Acá vas a ver qué te pide, qué desbloquea y cuánto traba.",
                    Modifier.padding(top = 4.dp),
                    color = C.texto2,
                    fontFamily = C.fuente,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
            return@Box
        }
        val scroll = rememberScrollState()
        LaunchedEffect(materia.id) { scroll.scrollTo(0) }
        Column(Modifier.fillMaxSize()) {
            if (onCerrar != null) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp, end = 8.dp), horizontalArrangement = Arrangement.End) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(C.tarjeta)
                            .semantics { contentDescription = "Cerrar detalle" }
                            .clickable(role = Role.Button, onClick = onCerrar),
                        contentAlignment = Alignment.Center,
                    ) { Text("✕", color = C.texto2, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
            } else {
                Spacer(Modifier.height(12.dp))
            }
            ContenidoDetalle(materia, avance, { onCambio(materia.id, it) }, onAbrir, Modifier.verticalScroll(scroll))
        }
    }
}

@Composable
private fun Superpuestas(
    vm: CorrelativasViewModel,
    superpuesta: Superpuesta?,
    onSuperpuesta: (Superpuesta?) -> Unit,
    perfil: ar.correlativas.mecanica.data.ResultadoPerfil?,
    resultadoNuevo: Pair<Map<String, Int>, Boolean>?,
    onResultado: (Pair<Map<String, Int>, Boolean>?) -> Unit,
    onAbrir: (Int) -> Unit,
) {
    val avance by vm.avance.collectAsStateWithLifecycle()
    val preferencias by vm.preferencias.collectAsStateWithLifecycle()
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
            Box(
                Modifier.fillMaxSize().background(C.fondo),
                contentAlignment = Alignment.TopCenter,
            ) {
                CapaFondo(if (s == Superpuesta.AJUSTES) Dibujo.RODAMIENTO else Dibujo.RESORTE, Modifier.matchParentSize())
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
                // En tablet las pantallas superpuestas no se estiran de borde a borde.
                Box(Modifier.widthIn(max = 760.dp)) {
                when (s) {
                    Superpuesta.AJUSTES -> PantallaAjustes(
                        tema = preferencias?.tema ?: ModoTema.SISTEMA,
                        estilo = preferencias?.estilo ?: Estilo.PREDETERMINADO,
                        nombre = preferencias?.nombre.orEmpty(),
                        onEstilo = vm::cambiarEstilo,
                        onNombre = vm::cambiarNombre,
                        perfil = perfil,
                        test = vm.test,
                        onTema = vm::cambiarTema,
                        onTest = { onSuperpuesta(Superpuesta.TEST) },
                        onVerPerfil = { onResultado(null); onSuperpuesta(Superpuesta.PERFIL) },
                        onBorrar = vm::borrarTodo,
                        onVolver = { onSuperpuesta(null) },
                    )
                    Superpuesta.TEST -> PantallaTest(
                        test = vm.test,
                        onTerminar = { respuestas ->
                            val primeraVez = perfil == null
                            onResultado(vm.terminarTest(respuestas) to primeraVez)
                            onSuperpuesta(Superpuesta.PERFIL)
                        },
                        onSalir = { onSuperpuesta(null) },
                    )
                    Superpuesta.PERFIL -> {
                        val porcentajes = resultadoNuevo?.first ?: perfil?.porcentajes
                        if (porcentajes == null) {
                            LaunchedEffect(Unit) { onSuperpuesta(null) }
                        } else {
                            PantallaPerfil(
                                test = vm.test,
                                porcentajes = porcentajes,
                                avance = a,
                                logroNuevo = resultadoNuevo?.second == true,
                                onRepetir = { onSuperpuesta(Superpuesta.TEST) },
                                onCerrar = { onSuperpuesta(null); onResultado(null) },
                                onAbrir = onAbrir,
                            )
                        }
                    }
                }
                }
                }
            }
        }
    }
}
