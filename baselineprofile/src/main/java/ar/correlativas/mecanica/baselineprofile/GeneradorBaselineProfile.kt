package ar.correlativas.mecanica.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

const val PAQUETE = "ar.correlativas.mecanica"

/**
 * Recorre lo que más se usa (arranque, inicio, mapa, lista, detalle) para que el Baseline Profile
 * precompile ese código. Correr con un dispositivo conectado:
 *
 *   ./gradlew :app:generateReleaseBaselineProfile
 */
@RunWith(AndroidJUnit4::class)
class GeneradorBaselineProfile {
    @get:Rule val regla = BaselineProfileRule()

    @Test fun generar() = regla.collect(packageName = PAQUETE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.recorrerApp()
    }
}

/** Uso típico: scrollear el inicio, mover el mapa, filtrar la lista y abrir una materia. */
fun UiDevice.recorrerApp() {
    val espera = 5_000L
    wait(Until.hasObject(By.text("Inicio")), espera)
    scrollear()

    findObject(By.text("Mapa"))?.click()
    wait(Until.hasObject(By.text("Mapa de la carrera")), espera)
    findObject(By.text("Ver camino crítico"))?.click()
    waitForIdle()
    // Desplazar el mapa.
    swipe(displayWidth / 2, displayHeight * 2 / 3, displayWidth / 3, displayHeight / 3, 20)
    waitForIdle()

    findObject(By.text("Lista"))?.click()
    wait(Until.hasObject(By.text("Buscar materia o número")), espera)
    scrollear()
    findObject(By.text("Física I"))?.click()
    waitForIdle()
    pressBack()
    waitForIdle()

    findObject(By.text("Inicio"))?.click()
    waitForIdle()
}

private fun UiDevice.scrollear() {
    val lista = findObject(By.scrollable(true)) ?: return
    lista.setGestureMargin(displayWidth / 8)
    repeat(2) {
        lista.fling(Direction.DOWN)
        waitForIdle()
    }
    lista.fling(Direction.UP)
    waitForIdle()
}
