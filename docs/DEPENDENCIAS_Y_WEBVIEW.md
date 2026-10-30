# Dependencias y contenido del mapa

## Límite de confianza

La WebView carga solamente el documento empaquetado `appassets.androidplatform.net`. Las solicitudes remotas se restringen al origen HTTPS exacto `tiles.openfreemap.org`; no se admiten credenciales, otros puertos, navegación ni tráfico HTTP. La CSP no permite scripts remotos ni scripts inline. Los datos de selección y ruta se serializan como JSON; no se introducen nombres o direcciones como HTML. El puente no inicia simulaciones.

Los créditos de OpenStreetMap, OpenMapTiles, OpenFreeMap y Geoapify se presentan mediante la UI nativa con enlaces, independientemente de la cartografía cargada. Cambiar de proveedor exige revisar también sus condiciones y créditos.

## MapLibre incluido

Se conserva la versión 4.7.1 y su licencia BSD para mantener la compatibilidad gráfica existente. La migración a una versión mayor exige verificar WebGL y los dispositivos API 26 antes de retirar ese soporte. No se afirma que esta versión antigua esté libre de vulnerabilidades.

El aviso oficial [GHSA-jrc7-96c5-q579](https://github.com/maplibre/maplibre-gl-js/security/advisories/GHSA-jrc7-96c5-q579) describe ejecución de HTML no confiable en atribuciones. La corrección upstream es 6.4.1. Esta app ya usa `attributionControl: false`, pero se añade una defensa local: el único destino HTML del control de atribuciones se cambia de `innerHTML` a `textContent`. Incluso si se habilitase accidentalmente, mostrará texto literal y no interpretará marcado del proveedor. Los enlaces nativos siguen funcionando. Es un endurecimiento local deliberado, no una actualización a 6.4.1 ni una copia de su saneador.

`vendor-integrity.json` fija versión, origen, parche y SHA-256 del JS, CSS y licencia. El test carga el `AttributionControl` real empaquetado y comprueba que atribuciones personalizadas y de fuentes con atributos peligrosos se escriben solo como texto. CI comprueba también los hashes y el contrato de deltas/recarga de estilo. Un hash detecta cambios accidentales; no sustituye la revisión del origen.

### Actualización revisable

1. Revisar releases y avisos en el repositorio oficial de MapLibre.
2. Obtener JS, CSS y licencia de la misma versión publicada; verificar origen e integridad de la distribución.
3. Mantener la protección de atribuciones o usar una versión upstream corregida. No introducir HTML de proveedores ni usar Popup.setHTML con datos externos.
4. Actualizar el manifiesto, conservar los tests y abrir un PR. Nunca descargar librerías ejecutables al iniciar la app.
5. Ejecutar CI y comprobar mapa, red, WebGL, estilos, marcadores y rutas en API 26 y un dispositivo moderno. Las pruebas JVM no renderizan WebGL.

## Herramientas del proxy

Wrangler y Miniflare son herramientas de desarrollo; no se empaquetan en el APK ni en el Worker. Se actualizan juntas, con versiones exactas y lockfile, Node 24 y `npm ci --ignore-scripts`. Miniflare sigue la versión que utiliza Wrangler; su sufijo beta debe revisarse en futuras actualizaciones. CI ejecuta `npm audit --audit-level=high`, además de contratos y SQLite real. Una caída del registro o un nuevo aviso requiere revisión; no se ignora automáticamente.

Dependabot prepara propuestas mensuales para Gradle, npm y Actions cuando esta configuración llegue a la rama predeterminada. No fusiona cambios. Las Actions se fijan por SHA y se revisan antes de sustituirlos.

## Controles operativos pendientes

La protección de `main`, revisión obligatoria y aprobación de entornos de entrega se configuran en GitHub por un administrador. Los PR y el CI preparados aquí no equivalen a tener esas reglas activadas. No se crean claves de firma, secretos de Geoapify/Cloudflare ni cuentas del propietario.
