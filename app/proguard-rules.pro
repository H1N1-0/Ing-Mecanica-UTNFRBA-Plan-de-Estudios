# Reglas de R8 para la variante release.
#
# La app no usa reflexión propia: los JSON se leen con org.json (del sistema) campo por campo,
# el ViewModel lo crea la fábrica de AndroidX (que trae sus propias reglas) y el widget está
# declarado en el manifiesto. Lo único que conviene fijar es lo que se guarda en disco por nombre.

# El progreso se guarda como "id:ESTADO" y el tema como su nombre (CodecEstados, Ajustes).
# R8 conserva Enum.name() igual, pero fijamos estos enums para que una optimización futura
# nunca pueda cambiar lo que se lee y escribe en DataStore.
-keep enum ar.correlativas.mecanica.data.Estado { *; }
-keep enum ar.correlativas.mecanica.data.ModoTema { *; }

# Stack traces legibles en los reportes (el mapping queda en app/build/outputs/mapping/release).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# El estilo visual también se guarda por nombre (Ajustes).
-keep enum ar.correlativas.mecanica.data.Estilo { *; }
