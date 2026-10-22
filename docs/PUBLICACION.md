# Preparación de Google Play y AdMob

## Estado y requisitos del propietario

El proyecto genera APK y AAB de producción sin firma, e incluye el flujo para firmarlos con claves persistentes. La cuenta de Play, la identidad del desarrollador, sus claves, las cuentas externas, las pruebas físicas y la ficha todavía requieren configuración real del propietario.

Se eligió cuenta **personal nueva**. Google Play cobra US$25 una vez y exige al menos 12 testers inscritos continuamente durante 14 días en una prueba cerrada antes de solicitar acceso a producción; cumplir el plazo no concede aprobación automática. Fuente: https://support.google.com/googleplay/android-developer/answer/14151465 .

El objetivo del proyecto es API 36 (Android 16), manteniendo Android 8 como mínimo. Verifica los requisitos de Play nuevamente el día de presentar la app: https://support.google.com/googleplay/android-developer/answer/11926878 .

## Ficha en español propuesta

**Nombre (máximo 30):** GPS Mocker: ubicación y rutas

**Descripción breve (máximo 80):** Simula ubicaciones GPS y recorridos desde el mapa. Guarda, importa y respalda.

**Descripción completa:**

> Prepara pruebas de ubicación en Android con GPS Mocker. Elige un punto desde el mapa, consulta sus coordenadas y comienza la simulación cuando estés listo.
>
> • Mapa claro, buscador de lugares y coordenadas visibles de solo lectura.
> • Favoritos con nombre e historial de ubicaciones utilizadas.
> • Recorridos con velocidad configurable, pausa, reanudación y repetición.
> • Importación y exportación de rutas GPX.
> • Respaldo y restauración de favoritos, historial, rutas y ajustes.
> • Opciones de altitud, precisión e intervalo de actualización.
> • Notificación con controles para detener la simulación y restaurar el GPS.
>
> Al abrir, la app intenta mostrar tu ubicación real sin iniciar una simulación. Para simular, selecciona GPS Mocker como aplicación de ubicación simulada en las opciones de desarrollador de Android. No requiere root. Android y otras aplicaciones pueden identificar o rechazar posiciones simuladas.
>
> Necesitas conexión para descargar el mapa y utilizar la búsqueda. El servicio de búsqueda depende del proveedor disponible en tu teléfono. Los recorridos por calles, cuando el servicio está habilitado, tienen un cupo compartido; también puedes dibujar recorridos directos o importar un GPX.
>
> Compatible con Android 8 o posterior. Revisa la política de privacidad y guarda un respaldo antes de cambiar de instalación.

Categoría propuesta: Herramientas. Clasificación y audiencia: responde el cuestionario según el contenido real; no se ha definido una app infantil ni se presupone una edad declarada. Añade el correo de soporte del propietario registrado en Play, la URL pública de privacidad y la web del desarrollador. No se inventaron datos personales de contacto.

La redacción aplica los límites y claridad de la skill `aso`; no es una auditoría de conversión de una ficha publicada ni promete instalaciones. Evita términos como «indetectable», promesas de eludir controles o funciones que el build presentado no tenga habilitadas.

## Recursos visuales

La app tiene icono adaptativo propio. Al ejecutar los tests de la interfaz, se generan:

- `app/build/outputs/store/icon-512.png`: icono de 512×512.
- `app/build/outputs/store/feature-1024x500.png`: gráfico de 1024×500.

Verifica los archivos exportados antes de subirlos. Las previews de Robolectric muestran controles nativos y no renderizan la cartografía; no se deben subir como si fueran capturas reales con mapa.

Captura en el teléfono al menos: inicio con mapa claro, selección/coordenadas, guardados, editor de recorrido y respaldo. Usa ubicaciones públicas de demostración, sin domicilios personales, notificaciones privadas ni datos ajenos. Las capturas deben corresponder al APK/AAB presentado.

## Privacidad y Seguridad de los datos

Publica `site/privacy.html` por HTTPS antes de presentar. Copia equivalente accesible en Ajustes sin red. El canal actual de soporte público es el repositorio; añade un contacto privado del propietario si hace falta para solicitudes personales. Mantén sincronizada la copia de `app/src/main/assets/privacy/` al cambiar la política web.

No copies automáticamente la salida del scanner al formulario de Play. Algunas coincidencias `record`/`track` del mapa no son audio ni datos de salud. El acceso a archivos por SAF y la persistencia únicamente local no significan por sí solos recopilación remota.

| Flujo real | Datos y destinatarios | Qué revisar para el formulario |
| --- | --- | --- |
| Favoritos, historial, ajustes y geometrías locales | DataStore en el teléfono | No se suben automáticamente a un servidor propio |
| Respaldo/GPX | Archivo en el destino elegido por el usuario | Si elige un proveedor en nube, es una acción de exportación solicitada por el usuario |
| Mapa | IP y zonas consultadas a OpenFreeMap/recursos del estilo | Ubicación inferida y datos técnicos conforme al proveedor |
| Ubicación actual y dirección | Precisa o aproximada según permiso; consultas a Geocoder Android | Declarar el tratamiento remoto aplicable al proveedor de geocodificación y explicar su finalidad |
| Por calles habilitado | Puntos seleccionados/perfil a Cloudflare y Geoapify | Coordenadas pueden ser personales; funcionalidad solicitada, no anonimato |
| Publicidad habilitada | Google Mobile Ads/UMP: IP/ubicación aproximada inferida, interacciones y diagnósticos según configuración | Actualizar antes de activar, contrastando la declaración del SDK vigente y el consentimiento; no se entrega selección GPS ni se solicita Advertising ID |

Revisa si cada dato se recopila o comparte, su finalidad, tratamiento temporal y opciones de control con las definiciones del formulario vigente. No declares «no recopila datos» basándote solo en que la app no tenga login. El cifrado en tránsito debe mantenerse por HTTPS. La app no ofrece cuentas; la eliminación de cuenta no aplica, pero sí los controles locales y contacto de privacidad.

Para el SDK: https://developers.google.com/admob/android/next-gen/privacy/play-data-disclosure . Política de datos: https://support.google.com/googleplay/android-developer/answer/10144311 .

## Permisos y servicio en primer plano

El manifiesto declara ubicación aproximada/precisa, servicio en primer plano de tipo `location`, notificaciones y wake lock. No se solicita ubicación en segundo plano ni acceso general al almacenamiento. Advertising ID y estado básico del teléfono se excluyen del manifiesto combinado.

Completa la declaración de servicio en primer plano de Play, explicando exactamente su función: la persona inicia una simulación GPS y necesita actualizaciones mientras prueba otra app o apaga la pantalla. La notificación permite detener y restaurar el GPS; la app no reinicia silenciosamente la simulación tras morir el proceso. No lo describas como seguimiento o navegación real si no hace esas funciones.

Graba un vídeo breve: selección como app simulada, elección de punto, Comenzar, notificación persistente, cambio de app, pausa/ruta y Detener. Explica qué se interrumpe si el servicio se detiene. Verifica que el tipo declarado sea aceptable para el caso de uso; no cambies a `specialUse` como mecanismo para evitar la revisión. Fuentes: https://support.google.com/googleplay/android-developer/answer/13392821 y https://developer.android.com/develop/background-work/services/fgs/service-types .

## Orden de salida

1. Recuperación de datos y configuración de claves: [respaldo y firma](RESPALDO_Y_FIRMA.md).
2. Completar [pruebas físicas](PRUEBAS.md), incluyendo dos actualizaciones estables sin pérdida de datos.
3. Desplegar web y, si se habilita, proxy de rutas: [configuración](RUTAS_Y_PUBLICIDAD.md).
4. Generar entrega firmada desde `main`; comprobar huellas, SHA-256, paquete, versión, permisos y compatibilidad nativa de 16 KiB.
5. Crear ficha, declaraciones, clasificación y prueba interna. Subir el AAB con la clave de subida y registrar la clave permanente de aplicación en Play App Signing.
6. Ejecutar la prueba cerrada de la cuenta personal y corregir incidencias. Solicitar acceso a producción y presentar a revisión cuando corresponda.
7. Vincular la app publicada a AdMob, verificar `app-ads.txt`, mensajes de privacidad y preparación. Mantener anuncios desactivados hasta que esa configuración esté lista; habilitarlos en una actualización posterior si es necesario.

La auditoría con `play-policy-insights` se ejecutó sobre el código y no encontró una ficha publicada para comparar. Su resultado fue **Needs Review**, con declaraciones previas a publicación pendientes; no garantiza aprobación. No hay publicación, testers ni cuentas creadas desde esta implementación.
