// Genera el Baseline Profile de la app (y mide el arranque) en un celular o emulador conectado:
//   ./gradlew :app:generateReleaseBaselineProfile
// El resultado queda en app/src/release/generated/baselineProfiles/ y se versiona.
plugins {
    id("com.android.test")
    id("org.jetbrains.kotlin.android")
    id("androidx.baselineprofile")
}

android {
    namespace = "ar.correlativas.mecanica.baselineprofile"
    compileSdk = 35

    defaultConfig {
        // Generar perfiles necesita Android 9+.
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.3.3")
}
