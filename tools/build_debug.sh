#!/usr/bin/env bash
# Ejecutar sin otra compilación de este proyecto en marcha.
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tools/clean_generated.py
./gradlew testDebugUnitTest lintDebug assembleDebug
# Mostrar el resultado antes de retirar informes y temporales.
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
files = list(Path('app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
counts = {key: 0 for key in ('tests', 'failures', 'errors', 'skipped')}
for file in files:
    suite = ET.parse(file).getroot()
    for key in counts:
        counts[key] += int(suite.attrib.get(key, 0))
if not files or counts['failures'] or counts['errors']:
    raise SystemExit('No se pueden confirmar las pruebas; se conservan los informes.')
print('Pruebas:', counts)
report = Path('app/build/reports/lint-results-debug.txt')
if report.exists():
    print('Lint:', report.read_text().strip().splitlines()[-1])
apk = Path('app/build/outputs/apk/debug/app-debug.apk')
print('APK:', apk.resolve(), '—', apk.stat().st_size, 'bytes')
PY
python3 tools/clean_generated.py --after
