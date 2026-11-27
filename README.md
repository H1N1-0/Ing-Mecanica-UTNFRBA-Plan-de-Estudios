# Ing-Mecanica-UTNFRBA-Plan-de-Estudios

Correlativas de Ingeniería Mecánica (UTN FRBA, plan 2023).

- `correlativas.json`: datos del plan y reglas para cursar y rendir.
- `Correlativas – … .mhtml.mht`: versión web.
- `app/`: app Android (Kotlin + Jetpack Compose) con widget para la pantalla de inicio.

## App Android

- Lista por nivel (1 a 5 y Electivas). Cada materia se marca como sin cursar, regularizada o aprobada; la PPS, pendiente o acreditada.
- Calcula qué podés cursar y rendir según las reglas del JSON (Proyecto Final pide además 10 hs anuales de electivas aprobadas).
- Detalle de cada materia: qué te pide y qué habilita.
- Camino crítico: cuántas materias traba cada una, la cadena más larga hasta recibirte (años mínimos de cursada), cuellos de botella y una tarjeta de prioridad con las 3 materias que más conviene aprobar.
- Tres estilos visuales a elección en Ajustes, combinables con claro/oscuro: **Plano técnico** (blueprint u hoja de dibujo, dibujos técnicos, rótulo de lámina y manómetro), **Gamificado** y **Material You** (colores del fondo de pantalla en Android 12+). El widget sigue el estilo.
- Frases con medallón del autor (iniciales, color e ícono según su área).
- Versión para tablet y horizontal: barra lateral, lista y detalle lado a lado, mapa con panel de detalle e inicio en columnas.
- El progreso se guarda en el celular (DataStore). El widget muestra qué podés cursar, qué podés rendir y tu avance, y se actualiza al cambiar algo en la app.

### Descargar el APK

Cada push a `main` compila un APK release optimizado (R8 + Baseline Profile; GitHub Actions, `.github/workflows/android.yml`) y lo publica en **Releases**. Desde el celular: abrí la última release, descargá el `.apk` e instalalo (Android pide permiso para instalar apps desde el navegador).

Todas las builds (debug y release) se firman con la misma clave (`app/debug.keystore`), así que cada versión nueva se instala encima de la anterior sin perder el progreso.

### Compilar localmente

```
./gradlew testDebugUnitTest :app:assembleRelease
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`.
