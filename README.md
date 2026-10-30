# GPS Mocker Android

App Android en Kotlin para seleccionar puntos en un mapa y simular ubicaciones durante pruebas. Android 8 o superior, sin root ni clave de Google Maps. Versión 0.4.0, código 5.

## Probar desde el celular

1. Abre **Actions → Compilar APK Android** en GitHub y una ejecución verde de la rama que quieres probar.
2. Descarga el artefacto `gps-mocker-debug-N`, extrae el ZIP e instala `app-debug.apk`.
3. Esta versión se llama **GPS Mocker Dev** y puede convivir con la app anterior. Pulsa **Detener** en la app anterior antes de cambiar de aplicación simulada. Después elígela en **Opciones de desarrollador → Seleccionar aplicación de ubicación simulada**. El acceso de la app intenta resaltar esa opción; algunos fabricantes exigen buscarla manualmente.
4. Activa la ubicación, concede permiso y elige un punto desde el mapa. **Comenzar** inicia la simulación; **Actualizar** aplica un punto nuevo. **Detener**, también disponible en la notificación, restaura el GPS.

Conserva la app antigua hasta recuperar sus datos. Las firmas de depuración pueden variar entre ejecuciones. [Respaldo, rescate por USB y transición a firma estable](docs/RESPALDO_Y_FIRMA.md).

## Funciones

- Mapa vectorial claro por defecto, aunque el teléfono use tema oscuro; selector claro/oscuro/automático. Al abrir busca una posición real reciente sin comenzar la simulación. El botón de centrar permite reintentar y descarta ubicaciones simuladas.
- Coordenadas de solo lectura en la tarjeta, con **Introducir coordenadas** para editar latitud y longitud en una hoja independiente. Acepta punto o coma decimal, muestra errores por campo y conserva el borrador al recrear la pantalla. Seleccionar no inicia ni actualiza la simulación. También permite elegir desde el mapa, marcador, buscador, favoritos e historial; copiar coordenadas y compartir enlace.
- Favoritos con nombre e historial de 50 lugares. Búsqueda y dirección mediante Geocoder de Android, cuando está disponible.
- Rutas directas, importadas GPX y cálculo por calles para caminar, bicicleta o automóvil **cuando se configure el servicio externo**. El mapa OpenFreeMap por sí solo no calcula recorridos.
- Velocidad de 0,5–300 km/h, pausa, reanudación, repetición y progreso. Al completar se conserva la posición final hasta Detener. Los circuitos viales calculan también el regreso por calles.
- GPX de hasta 2.000 puntos y 2 MiB; conserva la procedencia y atribución de rutas Geoapify.
- **Ajustes → Respaldo y restauración**: JSON versionado con favoritos, historial, selección, rutas y ajustes. Vista previa y reemplazo atómico; un archivo inválido conserva los datos existentes. Recuperación explícita ante datos locales dañados.
- Altitud, precisión e intervalo configurables; servicio en primer plano con notificación, controles y limpieza recuperable si Android interrumpe el proceso.
- Material 3, tema del sistema, colores Material You opcionales, controles adaptables y política de privacidad accesible sin red.
- Banner opcional con consentimiento UMP. **Desactivado por defecto**; no hay anuncios intersticiales ni vídeos.

## Activar servicios y distribuir

- [Respaldo y firma](docs/RESPALDO_Y_FIRMA.md): rescate del APK anterior, claves permanentes y APK/AAB firmados desde `main`.
- [Rutas y publicidad](docs/RUTAS_Y_PUBLICIDAD.md): Cloudflare/Geoapify gratuitos con cupo compartido, URL del proxy, AdMob y web de privacidad.
- [Preparación de Play Store](docs/PUBLICACION.md): ficha, arte gráfico, privacidad, declaraciones y prueba cerrada de la cuenta personal.
- [Pruebas](docs/PRUEBAS.md), [pendientes](docs/PENDIENTES.md) y [skills aplicadas/evaluadas](docs/SKILLS.md).

Sin URL del proxy, las rutas directas y GPX funcionan y el cálculo por calles informa que no está disponible. No se incluyen claves Geoapify, claves de firma ni identificadores publicitarios propios del propietario. Configurar cuentas, probar en teléfonos reales y publicar son pasos pendientes documentados, no resultados simulados.

## Compilar

Requisitos: JDK 21, Android SDK Platform 36 y Build Tools 36.0.0. Configura `sdk.dir` en `local.properties` o las variables habituales del SDK.

```bash
./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug assembleRelease bundleRelease
python -m unittest discover -s tools -p 'test_*.py'
npm ci --prefix routing-worker --ignore-scripts
npm --prefix routing-worker test
```

APK Dev: `app/build/outputs/apk/debug/app-debug.apk`. La salida release local es **sin firma** hasta ejecutar la herramienta de firma o el workflow configurado. GitHub Actions conserva APK de prueba 14 días e informes 7 días; los informes incluyen recursos gráficos de tienda y previews de controles nativos.

## Proveedores y límites

MapLibre GL JS 4.7.1 viene empaquetado con JS/CSS/licencia; OpenFreeMap sirve cartografía por HTTPS con atribución. El WebView necesita WebGL y conexión; no hay descarga masiva ni mapas completos sin conexión. Geocoder depende del proveedor del teléfono. Geoapify, si se habilita, recibe los puntos elegidos mediante el proxy Cloudflare. Consulta la [política](site/privacy.html).

Android marca las posiciones como simuladas; otras apps pueden rechazarlas o usar otras fuentes. La app no oculta esa marca. No reinicia silenciosamente el servicio después de morir el proceso. Si se revoca la selección como app simulada antes de detener, vuelve a elegirla y pulsa Detener para reintentar la limpieza.

El diseño sigue [mobile-android-design](https://skills.sh/wshobson/agents/mobile-android-design) y las decisiones de [DESIGN.md](docs/DESIGN.md). El mapa, las pruebas, los permisos, CI y la preparación de Play se revisan con las skills seleccionadas en el proyecto.
