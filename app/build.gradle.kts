import com.android.build.api.variant.ApplicationAndroidComponentsExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.baselineprofile")
}

// En CI cada build sube el versionCode, así el APK nuevo se instala encima del anterior.
val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "ar.correlativas.mecanica"
    compileSdk = 35

    defaultConfig {
        applicationId = "ar.correlativas.mecanica"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        // Keystore de debug versionado en el repo: todas las builds de CI firman con la
        // misma clave y Android acepta actualizar la app sin desinstalarla.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        // Release optimizada: R8 (código y recursos) + Baseline Profile. Se firma con la MISMA
        // clave que las builds de debug (app/debug.keystore) y tiene el mismo applicationId, así
        // que se instala encima de la versión que ya tenés sin perder el progreso.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        // Tests de interfaz con Robolectric (navegación y pantallas, sin emulador).
        unitTests.isIncludeAndroidResources = true
    }
}

// correlativas.json, perfiles.json y frases.json viven en la raíz del repo (para editarlos fácil):
// se copian a los assets de la app en cada build.
abstract class CopiarDatos : DefaultTask() {
    @get:InputFiles
    abstract val jsons: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copiar() {
        val destino = outputDir.get().asFile
        destino.mkdirs()
        jsons.files.forEach { it.copyTo(destino.resolve(it.name), overwrite = true) }
    }
}

val copiarDatos = tasks.register<CopiarDatos>("copiarDatos") {
    jsons.from(listOf("correlativas.json", "perfiles.json", "frases.json").map { rootProject.file(it) })
    outputDir.set(layout.buildDirectory.dir("generated/datos"))
}

extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(copiarDatos, CopiarDatos::outputDir)
}

composeCompiler {
    // Las clases de data/ son inmutables: marcarlas estables evita recomposiciones innecesarias.
    stabilityConfigurationFile = layout.projectDirectory.file("estabilidad_compose.conf")
}

baselineProfile {
    // El perfil se genera a mano con un dispositivo (ver baselineprofile/) y queda versionado en
    // src/release/generated/baselineProfiles: CI solo lo empaqueta, no necesita un emulador.
    automaticGenerationDuringBuild = false
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    // Instala el Baseline Profile al abrir la app aunque no venga de Play Store (APK de Releases).
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    baselineProfile(project(":baselineprofile"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // QR de la copia de seguridad: ZXing genera y lee el código (todo local, sin internet).
    implementation("com.google.zxing:core:3.5.3")
    // Leer el QR con la cámara (pide permiso de cámara solo al usarla).
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")
    implementation("androidx.glance:glance-appwidget:1.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    // Tests de interfaz en la JVM: Robolectric + Compose UI Test (sin emulador).
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // "Atrás" del sistema en los tests (va a la ventana con foco: diálogo, hoja o actividad).
    testImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
