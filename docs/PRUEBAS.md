# Verificación de GPS Mocker 0.4.0

## Automatización

JDK 21, Android SDK 36, Build Tools 36.0.0; bytecode Java/Kotlin 17. Gradle 8.11.1, AGP 8.10.1, Kotlin 2.2.21. Sin migración a Compose, Hilt o bases de datos adicionales.

```bash
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease bundleRelease
python -m unittest discover -s tools -p 'test_*.py'
npm ci --prefix routing-worker --ignore-scripts
npm --prefix routing-worker test
```

Las pruebas Android usan JUnit/Robolectric. Incluyen controles en API 26/35/36; permisos, ubicaciones reales/simuladas, cámara inicial, biblioteca, persistencia, respaldo, corrupción y fallo de escritura, geometría/pausa/bucle, GPX, selección de perfiles y restauración. Los casos nativos del mapa bloquean descargas: verifican controles y puente, no WebGL ni disponibilidad cartográfica.

Informes: `app/build/reports/tests/testDebugUnitTest/`, `app/build/reports/lint-results-*.html`. Previews: `app/build/outputs/previews/`. Arte gráfico de tienda: `app/build/outputs/store/` (icono 512 y gráfico 1024×500); estas imágenes se generan desde los vectores propios de la app, no sustituyen capturas reales con mapa.

La prueba de JSON extraído por USB solo se ejecuta cuando se pasa `-PlegacyBackup=/ruta/archivo.json`. El fixture de Python también se ha comprobado con el codec Android para evitar formatos incompatibles.

El servicio de rutas se prueba con Node y Miniflare: validación, cuotas, fallos de proveedor, separación de cabeceras, respuestas acotadas, SQLite bajo reservas concurrentes y persistencia tras reiniciar. `wrangler deploy --dry-run` prepara el bundle sin desplegar ni consumir solicitudes reales.

La integración de firma utiliza claves efímeras **de prueba**, comprueba certificados distintos para APK/AAB y rechazo de una huella incorrecta. Para ejecutarla, después de generar release, usa JDK/keytool, `ANDROID_HOME` y:

```bash
GPS_TEST_SIGNING=true python -m unittest discover -s tools -p 'test_sign_release.py'
```

En PowerShell define primero `$env:GPS_TEST_SIGNING='true'`. Las claves de producción y una actualización real no se simulan mediante este test.

## Verificación física pendiente

No había dispositivos conectados por ADB al implementar. Completa y registra esta tabla en tu teléfono y, antes de Play, en un segundo fabricante mediante los testers. Emuladores y Robolectric no se registran como teléfono físico.

| Escenario | Resultado / evidencia |
| --- | --- |
| Modelo, fabricante, Android, WebView, versión APK y SHA-256 | Pendiente |
| Inicio con permiso preciso, aproximado, denegado y denegación permanente | Pendiente |
| GPS desactivado, sin señal, reintento y posición real reciente | Pendiente |
| Selección manual no se sobrescribe por una respuesta atrasada | Pendiente |
| Simulación activa no se presenta como posición real | Pendiente |
| Comenzar, actualizar, pausar, reanudar, terminar y detener | Pendiente |
| GPS vuelve a funcionar en otra app después de Detener | Pendiente |
| Pantalla apagada, cambio de app, cierre del proceso y limpieza pendiente | Pendiente |
| Revocar app simulada, recuperar selección y reintentar limpieza | Pendiente |
| Exportar → instalación limpia → restaurar; nombres y conteos completos | Pendiente |
| Actualizar dos versiones estables conservando todos los datos | Pendiente |
| Tres perfiles viales, cruces/sentidos, circuito, sin conexión y cuota agotada | Pendiente de endpoint |
| Banner de prueba, UMP y opciones de privacidad; sin bloquear mapa | Pendiente de configuración |
| Fuente 1,5×, TalkBack, rotación, teclado, barras e insets | Pendiente |

## Rendimiento: sesiones comparables de 30 minutos

Registra por separado mapa visible, simulación con pantalla apagada, ruta activa y ruta pausada. Usa el mismo dispositivo, intervalo, temperatura y red para comparar versiones. Captura arranque, CPU, memoria, batería y bloqueos de CPU con Android Studio Profiler/Perfetto; los tiempos de Robolectric no son mediciones del teléfono.

Comandos de apoyo, ajustando el paquete si pruebas la versión estable:

```bash
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell dumpsys webviewupdate
adb shell dumpsys meminfo com.diegoca.gpsmocker.debug
adb shell dumpsys power
```

Conserva trazas y mediciones antes/después sin publicar ubicaciones ni respaldos personales. Verifica que no queden proveedor simulado ni wake lock propios después de Detener y que no exista crecimiento sostenido de memoria, cierre inesperado o ANR. Registra incidencias con pasos reproducibles. La skill `android-profiler` queda preparada para analizar trazas cuando haya dispositivo; no se inventaron cifras de batería ni rendimiento.
