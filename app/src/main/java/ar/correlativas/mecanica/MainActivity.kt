package ar.correlativas.mecanica

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import ar.correlativas.mecanica.ui.CorrelativasViewModel
import ar.correlativas.mecanica.ui.PantallaPrincipal
import ar.correlativas.mecanica.ui.TemaCorrelativas

class MainActivity : ComponentActivity() {
    private val vm: CorrelativasViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TemaCorrelativas { PantallaPrincipal(vm) }
        }
    }
}
