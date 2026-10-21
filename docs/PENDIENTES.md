# Pendientes de GPS Mocker

Actualizado: 21 de octubre de 2026. Base: versión 0.3.1.

Estos son los seis pendientes acordados con el usuario. Las casillas representan trabajo por realizar; la búsqueda de skills está documentada en [SKILLS.md](SKILLS.md).

Prioridades: **P0**, siguiente entrega; **P1**, preparación para distribución; **P2**, ampliaciones posteriores.

## 1. Firma estable para las actualizaciones — P0

- [ ] Definir y custodiar una clave de firma persistente, con copia de recuperación.
- [ ] Configurar la firma de las compilaciones distribuidas desde GitHub Actions sin incluir la clave ni sus contraseñas en el repositorio, logs o artefactos públicos.
- [ ] Separar las compilaciones de prueba de las de distribución y mantener `versionCode` creciente.
- [ ] Documentar cómo generar, verificar y distribuir APK firmados y, para Play Store, el AAB de producción y Play App Signing.
- [ ] Verificar que dos nuevas versiones firmadas con la misma clave se actualizan conservando favoritos, rutas y ajustes.

**Resultado esperado:** las futuras actualizaciones se instalan sobre la versión anterior y conservan los datos. La transición desde los APK actuales, firmados con claves de depuración variables, puede requerir una reinstalación; preparar antes el respaldo del punto 3. No se promete compatibilidad con una clave privada anterior que no se conserva.

Skills de apoyo: `github-actions-hardening` y, como referencia general de automatización, `github-actions-templates`. La configuración de firma Android se contrasta con la documentación de Android; esas skills no proporcionan una clave ni sustituyen su custodia.

## 2. Pruebas en teléfonos reales — P1

- [ ] Registrar dispositivo, fabricante, versión de Android, versión de WebView y APK utilizado.
- [ ] Comprobar el inicio en la ubicación real con permiso preciso y aproximado; probar denegación, ubicación desactivada, falta de señal y reintento.
- [ ] Confirmar que una posición simulada no se presenta como ubicación real y que abrir la app conserva una simulación activa.
- [ ] Verificar comienzo, actualización y detención de la simulación, restauración del GPS y recuperación tras una interrupción.
- [ ] Probar rutas, pausa, reanudación, repetición y finalización con pantalla apagada y al cambiar de aplicación.
- [ ] Medir batería, CPU y memoria durante una sesión representativa; revisar el servicio y el bloqueo de CPU con datos del dispositivo.
- [ ] Revisar el mapa claro/oscuro, legibilidad de etiquetas y marcadores, fuente ampliada, TalkBack y orientación horizontal.
- [ ] Cubrir el mínimo soportado, Android 8, y versiones recientes, incluyendo Android 15 y la versión vigente al publicar; distinguir resultados reales de pruebas en emulador.

**Resultado esperado:** informe reproducible con escenarios aprobados, incidencias y mediciones. Las 70 pruebas automatizadas de 0.3.1 son la base existente; no sustituyen esta comprobación física.

Skills de apoyo: `testing-setup`, `android-profiler`, `android-permissions-security`, `mobile-android-design` y `maplibre-cartography`.

## 3. Respaldo de favoritos y ajustes — P0

- [ ] Definir un formato de respaldo versionado para favoritos, ajustes, selección y rutas. Decidir si incluye historial e indicarlo al exportar.
- [ ] Exportar e importar mediante el selector de archivos de Android, sin permisos generales de almacenamiento.
- [ ] Validar formato, tamaño y coordenadas antes de modificar datos; una importación inválida debe conservar los datos existentes.
- [ ] Definir cómo combinar o reemplazar datos y resolver favoritos duplicados.
- [ ] Informar qué contiene el archivo, ya que puede incluir ubicaciones personales.
- [ ] Verificar exportación → instalación limpia → restauración y compatibilidad entre versiones.

**Resultado esperado:** recuperar los datos tras una reinstalación. La exportación GPX actual cubre rutas, pero no constituye un respaldo de favoritos y ajustes.

Apoyo técnico: documentación de Storage Access Framework y pruebas de persistencia e importación. No se seleccionó una skill de respaldo suficientemente adecuada al proyecto actual.

## 4. Rutas siguiendo las calles — P2

- [ ] Evaluar un motor de rutas y sus perfiles para caminar, bicicleta y automóvil; comparar cobertura, coste, cuotas, términos y privacidad.
- [ ] Mantener diferenciadas la ruta directa actual y la ruta calculada por calles.
- [ ] Previsualizar distancia, recorrido y duración estimada antes de iniciar.
- [ ] Resolver errores de conexión, límites del proveedor y trayectos imposibles conservando la ruta anterior.
- [ ] Integrar la geometría calculada con velocidad, progreso, pausa, repetición y GPX, respetando los límites de puntos y recursos del teléfono.
- [ ] Probar cruces, sentidos de circulación y restricciones según el perfil elegido.

**Resultado esperado:** simular un recorrido calculado sobre calles, con elección de perfil. La cartografía de OpenFreeMap no aporta por sí sola un servicio de cálculo de rutas.

Skills de apoyo: `maplibre-cartography` y `maplibre-tile-sources` para representación y fuentes; la elección del motor necesita documentación específica del proveedor.

## 5. Preparación para Play Store — P1

- [ ] Generar y verificar una compilación de producción y un AAB firmado, después de resolver el punto 1.
- [ ] Definir nombre, icono definitivo, categoría, descripción breve y completa, capturas y gráfico de la ficha.
- [ ] Preparar política de privacidad y declaración de Seguridad de los datos según el comportamiento real de la app, cartografía, geocodificación y los SDK que finalmente se incluyan.
- [ ] Revisar permisos de ubicación, servicio en primer plano, notificaciones y requisitos vigentes de API objetivo y de publicación en la cuenta utilizada.
- [ ] Describir claramente la función de ubicación simulada y comprobar las políticas aplicables a esa función.
- [ ] Realizar una prueba interna o cerrada según corresponda, atender las incidencias y preparar notas de versión.

**Resultado esperado:** paquete y ficha listos para presentar a revisión. Una auditoría o una skill no garantizan la aprobación de Google Play.

Skills de apoyo: `play-policy-insights`, `android-permissions-security` y `aso`. La skill `aso` se aprovecha para preparar la ficha; su auditoría completa de una ficha publicada se realizará cuando exista una URL de Play Store.

## 6. Monetización opcional con AdMob — P2

- [ ] Decidir formatos y posiciones que mantengan accesibles el mapa, las coordenadas y los controles de simulación.
- [ ] Integrar primero el SDK y unidades de prueba; verificar que la falta de red o de anuncio no bloquea el funcionamiento.
- [ ] Incorporar el flujo de consentimiento y las opciones de privacidad que correspondan a los mercados y configuración de la app.
- [ ] Medir el efecto sobre inicio, batería, memoria y uso del mapa.
- [ ] Actualizar política de privacidad y Seguridad de los datos con el comportamiento efectivo del SDK.
- [ ] Configurar unidades de producción y, cuando corresponda, `app-ads.txt`; comprobar la integración antes de distribuir.

**Resultado esperado:** monetización opcional que preserve la utilidad de la app. No se presupone una tarifa ni un ingreso fijo por anuncios.

Apoyo técnico: documentación oficial de Google Mobile Ads y UMP. Las skills específicas de AdMob encontradas tenían poca adopción o estaban orientadas a otros stacks; no se seleccionaron como guía principal.

## Orden propuesto de ejecución

1. Respaldo de datos y firma estable, preparando el respaldo antes de cualquier transición que obligue a reinstalar.
2. Verificación en teléfonos reales y corrección de incidencias.
3. Preparación de producción y Play Store.
4. Rutas por calles y monetización, como entregas posteriores.
