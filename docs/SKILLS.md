# Skills evaluadas para GPS Mocker

Investigación: 21 de octubre de 2026, siguiendo [find-skills](https://github.com/vercel-labs/skills/tree/main/skills/find-skills).

Contexto: Android nativo con Kotlin y vistas XML/Material Components, MapLibre GL JS 4.7.1 dentro de WebView, pruebas JUnit/Robolectric y compilación mediante GitHub Actions. Relación con el trabajo previsto: [PENDIENTES.md](PENDIENTES.md).

## Cómo se seleccionaron

Se consultó primero el ranking de [skills.sh](https://skills.sh/). La búsqueda CLI `npx --yes skills find 'android testing'` no devolvió resultados en este entorno; se continuó con el buscador HTTP del catálogo. Se buscaron Android, pruebas, Kotlin, GitHub Actions, Play Store, accesibilidad, MapLibre, GPX, firma, respaldo y AdMob.

Se comprobó la existencia del repositorio y del `SKILL.md`, su contenido y encaje técnico, las instalaciones del catálogo y las estrellas del repositorio. Los contadores son una captura de la consulta, no garantías de calidad; las estrellas corresponden al repositorio completo. No se ejecutaron scripts ni auditorías de las candidatas durante esta búsqueda.

## Prioritarias para las próximas entregas

| Skill y fuente | Instalaciones observadas | Aplicación concreta y límites |
| --- | ---: | --- |
| [testing-setup](https://skills.sh/android/skills/testing-setup), `android/skills` | 2 336 | Revisar la estrategia existente y añadir pruebas instrumentadas de permisos, sistema y restauración. Contempla Views y Robolectric. La guía también propone cambios amplios de infraestructura; su aplicación al proyecto debe acotarse al trabajo necesario y conservar la base existente. |
| [android-permissions-security](https://skills.sh/android/skills/android-permissions-security), `android/skills` | 384 | Revisar permisos de ubicación, denegación permanente, componentes exportados, receptores y URI de importación/exportación. Menor adopción, pero procede del repositorio de Android y declara Google LLC como autor. |
| [android-profiler](https://skills.sh/android/skills/android-profiler), `android/skills` | 926 | Medir CPU, memoria, arranque, batería y bloqueos de CPU del servicio y el mapa. Requiere preparar las herramientas y disponer de dispositivo o trazas; no se sustituye una medición por una estimación. |
| [github-actions-hardening](https://skills.sh/github/awesome-copilot/github-actions-hardening), `github/awesome-copilot` | 2 080 | Revisar el flujo que manejará claves de firma, permisos de CI, acciones externas y artefactos. Es una skill de revisión; no configura por sí sola la firma Android. |
| [maplibre-cartography](https://skills.sh/maplibre/maplibre-agent-skills/maplibre-cartography), `maplibre/maplibre-agent-skills` | 385 | Contraste, etiquetas, marcadores y orden de capas de rutas. Encaja directamente con el motor GL JS dentro del WebView. Repositorio de la organización MapLibre mantenido por la comunidad; adopción aún reducida. Contrastar ejemplos con la versión 4.7.1 del proyecto. |

Fuentes leídas:

- [testing-setup/SKILL.md](https://github.com/android/skills/blob/main/testing/testing-setup/SKILL.md)
- [android-permissions-security/SKILL.md](https://github.com/android/skills/blob/main/security/android-permissions-security/SKILL.md)
- [android-profiler/SKILL.md](https://github.com/android/skills/blob/main/profilers/android-profiler/SKILL.md)
- [github-actions-hardening/SKILL.md](https://github.com/github/awesome-copilot/blob/main/skills/github-actions-hardening/SKILL.md)
- [maplibre-cartography/SKILL.md](https://github.com/maplibre/maplibre-agent-skills/blob/main/skills/maplibre-cartography/SKILL.md)

Comandos de instalación para cuando se trabaje en esas áreas:

```bash
npx skills add android/skills@testing-setup
npx skills add android/skills@android-permissions-security
npx skills add android/skills@android-profiler
npx skills add github/awesome-copilot@github-actions-hardening
npx skills add maplibre/maplibre-agent-skills@maplibre-cartography
```

## Para publicación y ampliaciones posteriores

| Skill y fuente | Instalaciones observadas | Cuándo aporta |
| --- | ---: | --- |
| [play-policy-insights](https://skills.sh/android/skills/play-policy-insights), `android/skills` | 1 204 | Antes de presentar la app a Play Store: contrastar código, permisos y declaraciones de datos. Incluye scripts y un flujo de auditoría que deben prepararse y ejecutarse como tarea propia. |
| [aso](https://skills.sh/coreyhaines31/marketingskills/aso), `coreyhaines31/marketingskills` | 73 293 | Preparar nombre, descripción y capturas para Google Play; auditar conversión y visibilidad cuando exista la ficha. No promete instalaciones ni ingresos. |
| [maplibre-tile-sources](https://skills.sh/maplibre/maplibre-agent-skills/maplibre-tile-sources), `maplibre/maplibre-agent-skills` | 959 | Elegir fuentes y representar recorridos grandes manteniendo separados el mapa base y los datos de rutas. No es un motor para calcular recorridos por calles. |
| [github-actions-templates](https://skills.sh/wshobson/agents/github-actions-templates), `wshobson/agents` | 17 286 | Referencia general para artefactos, matrices, caché y automatización de entregas. Sus ejemplos son multistack; adaptar al flujo Gradle existente y verificar las versiones vigentes. |

Fuentes leídas:

- [play-policy-insights/SKILL.md](https://github.com/android/skills/blob/main/play/play-policy-insights/SKILL.md)
- [aso/SKILL.md](https://github.com/coreyhaines31/marketingskills/blob/main/skills/aso/SKILL.md)
- [maplibre-tile-sources/SKILL.md](https://github.com/maplibre/maplibre-agent-skills/blob/main/skills/maplibre-tile-sources/SKILL.md)
- [github-actions-templates/SKILL.md](https://github.com/wshobson/agents/blob/main/plugins/cicd-automation/skills/github-actions-templates/SKILL.md)

```bash
npx skills add android/skills@play-policy-insights
npx skills add coreyhaines31/marketingskills@aso
npx skills add maplibre/maplibre-agent-skills@maplibre-tile-sources
npx skills add wshobson/agents@github-actions-templates
```

## Guía de diseño ya utilizada

[mobile-android-design](https://skills.sh/wshobson/agents/mobile-android-design), de `wshobson/agents`: **24 646 instalaciones** observadas. Ya se utilizó como guía del rediseño Material 3; las decisiones están en [DESIGN.md](DESIGN.md). Para este proyecto se toman sus principios de contraste, accesibilidad y disposición adaptable, manteniendo las vistas XML existentes. Sus ejemplos Compose no implican una migración de la app.

Fuente: [mobile-android-design/SKILL.md](https://github.com/wshobson/agents/blob/main/plugins/ui-design/skills/mobile-android-design/SKILL.md).

```bash
npx skills add wshobson/agents@mobile-android-design
```

## Reputación de las fuentes consultadas

| Repositorio | Estrellas observadas | Procedencia |
| --- | ---: | --- |
| [android/skills](https://github.com/android/skills) | 9 130 | Organización Android; las guías seleccionadas declaran Google LLC como autor. |
| [github/awesome-copilot](https://github.com/github/awesome-copilot) | 40 276 | Repositorio de GitHub con contribuciones de la comunidad. |
| [maplibre/maplibre-agent-skills](https://github.com/maplibre/maplibre-agent-skills) | 155 | Organización MapLibre; guías mantenidas por la comunidad. |
| [wshobson/agents](https://github.com/wshobson/agents) | 41 438 | Colección comunitaria; fuente de la guía de diseño ya utilizada. |
| [coreyhaines31/marketingskills](https://github.com/coreyhaines31/marketingskills) | 55 799 | Colección comunitaria centrada en marketing. |

## Evaluadas sin prioridad de adopción

- [android-clean-architecture](https://skills.sh/affaan-m/ecc/android-clean-architecture): 11 820 instalaciones; repositorio [affaan-m/ECC](https://github.com/affaan-m/ECC), con 285 427 estrellas observadas. Se leyó su guía de capas y repositorios. Puede servir si crece la complejidad de persistencia o rutas; no justifica dividir ahora esta app en numerosos módulos ni incorporar Hilt, Room o Ktor sin una necesidad concreta.
- Las búsquedas de firma devolvieron principalmente Expo, Capacitor o publicación para Solana Mobile. No se seleccionaron como guías de firma para este proyecto Kotlin/Gradle. Utilizar la [documentación de firma Android](https://developer.android.com/studio/publish/app-signing).
- Las skills de respaldo encontradas tenían poca adopción o estaban orientadas a otros stacks. Utilizar la [documentación de Storage Access Framework](https://developer.android.com/training/data-storage/shared/documents-files) y pruebas del formato de respaldo.
- Las skills específicas de AdMob encontradas tenían entre 1 y 13 instalaciones y varias correspondían a Flutter, Expo, React Native o Godot. Se priorizarán la [documentación oficial de Google Mobile Ads para Android](https://developers.google.com/admob/android/quick-start) y [UMP](https://developers.google.com/admob/android/privacy).

## Estado

La investigación y su selección están documentadas. Las nuevas candidatas quedan evaluadas para los pendientes correspondientes; esta tarea no las instala ni ejecuta cambios de arquitectura, auditorías, firma, anuncios o publicación. `find-skills` se utilizó para la búsqueda y `mobile-android-design` ya se había aplicado al diseño.

## Aplicación en 0.4.0

- `testing-setup`: se respetaron JUnit/Robolectric y Views existentes, ampliando pruebas de persistencia, corrupción, restauración y rutas. No se incorporaron Hilt/Compose ni una migración de arquitectura innecesaria. Protocolo: [PRUEBAS.md](PRUEBAS.md).
- `android-permissions-security`: revisión de SAF, componentes privados, permisos en tiempo de uso y recuperación; se excluyeron Advertising ID y estado básico del teléfono del manifiesto combinado.
- `github-actions-hardening`: revisión de triggers, permisos, SHA y manejo de claves; informe y correcciones en [CI_REVIEW.md](CI_REVIEW.md).
- `maplibre-cartography`: recorrido azul con borde claro por debajo de las etiquetas y atribución enlazada del proveedor. Se conservaron el estilo claro y los controles actuales.
- `mobile-android-design`: continuidad del diseño Material 3, espacio inferior para el banner, acciones de respaldo y privacidad e icono adaptativo.
- `play-policy-insights`: se ejecutaron init, siete objetivos, verificación crítica y generación de informe siguiendo el protocolo. Resultado Needs Review, con declaración FGS y formularios previos a publicación pendientes. No existía ficha Play para contrastar declaraciones. Los falsos positivos de audio/fitness por nombres `record`/`track` se revisaron; el formulario de datos requiere contrastar además proveedores y SDK reales.
- `aso`: se aplicaron límites de nombre/descripciones y una presentación centrada en funciones verificables a la ficha propuesta en [PUBLICACION.md](PUBLICACION.md). No se presentó una auditoría de conversión inexistente.
- `android-profiler`: guía revisada; medición y análisis de trazas pendientes de dispositivo conectado. Se preparó el protocolo, sin atribuirle mediciones que no se ejecutaron.

El informe automatizado íntegro de Play queda en el workspace de la auditoría (`.scratch/play_policy_insights_40ed8f12-b0e4-4eb6-add0-f41ab6d64fd3/compliance_report.md`). La carpeta se excluye de Git; las conclusiones y limitaciones relevantes se recogen en la documentación de publicación.
