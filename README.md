# GPS Mocker Android

App Android en Kotlin para seleccionar puntos en un mapa y simular ubicaciones durante pruebas de desarrollo. Android 8.0 o superior; sin root ni clave de Google Maps.

Plan de trabajo: [pendientes priorizados](docs/PENDIENTES.md) y [skills evaluadas para el proyecto](docs/SKILLS.md).

## Versión 0.3.1

- Al abrir, busca una ubicación real reciente y centra el mapa sin comenzar la simulación. El botón de centrar permite reintentar. Solicita permiso de ubicación; admite ubicación aproximada. Si no hay permiso, señal o GPS activo, conserva la selección anterior e indica cómo continuar.
- El mapa comienza con estilo claro, incluso con el teléfono en modo oscuro. Se desactiva el oscurecimiento automático del WebView para mantener legibles calles y etiquetas. El selector de capas permite elegir otro estilo; las instalaciones anteriores con el ajuste automático pasan una sola vez al mapa claro.
- Descarta posiciones simuladas y posiciones almacenadas de hace más de 30 segundos. La búsqueda se cancela al elegir otro punto o salir de la pantalla y tiene un límite de 15 segundos. Una simulación activa conserva su posición; para obtener la ubicación real hay que detenerla primero. Las rutas guardadas se conservan al centrar la ubicación actual.

- Mapa vectorial a pantalla completa con MapLibre y OpenFreeMap, capas clara y oscura, controles flotantes y tarjeta inferior Material Design 3.
- Latitud y longitud de **solo lectura**: selecciona tocando el mapa, arrastrando el marcador o recuperando un lugar del buscador, favoritos o historial. Puedes copiar las coordenadas y compartir un enlace a OpenStreetMap.
- Búsqueda de ciudades, lugares y direcciones mediante el servicio de geocodificación de Android. También muestra la dirección del punto cuando el servicio está disponible.
- Favoritos con nombre, edición y eliminación; historial de las últimas 50 ubicaciones utilizadas.
- Editor de rutas: añade puntos en el mapa, arrastra sus marcadores, deshaz el último punto y configura velocidad entre 0,5 y 300 km/h. Las rutas grandes muestran los marcadores de sus extremos para mantener el mapa fluido.
- Rutas con pausa, reanudación, progreso y repetición. La repetición incluye un segmento de regreso al inicio para evitar saltos. Al finalizar una ruta se mantiene la posición final hasta que pulses Detener.
- Importación y exportación GPX mediante el selector de archivos de Android, sin permisos de almacenamiento general. Hasta 2000 puntos y 2 MB por archivo; admite tracks, rutas y waypoints.
- Opciones de altitud (−1000 a 50 000 m), precisión (1–1000 m) e intervalo de actualización (250 ms a 10 s). Los cambios se aplican a la simulación activa sin reiniciar el recorrido.
- Servicio en primer plano con notificación, pausa/reanudación de rutas y acción para detener. Si la restauración del GPS falla, muestra el error y permite reintentar.
- Acceso a las opciones de desarrollador con argumentos para desplazar y resaltar **Seleccionar aplicación de ubicación simulada**, en sistemas que admiten esa función.
- Tema claro/oscuro siguiendo el sistema, colores Material You opcionales desde Android 12 y disposición adaptada a orientación horizontal.

## Instalar desde el celular

1. En GitHub abre **Actions → Compilar APK Android** y elige una ejecución terminada en verde de la rama correspondiente.
2. En **Artifacts**, descarga `gps-mocker-debug-N`; necesitas iniciar sesión en GitHub.
3. Extrae el ZIP e instala `app-debug.apk`. Si Android lo solicita, permite instalar aplicaciones desde el navegador o gestor de archivos utilizado.
4. En las opciones de desarrollador, elige **GPS Mocker** como **Aplicación de ubicación simulada**. La app ofrece una guía y un acceso directo que intenta llevarte a esa opción. Algunos fabricantes ignoran el desplazamiento solicitado: en ese caso búscala manualmente dentro de Opciones de desarrollador.
5. Activa la ubicación del teléfono y concede el permiso de ubicación al iniciar. La ubicación aproximada es suficiente para el servicio.
6. Toca el mapa o busca un lugar. **Comenzar** aplica la selección; mover el marcador por sí solo no cambia una simulación ya iniciada. Pulsa **Actualizar** para aplicar el nuevo punto.
7. Usa **Detener** en la app o en la notificación para restaurar el proveedor GPS.

El APK tiene firma de depuración. Distintos equipos o ejecuciones de CI pueden producir firmas diferentes. Si Android rechaza la actualización por firma incompatible, desinstala la versión de prueba anterior; se eliminarán sus datos locales. Para distribuir actualizaciones permanentes se debe configurar una firma estable de publicación.

## Usar las rutas

1. Abre **Rutas → Dibujar en el mapa**. El punto seleccionado será el primer punto.
2. Toca el mapa para añadir puntos y arrastra los marcadores para ajustarlos. El botón de deshacer elimina el último punto.
3. Pulsa el botón de terminar edición, elige velocidad y repetición y pulsa **Iniciar ruta**.
4. Puedes pausar y reanudar desde la tarjeta o la notificación. Durante una pausa se siguen enviando posiciones estáticas con velocidad cero.
5. Para importar o exportar, usa **Rutas → Importar GPX / Exportar GPX**. Un archivo inválido no sustituye la ruta guardada. Exportar conserva las coordenadas, no los ajustes de velocidad ni la altitud global.

Los puntos se unen por segmentos geográficos directos, no por un motor de navegación vial. Para seguir una calle añade puntos a lo largo de ella o importa un recorrido. Los segmentos de varios tracks de un mismo archivo se concatenan; divide previamente el archivo si no quieres conectarlos.

## Favoritos, historial y búsqueda

- La estrella guarda o cambia el nombre del punto seleccionado.
- **Guardados** contiene las pestañas Favoritos e Historial. Mantén pulsado un favorito para renombrarlo o eliminarlo. Borrar el historial conserva los favoritos.
- Se registra una ubicación en el historial cuando una simulación comienza correctamente, no con cada movimiento del mapa.
- La búsqueda requiere conexión y un proveedor de geocodificación disponible en el teléfono. Algunos dispositivos sin ese servicio no pueden buscar direcciones; el mapa, las coordenadas, los favoritos y la simulación siguen funcionando. La app indica el problema en la búsqueda.

## Compilación y verificación

Requisitos: Android Studio/JDK 17 o superior, Android SDK Platform 35 y Build Tools 35.0.0. Abre el proyecto en Android Studio, o configura `sdk.dir` en `local.properties` usando `local.properties.example` como referencia.

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

GitHub Actions ejecuta esa misma verificación en pushes a `main` y ramas `feat/…`, y en pull requests a `main`. Conserva los APK 14 días y los informes 7 días. También permite ejecución manual cuando el workflow está presente en la rama predeterminada. Los informes incluyen vistas previas de los controles nativos; las pruebas de pantalla bloquean las descargas del mapa para no depender de servidores externos.

Las pruebas cubren validación numérica, persistencia, GPX inválidos y con declaraciones externas, rutas, pausa/reanudación, fin del recorrido, fallo de restauración del GPS y controles de pantalla en Android 9 y 15. La comprobación final del GPS y de las restricciones de batería debe hacerse en un teléfono real.

## Diseño y proveedores

El rediseño aplica [mobile-android-design](https://skills.sh/wshobson/agents/mobile-android-design), de `wshobson/agents`, encontrada con el flujo de [find-skills](https://github.com/vercel-labs/skills/tree/main/skills/find-skills). Se aplican sus pautas de Material Design 3 mediante Material Components para Android. Consulta [las decisiones de diseño](docs/DESIGN.md).

El componente de mapa utiliza MapLibre GL JS 4.7.1 dentro del WebView del sistema. Su JavaScript, CSS y licencia BSD se incluyen en el APK, con la integridad del paquete original verificada; no se descarga código del motor en cada inicio. Necesita una versión de Android System WebView con WebGL. El componente solo expone selección de puntos y estado de la cámara a la app; los controles, permisos y simulación se implementan en Kotlin.

La cartografía se descarga por HTTPS desde [OpenFreeMap](https://openfreemap.org), con atribución a OpenFreeMap, OpenMapTiles y OpenStreetMap, identificación de la app y caché del WebView. Los proveedores reciben las solicitudes necesarias para mostrar el área del mapa; el servicio de Android procesa las búsquedas y consultas de direcciones. No hay descarga masiva ni mapas completos sin conexión. Si el mapa no carga, se ofrece reintentar y comprobar la conexión o actualizar Android System WebView.

Android identifica las posiciones como simuladas. Se publica en el proveedor GPS mediante `LocationManager`; otras apps pueden utilizar fuentes distintas, posiciones almacenadas o rechazar ubicaciones simuladas. No se oculta esa marca.

El servicio mantiene actualizaciones al cambiar de app y utiliza un bloqueo de CPU con vencimiento corto mientras está activo. Android o el fabricante pueden interrumpirlo; no se reinicia silenciosamente después de que el proceso muera. Al abrir de nuevo se ofrece recuperar el proveedor pendiente. Si se revoca la selección como app de ubicación simulada antes de detener, vuelve a elegir GPS Mocker y pulsa Detener para reintentar la restauración.
