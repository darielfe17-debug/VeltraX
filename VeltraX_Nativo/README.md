# VeltraX Nativo 2.0 — Android + Windows

Aplicación de música **local** basada en las pantallas aportadas. Esta entrega reemplaza la anterior WebView. Android usa **Kotlin + Jetpack Compose + Material 3, MediaStore, Media3 MediaSessionService, WorkManager**. Windows usa **Compose Desktop + JavaFX Media**. Comparten el motor HTTP de descargas segmentadas en el módulo `:core`.

## Funciones
- Índice real de canciones locales. Android consulta MediaStore con autorización `READ_MEDIA_AUDIO` (Android 13+) o `READ_EXTERNAL_STORAGE` (Android 10–12L). Windows indexa carpetas elegidas explícitamente y `Music/VeltraX`.
- Controles reproducir / pausa, anterior / siguiente, progreso, playlists y favoritos. Android sigue reproduciendo desde el servicio de Media3, muestra notificación multimedia y trae un widget para la pantalla de inicio. Windows tiene un reproductor flotante **dentro del sistema de ventanas**, siempre visible, y reproduce mediante JavaFX.
- Descargas HTTP/HTTPS de archivos de audio directos: máximo 3 segmentos por archivo cuando hay servidor con `Range` y tamaño conocido, fallback a conexión normal en servidores sin Range. Pausa/reanuda almacenando segmentos de transferencia en disco. Android administra trabajos mediante WorkManager y copia los archivos completos a `Music/VeltraX` vía MediaStore. Windows permite 3 trabajos en paralelo y conserva cola pendiente.
- Interfaz OSCURA morada. No hay canción de demostración que simule audio disponible.

## Limitaciones de fuentes
- No extrae audio de webs de vídeos, Spotify, YouTube ni proveedores protegidos; no evita DRM. Copia solo URLs que devuelven archivos de audio de forma autorizada. Se recomienda HTTPS: HTTP sin cifrar se admite como opción de compatibilidad y puede exponer el contenido de la transferencia a terceros en la red. URLs que devuelven HTML/JSON o no tienen una cabecera reconocible de audio se rechazan; el servidor puede cortar una descarga o impedir rangos. Una URL HLS/MPD, token caducado o enlace con login no es un archivo de audio directo.
- El motor asume que el archivo directo se corresponde con el nombre/extensión facilitados. La decodificación depende de codecs disponibles: Android Media3 y JavaFX de Windows no garantizan soporte para todos los contenedores; **MP3 y WAV** son las opciones de mayor compatibilidad; M4A/AAC funcionan en muchos entornos. Las descargas no comprueban licencias de usuario, por lo que descarga únicamente contenido que tengas derecho a obtener.
- MediaStore no muestra archivos del sistema a los que Android no da acceso. Windows muestra nombres basados en el nombre de archivo, no analiza etiquetas ID3. Descargas pausadas en progreso pueden tardar en finalizar la operación de cancelación de una conexión de red.

## Compilar Android
1. Abre el directorio raíz en Android Studio con JDK 17. Instala Android SDK Platform 35.
2. Gradle: Kotlin 2.2.20, Android Gradle Plugin 8.11.1, Gradle 8.14.3. El IDE gestiona la descarga de dependencias de Maven.
3. Ejecuta la tarea `:androidApp:assembleDebug`. APK en `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
4. Instala el APK en un dispositivo Android 10 o posterior. Concede acceso a audio cuando se solicite. Para notificaciones de descargas permite notificaciones si Android lo requiere.

**GitHub Actions (para crear APK sin Android Studio)**: crea un repositorio privado, sube **el contenido** de este ZIP y activa Actions. Corre `VeltraX Android APK` desde Actions > Run workflow. El artefacto de la ejecución contiene el `.apk`. El repositorio necesita permitir Actions. No es necesario añadir una cuenta ni credenciales dentro del proyecto.

## Compilar Windows
1. Usa JDK 17 en una computadora Windows. Abre proyecto en IntelliJ IDEA/Android Studio con Compose Desktop.
2. Ejecuta `:desktopApp:run` para probar, o `:desktopApp:packageMsi` para crear el instalador MSI.
3. También puedes usar `.github/workflows/windows-msi.yml` en GitHub Actions (Windows). Windows utiliza JavaFX Media 21.0.2 y necesita los códecs compatibles del sistema. Carpeta local indexada solo si la eliges.

## Observaciones sobre el entregable
**No contiene APK ni MSI precompilados**. Este entorno carece de Android SDK/Gradle y no permite obtener dependencias en red; se ha comprobado la compilación Kotlin del motor compartido y se incluyen flujos de compilación CI, pero **no se ha ejecutado la compilación Android/Windows final**. Los workflows se entregan para ejecutar la compilación; puede ser necesario corregir dependencias o errores de plataforma al probarlos.

El mini-reproductor Android es interno a la aplicación y el widget es de pantalla de inicio; **no se solicita permiso de superposición sobre todas las apps**. No hay backend social, cuentas online ni catálogos musicales ajenos porque el alcance actual es solo música local.

### Ejecutar sin el wrapper de Gradle
Este ZIP **no incluye el ejecutable `gradlew` ni `gradle-wrapper.jar`**, porque las distribuciones no están disponibles en este entorno. Usa Gradle 8.14.3 instalado localmente o los workflows de GitHub Actions (`gradle :androidApp:assembleDebug` / `gradle :desktopApp:packageMsi`). La propiedad `distributionUrl` se incluye como referencia de versión.
