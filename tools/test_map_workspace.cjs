const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

test('map position patches preserve route and never rebuild GeoJSON', () => {
  let writes = 0, geometry;
  const events = {}, sources = new Map();
  class FakeMap {
    constructor() { this.touchZoomRotate = {disableRotation(){}}; }
    isStyleLoaded() { return true; }
    getSource(id) { return sources.get(id); }
    addSource(id, value) { geometry = value.data; sources.set(id, {setData(data){writes++;geometry=data;}}); }
    getStyle() { return {layers:[]}; }
    addLayer() {}
    on(name, callback) { events[name] = callback; }
    setStyle() { sources.clear(); }
    easeTo() {}
    jumpTo() {}
  }
  class Marker {
    on() { return this; }
    setLngLat() { return this; }
    addTo() { return this; }
    remove() { return this; }
  }
  const element = () => ({addEventListener(){},style:{},hidden:true});
  const context = {window:{maplibregl:{Map:FakeMap,Marker},GpsMocker:{initialState:()=>JSON.stringify({
    points:[[0,0],[1,1]],selection:[0,0],style:'light'
  }),onReady(){}}},document:{getElementById:element,createElement:element},location:{reload(){}}};
  context.maplibregl = context.window.maplibregl;
  vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../app/src/main/assets/map/workspace.js'),'utf8'),context);
  const initialWrites = writes;
  for(let i=0;i<100;i++) context.window.GpsMap.render({active:[i / 100, 0]});
  assert.equal(writes,initialWrites);
  assert.equal(geometry.features[0].geometry.coordinates.length,2);
  context.window.GpsMap.render({points:[[0,0],[2,2],[3,3]],editing:true});
  assert.equal(geometry.features[0].geometry.coordinates.length,3);
  context.window.GpsMap.render({style:'dark'});
  events['style.load']();
  assert.equal(geometry.features[0].geometry.coordinates.length,3);
});
