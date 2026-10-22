#!/usr/bin/env python3
"""Sign already-built APK/AAB. Gradle must never run while signing secrets are present."""
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def run(argv):
    return subprocess.run(argv,check=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE).stdout.decode()


def required(name):
    value=os.environ.get(name,'')
    if not value:raise ValueError('Falta configuración de firma: '+name)
    return value


OUTPUT_CREATED = False

def main():
    global OUTPUT_CREATED
    for prefix in ('ANDROID','UPLOAD'):
        for suffix in ('KEYSTORE_BASE64','KEYSTORE_PASSWORD','KEY_ALIAS','KEY_PASSWORD','CERT_SHA256'):required(prefix+'_'+suffix)
    source=Path(os.environ.get('UNSIGNED_DIR','unsigned'));dest=Path(os.environ.get('SIGNED_DIR','signed'));dest.mkdir(exist_ok=False);OUTPUT_CREATED=True
    sdk=Path(required('ANDROID_HOME'))/'build-tools/36.0.0'
    apks=list(source.rglob('*-unsigned.apk'));aabs=list(source.rglob('*.aab'))
    if len(apks)!=1 or len(aabs)!=1:raise ValueError('Se necesita exactamente un APK sin firma y un AAB.')
    badging=run([str(sdk/'aapt'),'dump','badging',str(apks[0])])
    match=re.search(r"package: name='com.diegoca.gpsmocker' versionCode='(\d+)' versionName='([^']+)'",badging)
    if not match or 'application-debuggable' in badging:raise ValueError('Paquete de producción inválido o depurable.')
    apk=dest/'gps-mocker-release.apk';aab=dest/'gps-mocker-release.aab'
    with tempfile.TemporaryDirectory(prefix='gps-sign-') as temporary:
        stores={}
        for prefix in ('ANDROID','UPLOAD'):
            store=Path(temporary)/(prefix+'.jks');store.write_bytes(base64.b64decode(required(prefix+'_KEYSTORE_BASE64'),validate=True));store.chmod(0o600);stores[prefix]=store
        run([str(sdk/'apksigner'),'sign','--ks',str(stores['ANDROID']),'--ks-key-alias',required('ANDROID_KEY_ALIAS'),
             '--ks-pass','env:ANDROID_KEYSTORE_PASSWORD','--key-pass','env:ANDROID_KEY_PASSWORD','--out',str(apk),str(apks[0])])
        verify=run([str(sdk/'apksigner'),'verify','--verbose','--print-certs',str(apk)])
        actual=re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)',verify)
        expected=re.sub('[^0-9a-f]','',required('ANDROID_CERT_SHA256').lower())
        if not actual or actual.group(1).lower()!=expected:raise ValueError('La firma APK no coincide con la huella esperada.')
        shutil.copyfile(aabs[0],aab)
        run(['jarsigner','-keystore',str(stores['UPLOAD']),'-storepass:env','UPLOAD_KEYSTORE_PASSWORD','-keypass:env','UPLOAD_KEY_PASSWORD',
             '-sigalg','SHA256withRSA','-digestalg','SHA-256',str(aab),required('UPLOAD_KEY_ALIAS')])
        run(['jarsigner','-verify',str(aab)])
        cert=run(['keytool','-J-Duser.language=en','-printcert','-jarfile',str(aab)])
        upload=re.search(r'SHA256: ([0-9A-Fa-f:]+)',cert)
        expected_upload=re.sub('[^0-9a-f]','',required('UPLOAD_CERT_SHA256').lower())
        if not upload or upload.group(1).replace(':','').lower()!=expected_upload:raise ValueError('La firma AAB no coincide con la clave de subida esperada.')
    metadata={'package':'com.diegoca.gpsmocker','versionCode':int(match.group(1)),'versionName':match.group(2),
              'commit':os.environ.get('GITHUB_SHA','local'),'apkCertificateSha256':expected,'aabUploadCertificateSha256':expected_upload,
              'files':{f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in (apk,aab)}}
    (dest/'release-metadata.json').write_text(json.dumps(metadata,indent=2)+'\n')
    print('APK y AAB firmados y certificados verificados; metadatos públicos generados.')


if __name__=='__main__':
    try:main()
    except (Exception,) as e:
        # Never emit tool output, key material or subprocess arguments containing secrets.
        dest=Path(os.environ.get('SIGNED_DIR','signed'))
        if OUTPUT_CREATED and dest.exists():shutil.rmtree(dest)
        if isinstance(e,ValueError):raise SystemExit(str(e))
        raise SystemExit('La firma falló. No se publicaron artefactos. Revisa claves, contraseñas y configuración.')
