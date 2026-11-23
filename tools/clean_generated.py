"""Limpia únicamente salidas conocidas de Gradle; nunca fuentes ni contenidos."""
from pathlib import Path
import argparse
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APK = ROOT / 'app/build/outputs/apk/debug/app-debug.apk'


def remove(path):
    if path.is_symlink() or path.is_file():
        path.unlink()
    elif path.is_dir():
        shutil.rmtree(path)


def keep_only(directory, names):
    if directory.is_dir():
        for child in directory.iterdir():
            if child.name not in names:
                remove(child)


def clean(after=False):
    if after:
        if not APK.is_file() or not zipfile.is_zipfile(APK):
            raise SystemExit('No hay un APK válido: se conservan los archivos para diagnóstico.')
        keep_only(ROOT / 'app/build', {'outputs'})
        keep_only(ROOT / 'app/build/outputs', {'apk'})
        keep_only(ROOT / 'app/build/outputs/apk', {'debug'})
        keep_only(APK.parent, {'app-debug.apk', 'output-metadata.json'})
    else:
        remove(ROOT / 'app/build')
    for name in ('build', '.gradle', '.kotlin'):
        remove(ROOT / name)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--after', action='store_true', help='Conserva el APK debug y sus metadatos')
    clean(parser.parse_args().after)
