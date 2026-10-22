# Activar rutas por calles y publicidad

## Rutas desde el mapa

El modo **Directa** conecta puntos geográficos. **Por calles** calcula un recorrido para caminar, bicicleta o automóvil. **Importada GPX** conserva la geometría del archivo. La velocidad de simulación es independiente del tiempo vial estimado.

En Rutas, elige **Por calles**, perfil, velocidad y repetición; edita puntos en el mapa y pulsa **Calcular por calles**. No se calcula con cada toque. Un error conserva la ruta anterior. Cambiar puntos, perfil o repetición exige recalcular. Una ruta ya calculada y guardada se reutiliza sin consultar de nuevo al proveedor al comenzar.

Límites: 2–20 puntos contando el origen añadido al repetir, hasta 100 km y 2.000 vértices finales. El circuito vuelve por calles; no se añade un regreso recto. Si la geometría necesita reducirse, se conserva una desviación máxima de 2 m y los vértices cercanos a los puntos elegidos. Si no cabe, se rechaza y se pide acortar. El GPX exporta geometría y atribución, no velocidad ni ajustes de simulación.

## Desplegar el servicio gratuito

La app no incluye una clave Geoapify. Sin endpoint configurado informa que el servicio no está disponible; permanecen rutas directas y GPX. Necesitas cuentas propias de Geoapify y Cloudflare, ambas en su plan gratuito.

1. Crea un proyecto Geoapify Free y su clave. No contrates un plan de pago ni actives actualizaciones automáticas.
2. Desde `routing-worker`, con Node 20+:

```bash
npm ci --ignore-scripts
npx wrangler login
npx wrangler secret put GEOAPIFY_KEY
npm run deploy
```

Introduce la clave cuando la CLI la pida. `wrangler.jsonc` crea un Durable Object SQLite; no actives Workers Paid. La configuración desactiva observabilidad del Worker. No actives logs de solicitudes o cuerpos que incluyan coordenadas o la clave del proveedor.

3. Toma la URL HTTPS que devuelve Cloudflare y añade `/v1/route`. Compila con:

```bash
./gradlew assembleDebug -ProutingUrl=https://TU-WORKER.TU-CUENTA.workers.dev/v1/route
```

En GitHub configura la variable de repositorio `ROUTING_URL` con esa URL para la entrega estable. El workflow de pruebas normal no presupone un servicio externo configurado. Comprueba en un teléfono los tres perfiles antes de distribuir con el endpoint activo.

La cuota se comparte entre todas las instalaciones: 100 intentos por ventana móvil de 24 h, 10 por minuto, uno por segundo y 2.000 créditos contabilizados. El contador reserva antes de llamar al proveedor; no reintenta automáticamente y bloquea si el contador falla. También registra de forma conservadora recargos de un desvío superior a 500 km, aunque la app lo rechace por superar 100 km.

Geoapify Free ofrece 3.000 **créditos**, no 3.000 rutas, por día y hasta 5 solicitudes/s. Una solicitud con N puntos consume normalmente N−1 créditos. Sus cuotas son blandas y puede haber recargos por distancia; estos controles no son garantía contractual de coste cero. Supervisa el panel y mantén la cuenta Free sin método de pago ni plan adicional. El cupo público puede agotarse por otros usuarios; el modo manual permanece disponible.

Solo se envían puntos y perfil. El proxy no reenvía IP, cookies, autorización ni identificadores del teléfono. Cloudflare procesa la conexión y Geoapify recibe las coordenadas: no se afirma anonimato. Los contadores guardan tiempo y créditos, no recorridos. Fuentes: [precios](https://www.geoapify.com/pricing/), [créditos](https://www.geoapify.com/pricing-details/), [DPA](https://www.geoapify.com/data-processing-agreement/), [Routing](https://apidocs.geoapify.com/docs/routing/).

## Banner de AdMob

Por defecto `adsEnabled=false`: la app no inicializa UMP/Ads ni solicita anuncios. La integración usa Google Mobile Ads Next-Gen 1.5.0 y UMP 4.0.0. Se excluyen los permisos Advertising ID y estado básico del teléfono, incluso en compilaciones con anuncios; no se usa ubicación seleccionada ni se registra el WebView con Ads.

Para desarrollo:

```bash
./gradlew assembleDebug -PadsEnabled=true -PadsTestMode=true
```

Usa únicamente anuncios de prueba. El banner mide 320×50 dp y reserva espacio debajo de la tarjeta, separado de controles e insets. Se oculta si no cabe o falla la carga. No hay anuncios en notificaciones ni servicio de simulación, intersticiales o vídeos. La app funciona sin anuncio o conexión.

UMP actualiza consentimiento en cada inicio y muestra formulario cuando corresponde. Solo después de `canRequestAds()` se inicializa el SDK en segundo plano. Las opciones de privacidad aparecen en Ajustes si UMP las exige. Verifica formulario requerido/no requerido, rechazo, error de red, consentimiento guardado y reapertura de opciones con dispositivos de prueba y la configuración real de tu cuenta.

Para producción, crea y verifica tus cuentas, configura mensajes de privacidad, registra la aplicación y la unidad banner. Configura variables de repositorio:

- `ADS_ENABLED=true`, únicamente al estar preparada la monetización.
- `ADMOB_APP_ID` y `ADMOB_BANNER_ID`, proporcionados por tu propia cuenta.

El workflow de entrega fuerza `adsTestMode=false` y comprueba formato de IDs. Los IDs son configuración pública, no claves privadas. Habilitar variables no sustituye la preparación de la cuenta ni las pruebas de consentimiento. Mantén `ADS_ENABLED=false` mientras falten.

## Web, privacidad y app-ads.txt

`site/` contiene web y política listas como fuentes estáticas. Publica en Cloudflare Pages Free para disponer de un hostname con raíz propia. Con sesión del propietario y desde `routing-worker`:

```bash
npx wrangler pages project create gps-mocker --production-branch main
npx wrangler pages deploy ../site --project-name gps-mocker
```

Usa el hostname que Cloudflare asigne y comprueba que `/privacy.html` sea público por HTTPS, sin login. No se ha desplegado un sitio desde esta sesión.

Cuando AdMob proporcione la línea de vendedor autorizada de tu cuenta, crea `site/app-ads.txt` con su contenido exacto y vuelve a desplegar. El `.example` no es una autorización válida. La línea debe estar en `/app-ads.txt` de la raíz del hostname, no solo bajo una subcarpeta. Registra ese sitio como web del desarrollador en Play y comprueba la verificación en AdMob.

Una app distribuida solo por GitHub no basta para completar la monetización de AdMob: se debe vincular la ficha de una tienda admitida y superar la preparación de la app. Si la revisión sigue pendiente, publica con publicidad desactivada y actívala mediante una actualización posterior.
