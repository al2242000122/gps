import json
import unittest
from rescue_legacy_backup import convert, preferences, check_legacy_version

class RescueTest(unittest.TestCase):
    def fixture(self):
        return {'preferences':b'<map><string name="route">[[19.0,-99.0]]</string><string name="speed">8</string><boolean name="dynamic_color" value="true"/></map>',
                'places':'<map><string name="favorites">[{"id":"id","name":"Café 🌎","lat":19,"lon":-99,"time":1}]</string></map>'.encode(),
                'simulation':b'<map><string name="latitude">19</string><string name="longitude">-99</string><string name="selection_name">Casa</string><boolean name="cleanup_needed" value="false"/></map>'}
    def test_preserves_fields_and_excludes_runtime(self):
        doc=json.loads(convert(self.fixture(),12345));data=doc['data']
        self.assertEqual('Café 🌎',data['favorites'][0]['name']);self.assertEqual(1,len(data['route']['points']))
        self.assertEqual([19.0,-99.0],data['selection']['point']);self.assertTrue(data['settings']['dynamicColor'])
        self.assertNotIn('cleanup_needed',str(doc));self.assertEqual(1,doc['schemaVersion'])
    def test_cleanup_or_incomplete_selection_blocks_rescue(self):
        raw=self.fixture();raw['simulation']=b'<map><boolean name="cleanup_needed" value="true"/></map>'
        with self.assertRaises(ValueError):convert(raw,1)
        raw['simulation']=b'<map><string name="latitude">19</string></map>'
        with self.assertRaises(ValueError):convert(raw,1)
    def test_xml_entities_and_invalid_booleans_are_rejected(self):
        for raw in (b'<!DOCTYPE map><map/>',b'<map><boolean name="a" value="TRUE"/></map>'):
            with self.assertRaises(ValueError):preferences(raw)
    def test_only_supported_legacy_versions_are_extracted(self):
        for code in (3,4):check_legacy_version('versionCode='+str(code)+' minSdk=26')
        for value in ('versionCode=5','versionCode=1','unknown'):
            with self.assertRaises(RuntimeError):check_legacy_version(value)
    def test_write_fixture_for_android_codec(self):
        import os
        path=os.environ.get('GPS_RESCUE_FIXTURE')
        if path:
            from pathlib import Path
            Path(path).write_bytes(convert(self.fixture(),12345))

if __name__=='__main__':unittest.main()
