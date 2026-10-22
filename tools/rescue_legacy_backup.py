#!/usr/bin/env python3
"""Rescue the old debuggable APK without uninstalling it. Python 3.10+, Android platform-tools."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = 'com.diegoca.gpsmocker'
MAX_BYTES = 2 * 1024 * 1024


def preferences(raw):
    if b'<!DOCTYPE' in raw.upper() or b'<!ENTITY' in raw.upper():
        raise ValueError('XML no permitido')
    root = ET.fromstring(raw)
    if root.tag != 'map':
        raise ValueError('No es un archivo de preferencias')
    out = {}
    for node in root:
        name = node.attrib['name']
        if name in out:
            raise ValueError('Preferencia duplicada')
        if node.tag == 'string':
            out[name] = node.text or ''
        elif node.tag == 'boolean':
            if node.attrib['value'] not in ('true', 'false'):
                raise ValueError('Valor lógico inválido')
            out[name] = node.attrib['value'] == 'true'
        elif node.tag in ('int', 'long', 'float'):
            out[name] = float(node.attrib['value']) if node.tag == 'float' else int(node.attrib['value'])
    return out


def convert(raw, exported_at):
    p, places, sim = [preferences(raw[name]) for name in ('preferences', 'places', 'simulation')]
    if sim.get('cleanup_needed', False):
        raise ValueError('Abre la app, pulsa Detener y restaura el GPS antes de continuar.')
    lat, lon = sim.get('latitude'), sim.get('longitude')
    if lat is None and lon is None:
        point = [19.432608, -99.133209]
    elif lat is None or lon is None:
        raise ValueError('La selección guardada está incompleta; conserva la instalación.')
    else:
        point = [float(lat), float(lon)]
    style = p.get('map_style', 'light')
    if style == 'auto' and not p.get('light_map_default_v1', False):
        style = 'light'
    data = {
        'favorites': json.loads(places.get('favorites', '[]')),
        'history': json.loads(places.get('history', '[]')),
        'settings': {'altitude': float(p.get('altitude', '0')), 'accuracy': p.get('accuracy', 3),
                     'interval': p.get('interval', 1000), 'dynamicColor': p.get('dynamic_color', False), 'mapStyle': style},
        'selection': {'point': point, 'name': sim.get('selection_name', 'Punto seleccionado'), 'address': sim.get('selection_address', '')},
        'route': {'points': json.loads(p.get('route', '[]')), 'speed': float(p.get('speed', '5')), 'loop': p.get('loop', False)}
    }
    result = {'format': 'gps-mocker-backup', 'schemaVersion': 1, 'exportedAt': exported_at, 'data': data}
    encoded = json.dumps(result, ensure_ascii=False, allow_nan=False, indent=2).encode('utf-8')
    if len(encoded) > MAX_BYTES:
        raise ValueError('El respaldo supera 2 MiB; conserva los XML y la instalación.')
    return encoded


def check_legacy_version(package_info):
    match = re.search(r'\bversionCode=(\d+)', package_info)
    if not match or int(match.group(1)) not in (3, 4):
        raise RuntimeError('El rescate USB admite los APK antiguos 0.3.x (códigos 3/4). En versiones nuevas usa Ajustes → Respaldo y restauración; conserva la instalación si tu versión no está admitida.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', help='Número de dispositivo mostrado por adb devices')
    parser.add_argument('--output', required=True, type=Path, help='Directorio nuevo y privado para el respaldo')
    parser.add_argument('--adb', default='adb')
    args = parser.parse_args()
    base = [args.adb]
    def run(*argv):
        result = subprocess.run(base + list(argv), stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
        if result.returncode:
            raise RuntimeError('Falló ADB. Revisa conexión, autorización USB y que el APK instalado permita run-as. No desinstales la app.')
        return result.stdout
    lines = run('devices').decode().splitlines()[1:]
    devices = [line.split()[0] for line in lines if len(line.split()) == 2 and line.split()[1] == 'device']
    if args.serial:
        if args.serial not in devices:
            raise RuntimeError('El dispositivo indicado no está autorizado o conectado.')
        base += ['-s', args.serial]
    elif len(devices) != 1:
        raise RuntimeError('Conecta un dispositivo autorizado o selecciona uno con --serial.')
    else:
        base += ['-s', devices[0]]
    run('exec-out', 'run-as', PACKAGE, 'id')
    package_info = run('shell', 'dumpsys', 'package', PACKAGE).decode(errors='replace')
    check_legacy_version(package_info)
    def extract():
        listing = run('exec-out', 'run-as', PACKAGE, 'ls', 'shared_prefs').decode().splitlines()
        raw = {}
        for name in ('preferences', 'places', 'simulation'):
            if name + '.xml.bak' in listing:
                raise RuntimeError('Hay una escritura pendiente. Abre y cierra la app y repite; no desinstales.')
            raw[name] = run('exec-out', 'run-as', PACKAGE, 'cat', 'shared_prefs/' + name + '.xml') if name + '.xml' in listing else b'<map />'
            if len(raw[name]) > MAX_BYTES:
                raise RuntimeError('Un archivo es demasiado grande; conserva la instalación.')
            preferences(raw[name])
        return raw
    raw = extract()
    if preferences(raw['simulation']).get('cleanup_needed', False):
        raise RuntimeError('Pulsa Detener dentro de GPS Mocker antes del rescate. No se ha detenido ni desinstalado la app.')
    run('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    run('shell', 'am', 'force-stop', PACKAGE)
    first = extract()
    second = extract()
    if first != second:
        raise RuntimeError('Los datos cambiaron durante la extracción. Conserva la instalación y vuelve a intentar.')
    backup = convert(second, int(time.time() * 1000))
    args.output.mkdir(mode=0o700, parents=True, exist_ok=False)
    def save(name, value):
        path = args.output / name
        with path.open('xb') as f:
            f.write(value)
        path.chmod(0o600)
    for name, value in second.items():
        save(name + '.xml', value)
    save('gps-mocker-respaldo.json', backup)
    d = json.loads(backup)['data']
    hashes = {name: hashlib.sha256(value).hexdigest() for name, value in second.items()}
    hashes['gps-mocker-respaldo.json'] = hashlib.sha256(backup).hexdigest()
    versions = re.findall(r'\bversion(?:Code|Name)=[^\s]+', package_info)
    manifest = {'package': PACKAGE, 'version': versions, 'sha256': hashes,
                'counts': {'favorites': len(d['favorites']), 'history': len(d['history']), 'routePoints': len(d['route']['points'])}}
    save('manifest.json', json.dumps(manifest, indent=2).encode())
    print('Rescate guardado en:', args.output)
    print('Favoritos:', manifest['counts']['favorites'], '| Historial:', manifest['counts']['history'], '| Puntos:', manifest['counts']['routePoints'])
    print('Antes de desinstalar, valida este JSON con el codec Android:')
    print('./gradlew verifyLegacyBackup -PlegacyBackup="' + str((args.output / 'gps-mocker-respaldo.json').resolve()) + '"')
    print('Esta herramienta nunca desinstala la app. Conserva los XML y el JSON hasta verificar la restauración en el teléfono.')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, KeyError, RuntimeError, OSError, subprocess.SubprocessError, ET.ParseError) as error:
        raise SystemExit(str(error))
