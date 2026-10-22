(() => {
  'use strict';
  const bridge = window.GpsMocker;
  const error = document.getElementById('map-error');
  document.getElementById('retry').addEventListener('click', () => location.reload());
  if (!window.maplibregl) {
    error.hidden = false;
    if (bridge) bridge.onError();
    return;
  }
  let state = bridge ? JSON.parse(bridge.initialState()) : {};
  let map;
  try { map = new maplibregl.Map({
    container: 'map', style: state.style || 'https://tiles.openfreemap.org/styles/liberty',
    center: state.center || [-99.133209, 19.432608], zoom: state.zoom || 15,
    minZoom: 2, maxZoom: 19, attributionControl: false, pitchWithRotate: false,
    dragRotate: false, renderWorldCopies: true, fadeDuration: 150
  }); } catch (_) { error.hidden = false; if (bridge) bridge.onError(); return; }
  map.touchZoomRotate.disableRotation();
  const pin = document.createElement('div');
  pin.className = 'selected-pin';
  pin.innerHTML = '<svg viewBox="0 0 40 48" aria-hidden="true"><path d="M20 2C10.6 2 3 9.6 3 19c0 12 17 26 17 26s17-14 17-26C37 9.6 29.4 2 20 2Z" fill="#2878f5" stroke="white" stroke-width="2"/><circle cx="20" cy="19" r="7" fill="white"/></svg>';
  const selected = new maplibregl.Marker({ element: pin, anchor: 'bottom', draggable: true });
  selected.on('dragend', () => { const p = selected.getLngLat(); if (bridge) bridge.onSelect(p.lat, p.lng); });
  pin.addEventListener('click', e => e.stopPropagation());
  const dot = document.createElement('div'); dot.className = 'live-dot';
  const active = new maplibregl.Marker({ element: dot });
  let routeMarkers = [];
  let routeKey = '';
  let currentStyle = state.style;

  function route() {
    if (!map.isStyleLoaded()) return;
    const points = state.points || [];
    const data = { type: 'FeatureCollection', features: points.length >= 2 ? [{ type: 'Feature', properties: {}, geometry: { type: 'LineString', coordinates: points } }] : [] };
    const source = map.getSource('gps-route');
    if (source) source.setData(data);
    else {
      map.addSource('gps-route', { type: 'geojson', data });
      const labels = map.getStyle().layers.find(layer => layer.type === 'symbol')?.id;
      map.addLayer({ id: 'gps-route-casing', type: 'line', source: 'gps-route', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#ffffff', 'line-width': 8, 'line-opacity': 0.9 } }, labels);
      map.addLayer({ id: 'gps-route', type: 'line', source: 'gps-route', layout: { 'line-cap': 'round', 'line-join': 'round' }, paint: { 'line-color': '#2878f5', 'line-width': 5, 'line-opacity': 0.85 } }, labels);
    }
    const key = JSON.stringify([points, !!state.editing]);
    if (key === routeKey) return;
    routeKey = key;
    routeMarkers.forEach(marker => marker.remove()); routeMarkers = [];
    const indexes = points.length <= 50 ? points.map((_, i) => i) : [0, points.length - 1];
    indexes.forEach(index => {
      const element = document.createElement('div'); element.className = 'waypoint'; element.textContent = String(index + 1);
      if (index > 998) element.style.fontSize = '9px';
      const marker = new maplibregl.Marker({ element, draggable: !!state.editing }).setLngLat(points[index]).addTo(map);
      marker.on('dragend', () => { const p = marker.getLngLat(); if (bridge) bridge.onMoveRoutePoint(index, p.lat, p.lng); });
      element.addEventListener('click', e => e.stopPropagation());
      routeMarkers.push(marker);
    });
  }

  function apply() {
    if (state.selection && !state.editing) selected.setLngLat(state.selection).addTo(map);
    else selected.remove();
    if (state.active) active.setLngLat(state.active).addTo(map);
    else active.remove();
    route();
  }

  window.GpsMap = { render(next) {
    state = next;
    if (state.style && state.style !== currentStyle) {
      currentStyle = state.style; map.setStyle(currentStyle);
    }
    if (state.camera) {
      const camera = { center: state.camera.center, zoom: state.camera.zoom, duration: 400 };
      if (state.camera.animate) map.easeTo(camera); else map.jumpTo(camera);
    }
    apply();
  } };
  map.on('click', e => { if (bridge) bridge.onSelect(e.lngLat.lat, e.lngLat.lng); });
  map.on('moveend', () => { const p = map.getCenter(); if (bridge) bridge.onCameraChanged(p.lat, p.lng, map.getZoom()); });
  map.on('style.load', () => { routeKey = ''; error.hidden = true; apply(); });
  map.on('load', () => { error.hidden = true; apply(); if (bridge) bridge.onReady(); });
  map.on('idle', () => { if (map.isStyleLoaded()) error.hidden = true; });
  map.on('error', () => { if (bridge) bridge.onError(); if (!map.isStyleLoaded()) error.hidden = false; });
  apply();
})();
