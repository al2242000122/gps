import base64
import hashlib
import importlib.util
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

@unittest.skipUnless(os.environ.get('GPS_TEST_SIGNING')=='true','Set GPS_TEST_SIGNING=true after building release APK/AAB')
class SigningIntegrationTest(unittest.TestCase):
    def test_signs_apk_and_bundle_and_rejects_wrong_certificate(self):
        root=Path(__file__).resolve().parents[1]
        import sys,json
        with tempfile.TemporaryDirectory(prefix='gps-test-sign-') as temp:
            temp=Path(temp);env=os.environ.copy()
            # Ephemeral test keys are deliberately distinct from persistent production keys.
            env['GPS_TEST_PASSWORD']='synthetic-test-password'
            for prefix in ('ANDROID','UPLOAD'):
                store=temp/(prefix+'.jks')
                subprocess.run(['keytool','-genkeypair','-keystore',str(store),'-storetype','JKS','-alias','test','-keyalg','RSA','-keysize','2048','-validity','2','-dname','CN=GPS TEST ONLY',
                                '-storepass:env','GPS_TEST_PASSWORD','-keypass:env','GPS_TEST_PASSWORD'],env=env,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
                cert=subprocess.run(['keytool','-exportcert','-keystore',str(store),'-alias','test','-storepass:env','GPS_TEST_PASSWORD'],env=env,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE).stdout
                env.update({prefix+'_KEYSTORE_BASE64':base64.b64encode(store.read_bytes()).decode(),prefix+'_KEYSTORE_PASSWORD':env['GPS_TEST_PASSWORD'],
                            prefix+'_KEY_PASSWORD':env['GPS_TEST_PASSWORD'],prefix+'_KEY_ALIAS':'test',prefix+'_CERT_SHA256':hashlib.sha256(cert).hexdigest()})
            source=temp/'unsigned';source.mkdir()
            import shutil
            shutil.copyfile(root/'app/build/outputs/apk/release/app-release-unsigned.apk',source/'app-release-unsigned.apk')
            shutil.copyfile(root/'app/build/outputs/bundle/release/app-release.aab',source/'app-release.aab')
            env['UNSIGNED_DIR']=str(source);env['SIGNED_DIR']=str(temp/'signed')
            script=[sys.executable,str(root/'tools/sign_release.py')]
            result=subprocess.run(script,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            self.assertEqual(0,result.returncode,result.stdout.decode()+result.stderr.decode())
            metadata=json.loads((temp/'signed/release-metadata.json').read_text())
            self.assertEqual('com.diegoca.gpsmocker',metadata['package']);self.assertEqual(env['ANDROID_CERT_SHA256'],metadata['apkCertificateSha256'])
            self.assertNotEqual(metadata['apkCertificateSha256'],metadata['aabUploadCertificateSha256'])
            env['SIGNED_DIR']=str(temp/'bad-signature');env['ANDROID_CERT_SHA256']='0'*64
            result=subprocess.run(script,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            self.assertNotEqual(0,result.returncode);self.assertFalse((temp/'bad-signature').exists())
            self.assertNotIn(env['GPS_TEST_PASSWORD'].encode(),result.stdout+result.stderr)
