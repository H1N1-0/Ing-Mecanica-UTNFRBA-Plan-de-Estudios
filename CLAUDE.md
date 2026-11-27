# CLAUDE.md

Correlativas de Ingeniería Mecánica (UTN FRBA, plan 2023): los datos del plan en JSON y una app
Android (Kotlin + Jetpack Compose) gamificada, con widget, para marcar materias y ver qué se puede
cursar y rendir. Todo el código, los textos y los commits están en español rioplatense.

## Cómo trabajar

- Responder siempre en español.
- No usar el emulador en cada cambio: alcanza con los tests unitarios y compilar
  (`./gradlew testDebugUnitTest :app:assembleRelease`). Usarlo solo si el usuario lo pide o si
  el cambio toca el Baseline Profile.

## Estructura

```
correlativas.json        Plan de estudios y correlativas (fuente de verdad)
perfiles.json            Perfiles del test vocacional «¿Qué ingeniero mecánico sos?»
frases.json              Frases motivacionales
*.mhtml.mht              Versión web original de las correlativas
app/
  build.gradle.kts       Copia los tres JSON a los assets en cada build (tarea copiarDatos)
  debug.keystore         Clave versionada: firma debug Y release, todas se instalan encima
  proguard-rules.pro     Reglas de R8 para la release
  estabilidad_compose.conf  Clases que Compose trata como estables (data/ es inmutable)
  src/main/res/raw/keep.xml  Recursos pedidos por nombre (getIdentifier) que el shrinker no ve
  src/release/generated/baselineProfiles/  Baseline Profile generado (se versiona)
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
      Tema.kt            Tokens de los 3 estilos × claro/oscuro (objeto C): paletas, fuentes, formas, movimiento
      Estilizados.kt     Componentes que cambian por estilo: Fondo, Medidor/Manómetro, Medallón, marcas de plano
      Dibujos.kt         Dibujos técnicos del Plano técnico (engranajes, pistón, bomba, turbina, rodamiento, resorte)
    widget/              Widget de Glance
  src/main/res/font/     Chakra Petch (Gamificado), Barlow Semi Condensed y Share Tech Mono (Plano técnico)
  licencias/             Licencias OFL de las tipografías
  src/test/              Tests JVM (JUnit) que leen el correlativas.json real
baselineprofile/         Módulo de test: genera el Baseline Profile y mide el arranque (necesita dispositivo)
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

## Estilos visuales (`ui/Tema.kt`)

Tres estilos (`data.Estilo`: PLANO por defecto, GAMIFICADO, MATERIAL) combinables con el tema
(`ModoTema`: sistema, claro, oscuro). Se guardan por nombre en el DataStore `ajustes` (clave
`estilo`), junto con `nombre` y `nombre_preguntado` para el rótulo; el progreso no se toca. El cambio es instantáneo:
`TemaJuego` actualiza `C.tema` y todo lo que lee `C` se redibuja. El widget usa las mismas paletas.

- **Tokens** (`TemaVisual`): `paleta` (colores), `fuente` y `fuenteDatos`, `formas`, `movimiento`
  (duraciones, rebote y tipo de festejo), `indicador` (anillo, anillo tonal o manómetro) y `brillo`.
- **Componentes base**, los únicos que miran el estilo: `Tarjeta`, `Nodo`, `Barra`, `Pildora`
  (Componentes.kt), `Fondo`, `Medidor`, `Medallon`, `EngranajesGirando` (Estilizados.kt). Las
  pantallas solo usan estos componentes y `C.*`: una pantalla nueva se adapta sola a los tres.
- **Plano técnico**: cuadrícula y un dibujo por pantalla (`Pestania.dibujo`; Ajustes = rodamiento,
  test = resorte), cotas y marcas de esquina en las tarjetas (`Tarjeta(cota = ...)`), rótulo de
  lámina y manómetro en el inicio, engranajes al desbloquear.
- **Material You**: `dynamicLight/DarkColorScheme` en Android 12+, esquema fijo antes. Los colores de
  estado se armonizan con el primario.
- **Frases**: cada una tiene `area` en frases.json (`data.Area`); el medallón usa iniciales, color
  (`colorArea`) e ícono (`iconoArea`) del área.

## Disposición adaptativa (`ui/App.kt`)

- Ancho compacto (celular vertical): barra inferior, detalle en hoja modal.
- Ancho medio o expandido (tablet, horizontal): `NavigationRail`, lista y detalle lado a lado,
  mapa con panel de detalle al costado, inicio en 2–3 columnas (`LazyVerticalStaggeredGrid`).
- El estado de navegación (pestaña, materia abierta, filtro, scrolls, zoom del mapa) se guarda con
  `rememberSaveable` arriba de todo, antes de elegir la disposición, para sobrevivir a la rotación.

## Compilar y probar

```
./gradlew testDebugUnitTest :app:assembleRelease   # lo que corre CI
./gradlew assembleDebug                            # build de desarrollo, sin R8
```

Necesita JDK 17+ y el SDK de Android (`ANDROID_HOME` o `local.properties`, que no se versiona).
La release (`app/build/outputs/apk/release/app-release.apk`) va con R8 (`isMinifyEnabled` y
`isShrinkResources`), el Baseline Profile y la firma de `app/debug.keystore`. CI
(`.github/workflows/android.yml`) corre tests y la release en cada PR, y publica el APK release en
Releases en cada push a `main`.

### Baseline Profile

Con un celular o emulador conectado (Android 9+; en emulador conviene una imagen con `adb root`):

```
./gradlew :app:generateReleaseBaselineProfile                # regenera el perfil
./gradlew :baselineprofile:connectedBenchmarkReleaseAndroidTest  # compara arranque con y sin perfil
```

El recorrido está en `baselineprofile/.../GeneradorBaselineProfile.kt`. Regeneralo cuando cambien
mucho las pantallas; si el texto de una pestaña cambia, actualizá el recorrido.

## Rendimiento

- Lo que cambia cada frame (pan/zoom del mapa, pulso de los nodos, animaciones) se lee en
  `graphicsLayer` o dentro de `Canvas`, nunca en la composición.
- Las correlativas no se recalculan en la UI: `Avance` y `CaminoCritico` se crean una vez por cambio
  de progreso (en `Dispatchers.Default`) y los derivados caros van en `remember(avance)`.
- Las listas perezosas llevan `key` estable (id de materia/logro) y `contentType`.
- Fondos y decoraciones de los estilos: se arman en `drawWithCache` (una vez por tamaño) y el fondo
  va en su propia capa (`graphicsLayer`), así scrollear o mover el mapa no lo redibuja.

## Reglas

- **Nada de datos personales en el repo**: ni nombres ni mails reales en código, tests, capturas
  versionadas o commits. El nombre del rótulo lo carga cada usuario: se pregunta una vez en el
  primer uso (`PreguntaNombre`, opcional), se cambia en Ajustes y se guarda solo en el DataStore
  `ajustes`. Sin nombre, el rótulo dice "Estudiante" (`nombreParaRotulo`). En tests y capturas
  usar datos de ejemplo genéricos.
- **No cambiar cómo se guarda el progreso**: DataStore `progreso`, clave `estados`, formato de
  `CodecEstados` (`id:ESTADO` separados por coma). Cambiarlo borraría el avance de quien actualice.
- No cambiar la lógica de correlativas de `Calculo.kt` sin que lo pidan; funciones nuevas van
  aparte y solo leen el plan y el avance.
- No cambiar `applicationId` ni `app/debug.keystore`, ni la firma de la release: la app nueva
  tiene que instalarse encima de la que ya tiene el usuario.
- R8: si algo se lee por nombre o reflexión (recursos con `getIdentifier`, enums guardados por
  nombre), agregá la regla en `proguard-rules.pro` o `res/raw/keep.xml` y probá la release.
- Respetar los tres estilos y los dos modos: colores, fuentes y formas siempre desde `C` (Tema.kt),
  nunca fijos en las pantallas. Si algo tiene que verse distinto según el estilo, va en un
  componente base o en un token nuevo, no en un `if (estilo)` dentro de una pantalla.
- Capturas: con Robolectric (Roborazzi) agregado solo en local, sin commitear. Para Material You
  usar `@Config(sdk = [33])`: en 34+ Robolectric no emula bien los colores dinámicos.
- Agregar tests en `app/src/test` para cualquier cálculo nuevo.
