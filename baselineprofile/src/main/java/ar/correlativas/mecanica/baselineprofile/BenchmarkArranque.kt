package ar.correlativas.mecanica.baselineprofile

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compara el arranque y la fluidez sin precompilar contra con el Baseline Profile:
 *
 *   ./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest
 *
 * Conviene correrlo en un celular real: en emulador los tiempos no son representativos.
 */
@RunWith(AndroidJUnit4::class)
class BenchmarkArranque {
    @get:Rule val regla = MacrobenchmarkRule()

    @Test fun arranqueSinPerfil() = arranque(CompilationMode.None())

    @Test fun arranqueConPerfil() = arranque(CompilationMode.Partial(BaselineProfileMode.Require))

    @Test fun recorridoSinPerfil() = recorrido(CompilationMode.None())

    @Test fun recorridoConPerfil() = recorrido(CompilationMode.Partial(BaselineProfileMode.Require))

    private fun arranque(modo: CompilationMode) = regla.measureRepeated(
        packageName = PAQUETE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = modo,
        startupMode = StartupMode.COLD,
        iterations = 10,
        setupBlock = { pressHome() },
    ) { startActivityAndWait() }

    private fun recorrido(modo: CompilationMode) = regla.measureRepeated(
        packageName = PAQUETE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = modo,
        startupMode = StartupMode.WARM,
        iterations = 5,
        setupBlock = { pressHome(); startActivityAndWait() },
    ) { device.recorrerApp() }
}
