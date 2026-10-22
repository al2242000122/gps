# Validación de 0.4.0

Resultados del 22 de octubre de 2026. Build con JDK 21, SDK/target 36 y mínimo 26. Las pequeñas correcciones finales de textos y enlaces se validan además en el workflow del commit del PR.

| Comprobación | Resultado |
| --- | --- |
| JUnit/Robolectric | 111 casos, 0 fallos, 0 errores; fixture de rescate activado |
| APIs de controles principales | 26, 35 y 36; no representan mediciones físicas |
| Lint debug/release | Sin errores; quedan avisos de dependencias, textos y mejoras KTX/layout |
| Compilación | APK Dev, APK release sin firma y AAB generados |
| Rescate Python | 5 pruebas aprobadas; extracción real del teléfono pendiente |
| Firma | Integración con claves efímeras aprobada; APK/app y AAB/upload con certificados distintos, rechazo de huella incorrecta |
| Worker | 8 pruebas Node/Miniflare aprobadas, SQLite concurrente y persistente incluido |
| Despliegue del Worker | Bundle comprobado mediante dry-run; no desplegado |
| JavaScript del mapa / workflows | node --check y actionlint sin errores |
| Integridad Gradle | SHA-256 de Gradle 8.11.1 contrastado con el archivo descargado y fijado en wrapper |
| Bibliotecas nativas DataStore | Segmentos ELF alineados a 16 KiB en arm64, ARM32, x86 y x86_64 |
| Arte gráfico | Icono de 512×512 y gráfico de 1024×500 exportados e inspeccionados |

Las pruebas verifican corrupción sin borrado silencioso, fallo de escritura sin reemplazo parcial, guardas de restauración, SAF, rutas y circuitos, orden de coordenadas, geometrías complejas, cancelación de peticiones obsoletas antes de abrir otra conexión, atribución GPX y fallos/cuotas del proxy. Los archivos de rescate del usuario no se han leído: solo fixtures sintéticos.

La auditoría de Play mediante la skill `play-policy-insights` terminó con Needs Review: declaraciones FGS y privacidad pendientes y ficha todavía inexistente. No es una garantía de aprobación; la propuesta de formulario se contrasta manualmente con el SDK y proveedores reales en [PUBLICACION.md](PUBLICACION.md).

Quedan pendientes dispositivo físico, consumo de batería/CPU/memoria, restauración de los datos reales, dos actualizaciones estables, claves y cuentas del propietario, despliegue de servicios y pruebas de consentimiento con AdMob configurado. Estado completo en [PENDIENTES.md](PENDIENTES.md).
