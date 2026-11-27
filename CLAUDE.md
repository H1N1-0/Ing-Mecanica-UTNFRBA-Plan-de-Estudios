# CLAUDE.md

Correlativas de Ingeniería Mecánica (UTN FRBA, plan 2023): los datos del plan en JSON y una app
Android (Kotlin + Jetpack Compose) gamificada, con widget, para marcar materias y ver qué se puede
cursar y rendir. Todo el código, los textos y los commits están en español rioplatense.

## Estructura

```
correlativas.json        Plan de estudios y correlativas (fuente de verdad)
perfiles.json            Perfiles del test vocacional «¿Qué ingeniero mecánico sos?»
frases.json              Frases motivacionales
*.mhtml.mht              Versión web original de las correlativas
app/
  build.gradle.kts       Copia los tres JSON a los assets en cada build (tarea copiarDatos)
  debug.keystore         Clave de debug versionada: todas las builds se instalan encima
  src/main/java/ar/correlativas/mecanica/
    MainActivity.kt      Calcula el WindowSizeClass y arranca AppCorrelativas
    data/
      Plan.kt            Modelo (Materia, Requisitos, Tipo) y parseo de correlativas.json
      Calculo.kt         Avance: reglas para cursar y rendir, habilita(), CodecEstados
      Progreso.kt        Guarda los estados en DataStore ("progreso") y actualiza el widget
      Ajustes.kt         Tema y resultado del test (DataStore aparte, "ajustes")
      Juego.kt           EstadoNodo, porcentaje, rangos, logros y layout del mapa (Mapa)
      CaminoCritico.kt   Grafo de dependencias, materias que traba cada una, camino crítico
      Contenido.kt       Frases y test vocacional
    ui/
      App.kt             Navegación y disposición adaptativa (celular / tablet / horizontal)
      CorrelativasViewModel.kt  Flujos de avance, camino crítico, preferencias y desbloqueos
      PantallaInicio.kt, PantallaMapa.kt, PantallaLista.kt, PantallaLogros.kt, ...
      HojaDetalle.kt     ContenidoDetalle (hoja en celular, panel lateral en tablet)
      CaminoCriticoUi.kt Tarjetas "Camino crítico" y "Prioridad", bloque del detalle
      Tema.kt            Paletas clara y oscura (objeto C), tipografía Chakra Petch
    widget/              Widget de Glance
  src/test/              Tests JVM (JUnit) que leen el correlativas.json real
```

## Datos

- `correlativas.json` tiene `obligatorias` (id 1–40, nivel 1–5), `electivas` (id ≥ 101, con
  `horas_anuales`) y `pps` (id 41). Cada materia tiene `cursar` y `rendir`, con listas de ids
  `regularizadas` y `aprobadas`. Proyecto Final (id 40) tiene `rendir.regla` en vez de listas.
- Para cambiar el plan se edita el JSON de la raíz; la app lo toma en la próxima build.

## Cálculo de correlativas (`data/Calculo.kt`)

- Estados por materia: `SIN_CURSAR`, `REGULARIZADA`, `APROBADA`.
- Cursar: las de `cursar.regularizadas` tienen que estar regularizadas o aprobadas; las de
  `cursar.aprobadas`, aprobadas.
- Rendir: tener la materia regularizada y cumplir `rendir` (en obligatorias, todas aprobadas).
- Proyecto Final se rinde con todas las demás obligatorias aprobadas y 10 hs anuales de electivas
  aprobadas. Una electiva con `horas_anuales` null no suma.
- PPS: se empieza con los mismos requisitos que para cursar Proyecto Final; no se rinde.
- `Avance.estadoNodo()` (en Juego.kt) traduce eso a BLOQUEADA / DISPONIBLE / REGULARIZADA /
  PARA_RENDIR / APROBADA para la UI.

## Camino crítico (`data/CaminoCritico.kt`)

Solo lee las correlativas; no modifica ninguna regla de `Avance`.

- `GrafoPlan`: nodos = obligatorias + PPS (las electivas no traban nada).
  - Para "trabar": B depende de A si A está en sus correlativas para cursar o rendir; Proyecto
    Final depende además de todas las obligatorias.
  - Para el tiempo: solo las correlativas para cursar.
- `CaminoCritico(avance)`:
  - `traba[id]`: materias no aprobadas que dependen (directa o indirectamente) de cada pendiente.
  - `anios`: cadena más larga de materias **sin cursar** por correlativas para cursar; cada paso
    es una cursada. Las regularizadas ya no suman cursadas.
  - `camino`: una cadena de ese largo; desempata prefiriendo la que llega a Proyecto Final y la
    que pasa por las materias que más traban. `holgura(id)`: margen en cursadas (0 = crítica).
  - `cuellos`: las 5 pendientes que más traban (con empates), si traban al menos 4.
  - `prioridades()`: las que se pueden cursar o rendir ya, por `traba` y luego camino crítico.
- El ViewModel lo recalcula en cada cambio de avance; la UI lo lee por `LocalCritico`.

## Disposición adaptativa (`ui/App.kt`)

- Ancho compacto (celular vertical): barra inferior, detalle en hoja modal.
- Ancho medio o expandido (tablet, horizontal): `NavigationRail`, lista y detalle lado a lado,
  mapa con panel de detalle al costado, inicio en 2–3 columnas (`LazyVerticalStaggeredGrid`).
- El estado de navegación (pestaña, materia abierta, filtro, scrolls, zoom del mapa) se guarda con
  `rememberSaveable` arriba de todo, antes de elegir la disposición, para sobrevivir a la rotación.

## Compilar y probar

```
./gradlew testDebugUnitTest assembleDebug
```

Necesita JDK 17+ y el SDK de Android (`ANDROID_HOME` o `local.properties`, que no se versiona).
El APK queda en `app/build/outputs/apk/debug/app-debug.apk`. CI (`.github/workflows/android.yml`)
corre lo mismo en cada PR y publica el APK en Releases en cada push a `main`.

## Reglas

- **No cambiar cómo se guarda el progreso**: DataStore `progreso`, clave `estados`, formato de
  `CodecEstados` (`id:ESTADO` separados por coma). Cambiarlo borraría el avance de quien actualice.
- No cambiar la lógica de correlativas de `Calculo.kt` sin que lo pidan; funciones nuevas van
  aparte y solo leen el plan y el avance.
- No cambiar `applicationId` ni `app/debug.keystore`: la app nueva tiene que instalarse encima.
- Respetar los dos temas: colores siempre desde `C` (Tema.kt), nunca fijos en las pantallas.
- Agregar tests en `app/src/test` para cualquier cálculo nuevo.
