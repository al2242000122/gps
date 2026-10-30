const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const root = path.join(__dirname, '..');

test('bundled map files match the reviewed integrity manifest', () => {
  const manifest = JSON.parse(fs.readFileSync(path.join(root, 'app/src/main/assets/map/vendor-integrity.json')));
  for (const [name, expected] of Object.entries(manifest.sha256)) {
    const bytes = fs.readFileSync(path.join(root, 'app/src/main/assets/map', name));
    assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'), expected, name);
  }
});

test('real bundled attribution control never interprets provider HTML', () => {
  const { AttributionControl } = require('../app/src/main/assets/map/maplibre-gl.js');
  const payload = '<details open onload="bad()" ontoggle="bad()">Provider</details>';
  const control = new AttributionControl({ customAttribution: payload });
  let plain = '';
  let htmlWrites = 0;
  control._innerContainer = {
    set innerHTML(value) { htmlWrites++; },
    set textContent(value) { plain = value; }
  };
  control._container = { classList: { remove() {}, add() {} } };
  control._map = { style: { sourceCaches: {} } };
  control._updateCompact = () => {};
  control._updateAttributions();
  assert.equal(htmlWrites, 0);
  assert.equal(plain, payload);
  control._map.style.sourceCaches.provider = { used: true, getSource: () => ({ attribution: payload + ' Tile' }) };
  control._updateAttributions();
  assert.equal(htmlWrites, 0);
  assert.ok(plain.includes('Tile'));
});
