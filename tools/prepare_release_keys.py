#!/usr/bin/env python3
"""Run on the owner's computer. Creates persistent keys; optionally configures GitHub from stdin."""
import argparse
import base64
import getpass
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess


def run(argv, **kwargs):
    return subprocess.run(argv, check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, **kwargs).stdout


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--directory', required=True, type=Path, help='New directory on your computer, outside the repository')
    p.add_argument('--github', action='store_true', help='Configure the release environment using your authenticated gh CLI')
    p.add_argument('--repo', default='al2242000122/gps')
    args = p.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+',args.repo):
        raise ValueError('Repositorio inválido')
    directory=args.directory.resolve()
    if directory.is_relative_to(Path(__file__).resolve().parents[1]):raise ValueError("Guarda las claves fuera del repositorio.")
    if directory.exists():raise ValueError('El directorio ya existe. Nunca se sobrescriben claves existentes.')
    password=getpass.getpass('Contraseña para las dos claves (mínimo 12 caracteres): ')
    if len(password)<12 or password!=getpass.getpass('Repite la contraseña: '):raise ValueError('Contraseña demasiado corta o distinta')
    directory.mkdir(parents=True,mode=0o700)
    env=os.environ.copy();env['GPS_KEY_PASSWORD']=password
    certs={}
    for kind in ('app','upload'):
        alias='gps-mocker-'+kind;store=directory/(kind+'.jks')
        run(['keytool','-genkeypair','-keystore',str(store),'-storetype','JKS','-alias',alias,
             '-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=GPS Mocker '+kind,
             '-storepass:env','GPS_KEY_PASSWORD','-keypass:env','GPS_KEY_PASSWORD'],env=env)
        store.chmod(0o600)
        cert=run(['keytool','-exportcert','-keystore',str(store),'-alias',alias,'-storepass:env','GPS_KEY_PASSWORD'],env=env)
        (directory/(kind+'.cer')).write_bytes(cert)
        certs[kind]=hashlib.sha256(cert).hexdigest()
    (directory/'public-fingerprints.json').write_text(json.dumps(certs,indent=2))
    print('Claves creadas en',directory)
    print('Guarda una segunda copia cifrada y la contraseña en un gestor antes de distribuir un APK.')
    if args.github:
        run(['gh','api','--method','PUT','repos/'+args.repo+'/environments/release'])
        for kind,prefix in (('app','ANDROID'),('upload','UPLOAD')):
            values={prefix+'_KEYSTORE_BASE64':base64.b64encode((directory/(kind+'.jks')).read_bytes()).decode(),
                    prefix+'_KEYSTORE_PASSWORD':password,prefix+'_KEY_ALIAS':'gps-mocker-'+kind,prefix+'_KEY_PASSWORD':password}
            for name,value in values.items():run(['gh','secret','set',name,'--repo',args.repo,'--env','release'],input=value.encode())
            run(['gh','variable','set',prefix+'_CERT_SHA256','--repo',args.repo,'--env','release','--body',certs[kind]])
        print('Secretos configurados en el entorno release. No se mostraron claves ni contraseñas.')


if __name__=='__main__':
    try:main()
    except subprocess.CalledProcessError:
        raise SystemExit('Falló keytool o GitHub. Conserva los archivos creados y revisa los permisos. No se mostraron salidas que pudieran incluir credenciales.')
    except (OSError,ValueError) as e:raise SystemExit(str(e))
