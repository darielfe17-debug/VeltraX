# Pruebas de VeltraX Nativo 2.0

Comprobaciones realizadas en el entorno de generación:

- `kotlinc` local compila `core/src/main/kotlin/com/veltrax/core/DirectAudioDownloader.kt` (advertencias por constructor `URL(String)` de Java, sin errores).
- Prueba con servidor HTTP local que soporta HTTP `Range` y un archivo simulado de 3,100,010 bytes: se completó una descarga dividida en 3 partes; SHA-256 final idéntico al original.
- Prueba de pausa al sobrepasar un umbral de bytes seguida de reanudación con los segmentos persistidos: estado `PAUSED` y longitud/copia correctas, SHA-256 idéntico.
- `DesktopModels.kt` compilado con stubs de estado Compose en un entorno de verificación sintáctica; esto NO equivale a compilar la app Windows.

**No comprobado / no terminado:**

- No se compiló el APK Android ni el MSI Windows. En este entorno faltan Android SDK y Gradle y la red de contenedor no permite descargarlos.
- No se realizó una prueba de ejecución de Jetpack Compose, Media3, WorkManager o JavaFX en dispositivo real.
- Las tareas de GitHub Actions entregadas para la compilación son una propuesta reproducible, pero tampoco se han ejecutado. Es posible que se requieran ajustes a dependencias, empaque o interfaces de plataformas.

Para validar la app real, compilar los dos artefactos con `.github/workflows/` y probar los permisos de MediaStore, reproducción en background, multimedia widget, y cancelación de descargas con varios servidores (sin rangos, HTTP redirects y archivos truncados).
