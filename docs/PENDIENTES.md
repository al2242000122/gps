# Pendientes de GPS Mocker

Actualizado: 30 de octubre de 2026. Implementación 0.4.0; las casillas completadas indican código o documentación terminados, no cuentas creadas ni validaciones físicas.

## Modernización auditada: cambios propuestos en ramas

- Entrada manual preservada: PR #2 (`fix/paridad-coordenadas`).
- Deltas del mapa, caché de geometría y restricción WebView: PR #3 (`perf/mapa-deltas`), CI aprobado.
- Admisión por origen, seudónimos diarios y limpieza del presupuesto: PR #4 (`fix/routing-admision`), CI aprobado; proxy sin desplegar.
- Persistencia fuera del hilo de UI y estado retenido: PR #5 (`refactor/estado-persistencia`).
- Temas Figma, preparación comprobable, posición activa y accesibilidad: PR #6 (`feat/ux-preparacion`).
- Integridad del mapa, tratamiento de atribuciones como texto y herramientas del proxy actualizadas: rama `fix/mapa-integridad`.

Los PR están apilados para revisar cada fase. Las pruebas, lint y APK deben aprobarse en el head de cada PR antes de integrarlo; una captura JVM no acredita funcionamiento físico. No se ha fusionado ninguno ni modificado `main`. La integración y entrega requieren revisión y aprobación del propietario.

Diseño y límites de adaptación: [DISENO_IMPLEMENTADO.md](DISENO_IMPLEMENTADO.md). Dependencias, aviso de MapLibre y controles operativos: [DEPENDENCIAS_Y_WEBVIEW.md](DEPENDENCIAS_Y_WEBVIEW.md).

Pendientes que requieren acceso externo: dispositivo ADB y datos antiguos del usuario; claves de firma reales; cuentas Cloudflare/Geoapify/Play/AdMob; URL del proxy; reglas de protección y entorno de entrega en GitHub. Se conserva el código y las guías para esos pasos, sin inventar resultados de laboratorio, claves o cuentas.

## 1. Firma estable — P0

- [x] Separar Dev `.debug` de distribución estable y establecer versionCode creciente.
- [x] Preparar creación local de claves persistentes app/upload con contraseña, certificados y copia bajo custodia del propietario.
- [x] Flujo manual desde main: compilar sin secretos y firmar en otro job, verificar huellas, generar metadatos públicos y limpiar temporales.
- [x] Prueba con claves efímeras: APK/AAB válidos, certificados separados y rechazo de huella incorrecta.
- [ ] Crear y custodiar las claves **reales** y configurar los secretos del entorno GitHub `release`.
- [ ] Integrar el código validado en main y ejecutar la primera entrega con esas claves.
- [ ] Verificar dos actualizaciones estables consecutivas en el teléfono conservando todos los datos.

Guía: [respaldo y firma](RESPALDO_Y_FIRMA.md). La firma de APK antiguos puede ser irrecuperable: rescatar los datos antes de una reinstalación.

## 2. Pruebas físicas — P1

- [x] Ampliar automatización para API 26/35/36 y pruebas de respaldo, corrupción, escritura fallida, restauración y rutas.
- [x] Preparar matriz reproducible y sesiones de rendimiento de 30 minutos.
- [ ] Ejecutar la matriz en el teléfono del usuario y un segundo fabricante para Play.
- [ ] Medir CPU, memoria, batería, arranque y wake lock; conservar trazas e incidencias.
- [ ] Comprobar restauración real del GPS, interrupciones, pantalla apagada, TalkBack, fuente ampliada y rotación.

Estado: no había dispositivo ADB conectado. [Protocolo y evidencias](PRUEBAS.md).

## 3. Respaldo — P0

- [x] JSON versionado (lee 1/2, exporta 2), con favoritos, historial, selección, apariencia, opciones y ruta.
- [x] Exportar/importar con SAF sin permisos generales; mostrar fecha y conteos y confirmar reemplazo completo.
- [x] Validar estructura, UTF-8, versión, tamaño, coordenadas y límites antes de modificar.
- [x] DataStore único, migración persistente desde preferencias y reemplazo atómico; no vaciar silenciosamente datos dañados.
- [x] Bloquear restauración con simulación/limpieza pendientes y escrituras normales durante el reemplazo.
- [x] Herramienta USB para extraer y verificar XML del APK antiguo, conservar originales y generar JSON compatible; fixture contrastado con el codec Android.
- [ ] Ejecutar rescate de los datos **del usuario** y comprobar exportación → instalación limpia → restauración en teléfono.

## 4. Rutas por calles — P2

- [x] Seleccionar Geoapify Free comercial y proxy Cloudflare Free con secreto fuera del APK.
- [x] Perfiles caminar/bicicleta/automóvil, puntos elegidos desde mapa, cálculo explícito, distancia/tiempo y alternativa directa/GPX.
- [x] Circuito completo por calles, validación de geometría, simplificación acotada y atribución en app, respaldo y GPX.
- [x] Cuotas compartidas con SQLite, reservas previas, bloqueo ante fallo, cancelación de respuestas obsoletas y ausencia de reintentos automáticos.
- [x] Pruebas de contrato, errores y SQLite concurrente/persistente; preparación del bundle mediante dry-run.
- [ ] Crear cuentas gratuitas del propietario, guardar Geoapify como secreto y desplegar el Worker.
- [ ] Configurar URL y comprobar cobertura/perfiles/circuitos reales en el teléfono.

Guía: [rutas y publicidad](RUTAS_Y_PUBLICIDAD.md). Las cuotas de Geoapify son blandas: no se promete uso ilimitado ni garantía contractual de coste cero.

## 5. Preparación para Play Store — P1

- [x] Actualizar compile/target a API 36 conservando mínimo 26; generar APK/AAB de producción sin firma.
- [x] Icono adaptativo y exportación de icono 512 y gráfico 1024×500 desde vectores de la app.
- [x] Preparar ficha en español, política local/web, mapa de datos/permisos y requisitos de publicación.
- [x] Ejecutar auditoría `play-policy-insights`; resultado Needs Review por configuraciones/declaraciones pendientes, sin ficha publicada para comparar.
- [ ] Crear y verificar cuenta personal Play; configurar contacto real y web pública.
- [ ] Obtener capturas reales del mapa y vídeo de uso del servicio en primer plano.
- [ ] Completar declaraciones, clasificación, audiencia y Seguridad de los datos del build que se presente.
- [ ] Registrar claves en Play App Signing y ejecutar prueba interna/cerrada: 12 testers durante 14 días, después solicitar producción.

Guía: [publicación](PUBLICACION.md). La auditoría no garantiza aprobación.

## 6. AdMob — P2

- [x] Integración opcional Next-Gen/UMP, solo banner 320×50, espacio reservado y ocultación si no cabe o falla.
- [x] Consentimiento previo a inicialización/carga y acceso a opciones de privacidad cuando corresponde.
- [x] IDs de prueba y flag desactivado por defecto; excluir Advertising ID y estado básico del teléfono.
- [x] Política y guía de configuración de cuenta, mensajes, unidades reales y app-ads.txt.
- [ ] Crear/verificar cuenta AdMob, mensajes UMP y unidad banner del propietario.
- [ ] Probar consentimiento y rendimiento real con anuncios de prueba.
- [ ] Publicar línea app-ads.txt de la cuenta, vincular ficha Play y completar preparación de AdMob.
- [ ] Habilitar unidades reales mediante una entrega posterior cuando esté todo preparado.

## Próximo paso operativo

Probar el APK Dev junto a la instalación actual, rescatar los datos antiguos y preparar las claves del propietario. Después activar servicios externos y completar las verificaciones físicas antes de presentar la app a Play.
