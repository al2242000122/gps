# GPS Mocker Android

Aplicación Android en Kotlin para seleccionar un punto en un mapa y simular su ubicación durante pruebas de desarrollo. No requiere root ni una clave de Google Maps.

## Requisitos

- Android Studio con JDK 17 o superior.
- Android SDK Platform 35 y Build Tools 35.0.0.
- Un dispositivo o emulador con Android 8.0 o superior.

## Instalar desde el celular con GitHub Actions

1. Sube estos archivos a tu repositorio de GitHub, incluida la carpeta `.github/workflows`.
2. En GitHub abre **Actions → Compilar APK Android**. Habilita Actions si GitHub lo solicita.
3. Cada push a `main` o a una rama `feat/…` ejecuta las pruebas, lint y la compilación. También puedes usar **Run workflow** en la rama que contenga el workflow; para verlo en la lista, el archivo debe existir en la rama predeterminada.
4. Abre una ejecución que termine en verde. En **Artifacts**, descarga `gps-mocker-debug-N` (necesitas iniciar sesión en GitHub).
5. Extrae el ZIP en tu teléfono e instala `app-debug.apk`. Si Android lo solicita, permite instalar aplicaciones desde el navegador o gestor de archivos que estés usando.

El APK es de prueba, no una versión firmada para Google Play. Distintas computadoras o ejecuciones pueden usar certificados de depuración diferentes: si Android rechaza una actualización por firma incompatible, desinstala la versión anterior y vuelve a instalar; se perderán sus datos locales. Los artefactos de APK se conservan durante 14 días.

## Primer uso

1. Activa las opciones de desarrollador del teléfono (normalmente pulsando siete veces **Número de compilación** en los ajustes del dispositivo).
2. En **Seleccionar aplicación de ubicación simulada**, elige **GPS Mocker**. Hay un acceso directo dentro de la app.
3. Activa la ubicación del teléfono. Toca el mapa para colocar el marcador o mantenlo pulsado para arrastrarlo. También puedes escribir latitud y longitud; se aceptan punto y coma decimal.
4. Pulsa **Iniciar / actualizar ubicación** y concede el permiso de ubicación. La ubicación aproximada también es suficiente para el servicio. En Android 13 o superior puedes permitir notificaciones para tener el botón de detener visible.
5. La posición se vuelve a publicar cada segundo. Al mover el marcador, pulsa otra vez **Iniciar / actualizar ubicación** para aplicar el nuevo destino.
6. Para finalizar, pulsa **Detener simulación** en la app o en su notificación.

El servicio mantiene la simulación al cambiar de aplicación. Se utiliza un bloqueo de CPU con vencimiento corto, renovado mientras la simulación está activa, para continuar las actualizaciones con la pantalla apagada. Android y las restricciones de batería del fabricante pueden interrumpir un servicio; la app no promete ejecución indefinida. Si el proceso se interrumpe, la siguiente apertura ofrece restaurar el proveedor GPS. No se reinicia automáticamente una simulación después de que Android mate el proceso.

Si se revoca la selección como app de ubicación simulada antes de detenerla, la restauración puede fallar. La app muestra el error, permite reintentar y no anuncia falsamente que el GPS se restauró. Vuelve a seleccionar GPS Mocker y pulsa Detener.

## Alcance de la versión 0.2

- Mapa OpenStreetMap con osmdroid, zoom, selección por toque y marcador arrastrable.
- Entrada manual validada, con persistencia de la última selección y recuperación de la vista al girar la pantalla.
- Simulación continua del proveedor GPS mediante `LocationManager` y servicio en primer plano de tipo `location`.
- Notificación persistente con acción para detener y restauración explícita del proveedor real.
- Comprobación de selección como app de ubicación simulada y permisos en tiempo de ejecución.
- Compilación automática, pruebas unitarias y Android Lint mediante GitHub Actions.

El mapa descarga teselas de `tile.openstreetmap.org` por HTTPS, con identificación de la app y caché local. No incluye descargas masivas ni mapas sin conexión completos. Consulta la [política de uso de teselas](https://operations.osmfoundation.org/policies/tiles/); para una distribución con mucho tráfico se debe elegir un proveedor adecuado. La atribución y el enlace a OpenStreetMap permanecen visibles junto al mapa. El proveedor de mapas recibe las solicitudes necesarias para cargar las zonas que visualizas.

Android marca estas posiciones como simuladas. No hay mecanismos para ocultarlas ni evadir controles de otras aplicaciones. Se simula el proveedor GPS; una app que use otras fuentes, posiciones almacenadas o detección de simulación puede mostrar un resultado diferente.

La búsqueda de direcciones, rutas, favoritos, anuncios y compras no forman parte de esta versión.

## Compilación

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.

Abre el proyecto en Android Studio y configura el SDK, o copia `local.properties.example` a `local.properties` y ajusta `sdk.dir`. El repositorio incluye el wrapper de Gradle; no hace falta instalar Gradle por separado.

## Verificación en un teléfono

1. Comprueba que la app aparece en el selector de ubicación simulada.
2. Toca dos puntos distintos, arrastra el marcador y verifica que cambien las coordenadas. Comprueba también la entrada manual con coma decimal y los valores fuera de rango.
3. Inicia y comprueba las coordenadas desde una app de prueba que solicite actualizaciones del proveedor GPS.
4. Cambia de aplicación, gira la pantalla y apaga la pantalla durante al menos un minuto. Verifica después que las actualizaciones continúan y conservan el destino.
5. Cambia el punto y vuelve a pulsar Iniciar; confirma que se aplica el nuevo destino.
6. Detén desde la notificación y desde la app; confirma que vuelven las posiciones reales. La primera posición real puede tardar en llegar.
7. Prueba permisos denegados, notificaciones denegadas, pérdida de red del mapa y revocación de la selección como app de ubicación simulada. Una pérdida de red del mapa no debe detener una simulación ya iniciada.
8. Interrumpe el proceso y vuelve a abrir. No debe indicar que la simulación sigue activa; usa Detener para restaurar el proveedor si quedó pendiente.
