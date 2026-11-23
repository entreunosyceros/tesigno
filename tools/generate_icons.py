"""Genera recursos Android a partir del PNG original sin recortar el logo.
Requiere Pillow: python3 -m pip install Pillow
"""
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw

RES = Path(__file__).resolve().parents[1] / 'app/src/main/res'
source = Image.open(RES / 'drawable/logo_tesigno.png').convert('RGBA')

def padded(size, fraction, background):
    canvas = Image.new('RGBA', (size, size), background)
    logo = ImageOps.contain(source, (round(size * fraction), round(size * fraction)), Image.Resampling.LANCZOS)
    canvas.alpha_composite(logo, ((size - logo.width) // 2, (size - logo.height) // 2))
    return canvas

for density, scale in [('mdpi', 1), ('hdpi', 1.5), ('xhdpi', 2), ('xxhdpi', 3), ('xxxhdpi', 4)]:
    folder = RES / f'mipmap-{density}'
    folder.mkdir(exist_ok=True)
    padded(round(48 * scale), .86, (0, 0, 0, 0)).save(folder / 'ic_launcher.png')
    size = round(48 * scale)
    rounded = padded(size, .64, 'white')
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).ellipse((1, 1, size - 2, size - 2), fill=255)
    rounded.putalpha(mask)
    rounded.save(folder / 'ic_launcher_round.png')
    # El cuadrado completo cabe dentro de la zona circular segura del icono adaptativo.
    padded(round(108 * scale), 46 / 108, (0, 0, 0, 0)).save(folder / 'ic_launcher_foreground.png')

folder = RES / 'drawable-nodpi'
folder.mkdir(exist_ok=True)
padded(864, 136 / 288, (0, 0, 0, 0)).save(folder / 'splash_logo.png')
