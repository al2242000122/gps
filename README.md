# GPS Mocker Android

Aplicación Android en Kotlin para simular coordenadas durante pruebas legítimas de desarrollo.

## Requisitos

- Android Studio con JDK 17 o superior.
- Android SDK Platform 34.
- Un dispositivo o emulador con Android 8.0 o superior.

## Primer uso

1. Abre el proyecto en Android Studio y espera la sincronización de Gradle.
2. Instala la aplicación en el dispositivo.
3. Activa las opciones de desarrollador.
4. En **Aplicación de ubicación simulada**, selecciona **GPS Mocker**.
5. Ingresa latitud y longitud y pulsa **Aplicar ubicación**.

## Alcance de la versión 0.1

- Validación de latitud y longitud.
- Aplicación de una posición GPS simulada.
- Detención y restauración del proveedor GPS.
- Acceso directo a las opciones de desarrollador.

Android marca estas posiciones como simuladas. El proyecto no contiene mecanismos para ocultarlas ni para evadir controles de aplicaciones de terceros.

## Compilación

```bash
./gradlew assembleDebug
```

El APK se genera en `app/build/outputs/apk/debug/app-debug.apk`.
