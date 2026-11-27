import com.android.build.api.variant.ApplicationAndroidComponentsExtension

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
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
        release {
            isMinifyEnabled = false
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
    }
}

// correlativas.json vive en la raíz del repo: se copia a los assets de la app en cada build.
abstract class CopiarCorrelativas : DefaultTask() {
    @get:InputFile
    abstract val json: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copiar() {
        val destino = outputDir.get().asFile
        destino.mkdirs()
        json.get().asFile.copyTo(destino.resolve("correlativas.json"), overwrite = true)
    }
}

val copiarCorrelativas = tasks.register<CopiarCorrelativas>("copiarCorrelativas") {
    json.set(rootProject.layout.projectDirectory.file("correlativas.json"))
    outputDir.set(layout.buildDirectory.dir("generated/correlativas"))
}

extensions.getByType<ApplicationAndroidComponentsExtension>().onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(copiarCorrelativas, CopiarCorrelativas::outputDir)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.glance:glance-appwidget:1.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
