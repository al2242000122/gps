# Diseño Figma y adaptación Android

Referencia editable: https://www.figma.com/design/HYUIdmR8hCzg2x0H2btEcg?node-id=3-54

Se reutilizan Kotlin, XML, Material 3, recursos de tema y los vectores originales. No se añade una segunda plataforma de UI ni imágenes de pantallas. El mapa de Figma es un esquema: Android conserva MapLibre, gestos, marcadores, rutas y atribuciones enlazadas.

| Vista de referencia | Comportamiento Android |
| --- | --- |
| Selección, `5:1401` | Azul `#1F63D7` con texto blanco (contraste 5,49:1), selección identificada, coordenadas copiables, favoritos y compartir. |
| Coordenadas, `5:1492` | Entrada manual con punto/coma, error por campo, validación de límites y borrador al recrear; seleccionar no inicia ni actualiza la simulación. |
| Ruta activa, `5:1545` | Progreso existente, posición simulada separada de la selección y botones con texto para pausar/reanudar y detener. Se conserva el destino al terminar hasta detener. |
| Guardados, `6:52` | Se conservan favoritos, historial, selección, renombrado, borrado y confirmación. Los guardados se confirman después de persistir. |
| Restauración, `6:100` | Fecha y conteos, reemplazo completo explícito, cancelación y exportación de datos actuales antes de volver a importar. |
| Preparación, `6:157` | Tres comprobaciones reales, actualización explícita del estado y acceso al ajuste de ubicación simulada. No inicia un servicio al comprobar. |

La composición se adapta al mapa de pantalla completa existente: utiliza tarjetas y hojas desplazables. No es una reproducción píxel por píxel de las seis mesas de trabajo. En horizontal, la tarjeta se sitúa al lado del mapa. Con fuente superior al 130 %, los accesos secundarios conservan texto y retiran iconos redundantes para disponer de más ancho; sus objetivos siguen siendo de 48 dp como mínimo.

Las capturas Robolectric de CI permiten revisar temas claro/oscuro, horizontal y fuente 200 %. Su mapa no carga red. No sustituyen las pruebas de TalkBack, teclado, WebGL y escala del sistema en dispositivos reales descritas en PRUEBAS.md.
