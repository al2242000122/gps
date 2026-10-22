# Respaldo, transición y firma

## Probar desde el celular

La compilación de desarrollo se llama **GPS Mocker Dev**, paquete `com.diegoca.gpsmocker.debug`. Puede coexistir con los APK anteriores (`com.diegoca.gpsmocker`). Selecciona **GPS Mocker Dev** en las opciones de desarrollador para probarla. La firma de desarrollo puede variar entre ejecuciones; exporta un respaldo antes de reemplazar una instalación Dev incompatible.

La distribución estable conservará `com.diegoca.gpsmocker` y una firma permanente. Todavía requiere crear y custodiar las claves del propietario. El APK actual de prueba no permite recuperar por arte de magia la clave de una ejecución antigua.

## Rescatar la instalación antigua con computadora

Requisitos: Python 3.10+, Android platform-tools/ADB, depuración USB y el teléfono autorizado. La herramienta admite APK 0.3.x (versionCode 3 o 4); una versión nueva usa el respaldo dentro de la app. La instalación antigua debe ser depurable (`run-as`); si no lo es, la herramienta falla sin desinstalar ni interpretar el error como datos vacíos.

1. Abre la app antigua, pulsa **Detener** y confirma que el GPS quedó restaurado. Mantén instalada esa versión.
2. Conecta por USB y comprueba `adb devices`. Autoriza la computadora en el teléfono.
3. Desde el repositorio ejecuta:

```bash
python tools/rescue_legacy_backup.py --output ../rescue-gps-2026
```

Si hay varios teléfonos, añade `--serial NUMERO`. En Windows también puedes usar `py` en lugar de `python`. Si ADB no está en PATH, usa `--adb RUTA_AL_EJECUTABLE`.

La herramienta revisa la marca de limpieza, envía la app a inicio y la detiene para extraer datos estables. Guarda los tres XML originales, un JSON versión 1, conteos y SHA-256 en un directorio nuevo. Las dos extracciones deben coincidir. No desinstala la app ni modifica sus preferencias.

4. Valida el JSON con el codec real Android, usando JDK 21 y SDK 36:

```bash
./gradlew verifyLegacyBackup -PlegacyBackup=/ruta/absoluta/rescue-gps-2026/gps-mocker-respaldo.json
```

En Windows utiliza `gradlew.bat`. Revisa que la prueba `external legacy rescue is validated by production codec` pase; sin esa propiedad la prueba se omite y no valida ningún rescate.

5. Copia el JSON al teléfono e impórtalo primero en GPS Mocker Dev. Comprueba favoritos, nombres, historial, ajustes y ruta. Conserva también los XML originales y los hashes.
6. Solo después de recuperar y comprobar los datos, realiza la transición a un APK estable que exija desinstalar el anterior. Restaura el mismo JSON en la instalación estable.

Si el rescate falla, conserva la instalación antigua. No se recomienda desinstalar para resolver un error de lectura.

## Respaldo dentro de la app

En **Ajustes → Respaldo y restauración → Exportar**, elige dónde guardar el JSON. Incluye favoritos, historial (hasta 50 lugares), selección, parámetros de simulación, apariencia y ruta. Puede revelar domicilios y recorridos: guárdalo en un lugar de confianza.

**Restaurar** muestra fecha y conteos y pide reemplazar los datos actuales. No combina bibliotecas. La app admite formatos 1 y 2; exporta 2. Límite: 2 MiB. Se validan UTF-8, estructura, versiones, coordenadas, textos, rutas y ajustes antes de guardar. Un error de validación o escritura conserva los datos anteriores.

La restauración se bloquea si hay simulación activa o GPS pendiente de limpiar; vuelve a comprobarlo al confirmar. No restaura permisos, selección de app simulada, avisos del sistema ni estado del servicio. No inicia la simulación. El proceso utiliza una única transacción DataStore y bloquea escrituras normales durante el reemplazo.

La migración desde las preferencias antiguas se escribe una sola vez y conserva los XML originales. Si el archivo local está dañado, se abre recuperación; **Detener y restaurar GPS** sigue disponible. La recuperación desde un JSON válido archiva el archivo dañado antes de sustituirlo. No hay reinicio silencioso de la biblioteca a vacía.

## Crear y custodiar claves permanentes

Hazlo en tu propia computadora, con JDK/keytool y una carpeta fuera del repositorio:

```bash
python tools/prepare_release_keys.py --directory ../gps-mocker-claves
```

Se crean `app.jks` (identidad permanente de la app), `upload.jks` (subida de AAB), certificados públicos y sus huellas SHA-256. La contraseña se pide sin mostrarla. Guarda una segunda copia cifrada en otro lugar y la contraseña en un gestor. Nunca publiques los JKS, su Base64 o contraseñas.

Para configurar GitHub automáticamente, inicia sesión con tu CLI `gh` con permisos administrativos adecuados y añade `--github`. Crea el entorno `release`, carga ocho secretos por stdin y dos huellas públicas. Si falla a mitad, conserva las claves; no vuelvas a generar otras para sustituirlas. Carga manualmente lo que falte en **Settings → Environments → release**:

| Tipo | Nombres |
| --- | --- |
| Secretos de app | `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` |
| Secretos de subida | `UPLOAD_KEYSTORE_BASE64`, `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD` |
| Variables públicas | `ANDROID_CERT_SHA256`, `UPLOAD_CERT_SHA256` |

Los alias generados son `gps-mocker-app` y `gps-mocker-upload`. El Base64 se obtiene del archivo binario; no lo pegues en issues, commits ni mensajes. La integración de esta sesión no ha configurado esos secretos.

## Entregas firmadas

Integra primero el código validado en `main`. Ejecuta **Actions → Preparar entrega firmada** desde `main`. Otros refs no ejecutan la entrega. El primer job prueba y genera APK/AAB sin claves. El segundo firma los binarios del mismo workflow; no ejecuta Gradle con secretos presentes. Fallará si faltan claves, si el paquete es depurable o si la huella del certificado no coincide con la esperada.

El artefacto `gps-mocker-signed-N` contiene APK instalable, AAB y `release-metadata.json` con versión, commit, certificados públicos y SHA-256 de archivos. Los artefactos `unsigned-intermediate` no son una entrega instalable. Aumenta `versionCode` para cada publicación.

En Play App Signing, utiliza la clave **app** existente para conservar compatibilidad con APK directos. Sigue el procedimiento de Google para importar esa clave usando su herramienta PEPK y su clave de cifrado; no elijas por defecto una identidad nueva generada por Google. Registra **upload** como certificado de subida del AAB. Documentación: https://developer.android.com/studio/publish/app-signing .

Antes de considerar cerrada la transición, comprueba dos versiones consecutivas estables en el teléfono mediante actualización sobre la anterior y verifica que todos los datos permanezcan. Esta prueba física aún está pendiente.
