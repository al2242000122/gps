# Diseño de GPS Mocker 0.3

Referencia: pantalla de mapa, marcador azul, controles flotantes y panel inferior aportada por el usuario.

Guía aplicada: [mobile-android-design](https://github.com/wshobson/agents/tree/main/plugins/ui-design/skills/mobile-android-design), localizada siguiendo [find-skills](https://github.com/vercel-labs/skills/tree/main/skills/find-skills). La guía se instaló y leyó durante el trabajo; no se añade como dependencia de la app.

## Estructura visual

- El mapa ocupa toda la pantalla. MapLibre y la cartografía vectorial de OpenFreeMap reemplazan las teselas estándar, con zoom suave y estilos Liberty y Dark.
- El buscador superior es una superficie flotante con identidad discreta y acceso a ajustes. Buscar un lugar lleva su selección al mapa.
- La tarjeta inferior presenta estado, nombre/dirección, coordenadas de solo lectura y la acción principal. Los campos editables de latitud y longitud se eliminan.
- El botón principal azul destaca Comenzar, Actualizar o Iniciar ruta según el contexto. Detener y Pausar aparecen cuando son relevantes.
- Guardados, Rutas y Compartir se mantienen visibles. Sus opciones y ajustes secundarios se abren en hojas o diálogos Material.
- El editor de rutas utiliza un aviso independiente y controles de deshacer/terminar; los puntos de ruta tienen marcadores numerados.

## Sistema de diseño

- Material Design 3 mediante Material Components y tema DayNight; se conservan las vistas Android para integrar el mapa nativo existente.
- Acento azul `#2878F5`, texto principal oscuro, superficies claras y bordes redondeados. Paleta nocturna con contraste y colores Material You opcionales.
- Título principal 19 sp, texto de cuerpo 13–16 sp, coordenadas monoespaciadas de 14 sp. La tipografía respeta el escalado del sistema.
- Objetivos de pulsación principales de al menos 48 dp, con descripciones de accesibilidad en botones que solo tienen icono.
- El estado se comunica con texto, no solo con color. El anuncio accesible del estado se actualiza cuando cambia su contenido, evitando repetirlo en cada posición de la ruta.
- El diseño respeta barras del sistema y recortes. En horizontal ancho, la tarjeta pasa al lateral y deja el buscador y el mapa utilizables.

## Interacción y estado

- Elegir un punto no inicia una simulación automáticamente. Comenzar/Actualizar confirma la intención.
- Favoritos e historial conservan ubicaciones reutilizables; una importación inválida no destruye la ruta previa.
- La velocidad depende del tiempo transcurrido. Pausar conserva la posición; reanudar no acumula el tiempo pausado. Completar mantiene el destino hasta Detener.
- Selección, nombre, dirección, favoritos, historial, ruta y ajustes se guardan localmente. La vista del mapa y el modo de edición sobreviven a la recreación de la pantalla.
- Los errores de búsqueda o red no detienen una simulación activa. El acceso a configuración solicita desplazamiento a `mock_location_app`; la guía contempla fabricantes que ignoran ese argumento.

## Verificación visual

Robolectric con renderizado nativo genera vistas previas de teléfono, tema oscuro y horizontal en `app/build/outputs/previews`. Se verifica el tamaño de la acción principal y la disposición lateral. Las vistas previas muestran los controles nativos, con el WebView sin descargas. El componente de mapa vectorial se comprueba por separado con su HTML, motor y estilos reales.
