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
      Ajustes.kt         Tema, estilo, nombre y resultado del test (DataStore aparte, "ajustes")
      Notas.kt           Notas de finales y aplazos (DataStore "notas", versionado) y Estadisticas
      Backup.kt          Copia de seguridad: JSON del archivo y texto comprimido del QR, con validación
      CopiaAutomatica.kt Copia automática (almacenamiento interno) antes de importar la historia del SIU
      LectorPdf.kt       Lector de PDF propio (Inflater, textos con posición), sin librerías
      HistoriaSiu.kt     Tabla de la historia académica del SIU y mapeo a estados (ImportacionSiu)
      Qr.kt              Generar y leer QR con ZXing (sin red)
      Juego.kt           EstadoNodo, porcentaje, rangos, logros y layout del mapa (Mapa)
      CaminoCritico.kt   Grafo de dependencias, materias que traba cada una, camino crítico
      Contenido.kt       Frases y test vocacional
      Busqueda.kt        normalizar() y Materia.coincide(): buscar por sigla, nombre, número o código SIU
    ui/
      App.kt             Navegación y disposición adaptativa (celular / tablet / horizontal)
      CorrelativasViewModel.kt  Flujos de avance, camino crítico, preferencias y desbloqueos
      PantallaInicio.kt, PantallaMapa.kt, PantallaLista.kt, PantallaLogros.kt, ...
      HojaDetalle.kt     ContenidoDetalle (hoja en celular, panel lateral en tablet)
      NombreMateria.kt   NombreOAbrev: nombre completo si entra, si no la sigla (vistas compactas)
      CaminoCriticoUi.kt Tarjetas "Camino crítico" y "Prioridad", bloque del detalle
      NotasUi.kt         Diálogos de nota y aplazo, bloque "Final" del detalle (LocalNotas)
      PantallaEstadisticas.kt  Promedios, finales, gráfico de evolución por año, mejor y peor nota
      CopiaUi.kt, EscanerQr.kt  Exportar/importar en Ajustes (archivo, QR, cámara con CameraX, imagen)
      ImportarSiuUi.kt   Importar la historia del SIU en Ajustes: selector de PDF, vista previa, deshacer
      Tema.kt            Tokens de los 3 estilos × claro/oscuro (objeto C): paletas, fuentes, formas, movimiento
      Estilizados.kt     Componentes que cambian por estilo: Fondo, Medidor/Manómetro, Medallón, marcas de plano
      Dibujos.kt         Dibujos técnicos del Plano técnico (engranajes, pistón, bomba, turbina, rodamiento, resorte)
    widget/              Widget de Glance
  src/main/res/font/     Chakra Petch (Gamificado), Barlow Semi Condensed y Share Tech Mono (Plano técnico)
  licencias/             Licencias OFL de las tipografías
  src/test/              Tests JVM (JUnit) que leen el correlativas.json real, y tests de interfaz
                         con Robolectric + Compose UI Test (ui/AjustesNavegacionTest, ui/AtrasTest)
baselineprofile/         Módulo de test: genera el Baseline Profile y mide el arranque (necesita dispositivo)
```

## Datos

- `correlativas.json` tiene `obligatorias` (id 1–40, nivel 1–5), `electivas` (id ≥ 101, con
  `horas_anuales`) y `pps` (id 41). Cada materia tiene `cursar` y `rendir`, con listas de ids
  `regularizadas` y `aprobadas`. Proyecto Final (id 40) tiene `rendir.regla` en vez de listas.
- Toda materia (obligatorias, PPS y electivas) tiene `codigo_siu` (string) y `abrev` (sigla de hasta
  6 caracteres: AM1, IyS, PF). Ambos son únicos (la sigla sin distinguir mayúsculas);
  `AbreviaturasTest` falla si falta alguno o se repite. Si agregás una materia, poné los dos.
- Para cambiar el plan se edita el JSON de la raíz; la app lo toma en la próxima build.

## Cálculo de correlativas (`data/Calculo.kt`)

- Estados por materia: `SIN_CURSAR`, `CURSANDO`, `REGULARIZADA`, `APROBADA`, `PROMOCIONADA`
  (aprobada sin final). Para correlativas, horas de electivas, avance y logros, Promocionada cuenta
  igual que Aprobada: compará con `estado.aprobada` (y `EstadoNodo.completada`), nunca con
  `== APROBADA`, salvo que importe cómo se aprobó (por ejemplo, el logro "Primer final" pide un final).
- Cursando cuenta como sin cursar para las correlativas y el camino crítico (no habilita nada), pero
  ya no se ofrece para cursar. "Regularizada o más" es `estado.regularizada`: nunca `!= SIN_CURSAR`.
- Cursar: las de `cursar.regularizadas` tienen que estar regularizadas o aprobadas; las de
  `cursar.aprobadas`, aprobadas.
- Rendir: tener la materia regularizada y cumplir `rendir` (en obligatorias, todas aprobadas).
- Proyecto Final se rinde con todas las demás obligatorias aprobadas y 10 hs anuales de electivas
  aprobadas. Una electiva con `horas_anuales` null no suma.
- PPS: se empieza con los mismos requisitos que para cursar Proyecto Final; no se rinde.
- `Avance.estadoNodo()` (en Juego.kt) traduce eso a BLOQUEADA / DISPONIBLE / REGULARIZADA /
  PARA_RENDIR / APROBADA / PROMOCIONADA / CURSANDO para la UI (Cursando: `C.violeta`, `ic_libro`). Promocionada usa el color de Aprobada (`C.oro`)
  y se distingue por el ícono (`ic_promocion`, estrella en un aro) y un segundo aro en el `Nodo`.

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

## Notas y estadísticas (`data/Notas.kt`)

- Promocionar pide la nota ANTES de cambiar el estado (`pedirPromocion` en el ViewModel): sin nota
  no se confirma y cancelar deja la materia como estaba. La nota va en `finales`, como la de un final.
- Al aprobar una materia (no la PPS) se ofrece cargar nota (1–10) y fecha, después de la
  celebración; también desde el detalle ("Final"), junto con los aplazos (nota 1–5, fecha). Todo
  es opcional: sin nota, la materia cuenta igual como aprobada. Las notas NO tocan las correlativas.
- Se guardan en el DataStore `notas` (clave `libreta`, JSON de `CodecLibreta` con `version`),
  aparte del progreso. Si cambia el formato, subí `CodecLibreta.VERSION` y migrá en `deJson`.
- `Estadisticas(avance, libreta)`: solo cuentan las notas de materias hoy aprobadas (por final o
  promoción: `porFinal` / `porPromocion`); promedio sin y con aplazos, finales rendidos (aprobadas
  por final + aplazos; las promociones no son finales), mejor/peor, evolución acumulada por año (las
  notas sin fecha cuentan en el promedio pero no en el gráfico).
- "Reiniciar mi progreso" borra también las notas.

## Siglas (`abrev`)

- Donde el nombre completo no entra se usa `Materia.abrev`: etiquetas de los nodos del mapa, eslabones
  del camino crítico (tarjeta y banner del mapa) y el widget angosto (`textoMateria`). En tarjetas
  compactas (inicio, prioridad, estadísticas) `NombreOAbrev` mide y elige. Con la sigla visible, el
  `contentDescription` lleva el nombre completo para TalkBack.
- El detalle muestra el nombre completo con la sigla al lado; la lista y las correlativas del detalle
  muestran la sigla en la línea de abajo. El buscador encuentra por sigla, nombre, número o código SIU.

## Copia de seguridad (`data/Backup.kt`)

- JSON con `formato`, `version`, `exportado`, `progreso` (el MISMO texto de `CodecEstados`), `notas`,
  `test` y `ajustes`. Exportar/importar archivo con el selector del sistema (sin permisos de
  almacenamiento). QR: `CMEC1:` + JSON comprimido (deflate) en Base64 URL.
- Importar valida (formato, versión, materias que existen, rangos de notas), muestra un resumen y
  pide confirmación antes de reemplazar. Si cambia el formato, subí `Backup.VERSION` (y el prefijo
  del QR), migrá en `Backup.migrar` y seguí leyendo las versiones viejas. Versión 2 (`CMEC2:`) suma
  PROMOCIONADA y la 3 (`CMEC3:`), CURSANDO; las anteriores se siguen importando. `PromocionTest`
  tiene una copia v1 literal.
- La app NO tiene permiso de internet: el manifiesto lo quita con `tools:node="remove"` (hay un test).
  La cámara se pide solo al escanear.

## Historia académica del SIU (`data/LectorPdf.kt`, `data/HistoriaSiu.kt`)

- En Ajustes, "Importar PDF del SIU": el PDF se elige con el selector del sistema (`OpenDocument`,
  sin permisos) y se lee en el celular. Sin internet y sin librerías: `LectorPdf` descomprime los
  streams FlateDecode con `Inflater` e interpreta los operadores de texto (el SIU usa Courier con
  WinAnsiEncoding). Si un PDF trae fuentes con codificación propia, no se lee: se muestra el error.
- `HistoriaSiu.filas`: columnas por la x de los encabezados de cada página; une los nombres de dos
  líneas (el código entre paréntesis marca el final del nombre). Si no hay encabezado, error y no
  cambia nada.
- `ImportacionSiu`: busca por `codigo_siu` y si no por nombre normalizado. Regularidad aprobada →
  Regularizada (su nota no se guarda: no es un final); Examen aprobado → Aprobada con nota; Examen
  reprobado → aplazo (nota 1–5, sin duplicar); Promoción → Promocionada con nota; En curso →
  Cursando; Equivalencia → Aprobada. Con varias filas gana la de mayor `rango`.
- Vista previa (`VistaPreviaSiu`): actual → nuevo y nota; las bajas van destacadas y desmarcadas; las
  filas no reconocidas, aparte. Aplicar guarda antes una copia automática (`CopiaAutomatica`) que se
  restaura desde Ajustes ("Deshacer la última importación").
- Tests con un PDF inventado que arma `PdfDePrueba` (nunca un PDF real: tiene datos personales).

## Disposición adaptativa (`ui/App.kt`)

- Ancho compacto (celular vertical): barra inferior, detalle en hoja modal.
- Ancho medio o expandido (tablet, horizontal): `NavigationRail`, lista y detalle lado a lado,
  mapa con panel de detalle al costado, inicio en 2–3 columnas (`LazyVerticalStaggeredGrid`).
- **Un solo botón de Ajustes** (`BotonAjustes`): en el celular vertical, arriba a la derecha del
  inicio; con barra lateral, arriba de la barra (el inicio no muestra el suyo: `botonAjustes`).
  Ajustes es la misma pantalla en todas las disposiciones; `AjustesNavegacionTest` lo verifica.
- **Atrás** (botón, gesto y atrás predictivo de Android 14+, `enableOnBackInvokedCallback`), en
  orden: diálogos y hoja de detalle se cierran solos; Ajustes/Estadísticas/Test/Perfil tienen su
  `BackHandler`; el de `AppCorrelativas` cierra el panel de detalle (tablet) o vuelve a Inicio. En
  Inicio sin nada abierto no hay manejador activo: el sistema cierra la app. Si agregás una pantalla
  superpuesta o un panel, que "atrás" lo cierre. `AtrasTest` lo verifica en celular y tablet.
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
- **Sin internet**: no agregar el permiso ni librerías que manden datos afuera; todo es local.
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
- Tests de interfaz: Robolectric + Compose UI Test (están en el repo y corren en CI). No usar
  `mainClock.autoAdvance = false` con la app entera: las animaciones infinitas no la dejan quedar
  "idle" y cada acción espera 60 s. Si una pantalla nueva tiene opciones, agregalas a la lista
  de `AjustesNavegacionTest` (si es Ajustes) o a un test equivalente.
- Capturas: con Roborazzi agregado solo en local, sin commitear. Para Material You usar
  `@Config(sdk = [33])`: en 34+ Robolectric no emula bien los colores dinámicos.
- Agregar tests en `app/src/test` para cualquier cálculo nuevo.
