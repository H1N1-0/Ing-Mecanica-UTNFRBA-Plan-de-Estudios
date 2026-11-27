package ar.correlativas.mecanica

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ar.correlativas.mecanica.data.ModoTema
import ar.correlativas.mecanica.ui.AppCorrelativas
import ar.correlativas.mecanica.ui.CorrelativasViewModel
import ar.correlativas.mecanica.ui.TemaJuego
import ar.correlativas.mecanica.ui.esOscuro

class MainActivity : ComponentActivity() {
    private val vm: CorrelativasViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val preferencias by vm.preferencias.collectAsStateWithLifecycle()
            val modo = preferencias?.tema ?: ModoTema.SISTEMA
            val oscuro = esOscuro(modo)
            // Íconos de las barras del sistema claros u oscuros según el tema elegido.
            DisposableEffect(oscuro) {
                val estilo = if (oscuro) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
                onDispose {}
            }
            // Tamaño de pantalla: celular, tablet, horizontal. Se recalcula al girar.
            val ventana = calculateWindowSizeClass(this)
            TemaJuego(modo) { AppCorrelativas(vm, ventana) }
        }
    }
}
