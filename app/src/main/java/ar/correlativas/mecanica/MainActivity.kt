package ar.correlativas.mecanica

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ar.correlativas.mecanica.ui.AppCorrelativas
import ar.correlativas.mecanica.ui.CorrelativasViewModel
import ar.correlativas.mecanica.ui.TemaJuego

class MainActivity : ComponentActivity() {
    private val vm: CorrelativasViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // La app es oscura siempre: íconos claros en las barras del sistema.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            TemaJuego { AppCorrelativas(vm) }
        }
    }
}
