#!/usr/bin/env python3
"""Generates every variant of the Presserl brand icon into this directory.

The motif is a tilted newspaper front page: a Playfair Display "P" masthead, a double rule, the
eight section colours as section bar, a child's drawing as lead picture and a few text lines,
paper on the reader's accent red. The "P" is read from the reader's bundled WOFF2 and written as
an outline, so no output needs the font installed.

Needs fontTools + brotli (see build.sh), rsvg-convert, ImageMagick 7 (magick) and
google-chrome-stable. Run through build.sh; see README.md.
"""

import math
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ICONS = Path(__file__).resolve().parent
FONTS = ICONS.parent / "backend/src/main/resources/META-INF/resources/reader/fonts"

# Default theme of the reader (reader.css)
PAPER, INK, MUTED, ACCENT, RULE = "#fbf8f1", "#1d1b18", "#5b5650", "#a8321d", "#d9d2c3"
SECTIONS = ["#c62828", "#bf5a00", "#9a7400", "#2e7d32", "#00786b", "#1565c0", "#7b1fa2", "#c2185b"]
SKY, SUN, HILL, HILL_LIGHT = "#6ea8f0", "#e3c24a", "#2e7d32", "#6cc070"

FAVICON_SIZES = [16, 32, 48, 180, 192, 512]
ICO_SIZES = [16, 32, 48]
FEATURE_TEXT = {
    "de": ("Schreib für deine eigene Zeitung.", "Für Kinder von 6 bis 16 –<br>ohne Werbung, ohne Tracking."),
    "en": ("Write for your own newspaper.", "For kids aged 6 to 16 –<br>no ads, no tracking."),
}

# The front page on the 512 grid, upright and centred on 256/256 (D1)
SHEET_X, SHEET_Y, SHEET_W, SHEET_H, SHEET_R = 116, 94, 280, 324, 12
TILT = -6
PICTURE = (140, 298, 112, 96, 5)

# Android adaptive icon (D4): 108 dp canvas, the whole sheet inside the 66 dp safe-zone circle
ANDROID_CANVAS, ANDROID_SAFE_ZONE = 108, 66


def num(value):
    return f"{value:.2f}".rstrip("0").rstrip(".")


# --- glyph


def glyph_path(cx, baseline, height):
    """The Playfair Display "P" as path data: centred on cx, standing on baseline, height tall."""
    font = TTFont(FONTS / "playfair-display-latin-700-normal.woff2")
    glyphs = font.getGlyphSet()
    glyph = glyphs[font.getBestCmap()[ord("P")]]
    bounds = BoundsPen(glyphs)
    glyph.draw(bounds)
    x_min, y_min, x_max, y_max = bounds.bounds
    scale = height / (y_max - y_min)
    pen = SVGPathPen(glyphs, ntos=num)
    glyph.draw(TransformPen(pen, (scale, 0, 0, -scale, cx - (x_min + x_max) / 2 * scale, baseline)))
    return pen.getCommands()


# --- shapes as path data (shared by SVG and Android)


def rect(x, y, w, h, r=0):
    if r == 0:
        return f"M{num(x)},{num(y)}H{num(x + w)}V{num(y + h)}H{num(x)}Z"
    return (f"M{num(x + r)},{num(y)}H{num(x + w - r)}A{num(r)},{num(r)} 0 0 1 {num(x + w)},{num(y + r)}"
            f"V{num(y + h - r)}A{num(r)},{num(r)} 0 0 1 {num(x + w - r)},{num(y + h)}"
            f"H{num(x + r)}A{num(r)},{num(r)} 0 0 1 {num(x)},{num(y + h - r)}"
            f"V{num(y + r)}A{num(r)},{num(r)} 0 0 1 {num(x + r)},{num(y)}Z")


def ellipse(cx, cy, rx, ry):
    return (f"M{num(cx - rx)},{num(cy)}A{num(rx)},{num(ry)} 0 1 0 {num(cx + rx)},{num(cy)}"
            f"A{num(rx)},{num(ry)} 0 1 0 {num(cx - rx)},{num(cy)}Z")


def masthead():
    """The "P" and the double rule as (path, fill)."""
    return [(glyph_path(256, 238, 120), INK), (rect(140, 252, 232, 5), INK), (rect(140, 261, 232, 2), INK)]


def section_bar():
    width = (232 - 7 * 6) / 8
    return [(rect(140 + i * (width + 6), 274, width, 9, 2), colour) for i, colour in enumerate(SECTIONS)]


def drawing():
    """The lead picture: sky, sun and two hills, to be clipped to PICTURE."""
    x, y, w, h, _ = PICTURE
    return [(rect(x, y, w, h), SKY), (ellipse(222, 322, 13, 13), SUN),
            (ellipse(170, 404, 70, 38), HILL), (ellipse(240, 410, 56, 34), HILL_LIGHT)]


def text_lines():
    return [(rect(264, 300 + i * 16, 108 if i < 5 else 70, 7, 3.5), MUTED if i == 0 else RULE) for i in range(6)]


# --- SVG


def svg_paths(shapes):
    return "".join(f'<path d="{d}" fill="{fill}"/>' for d, fill in shapes)


def sheet_svg():
    return (f'<rect x="{SHEET_X + 6}" y="{SHEET_Y + 10}" width="{SHEET_W}" height="{SHEET_H}" rx="{SHEET_R}" '
            f'fill="#000" opacity=".22"/>'
            + svg_paths([(rect(SHEET_X, SHEET_Y, SHEET_W, SHEET_H, SHEET_R), PAPER)] + masthead() + section_bar())
            + f'<clipPath id="pic"><path d="{rect(*PICTURE)}"/></clipPath>'
            + f'<g clip-path="url(#pic)">{svg_paths(drawing())}</g>'
            + svg_paths(text_lines()))


def play_icon_svg(background=True):
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512" width="512" height="512">'
            + (f'<rect width="512" height="512" fill="{ACCENT}"/>' if background else "")
            + f'<g transform="rotate({TILT} 256 256)">{sheet_svg()}</g></svg>\n')


def favicon_svg():
    """Simplified for 16 px: upright sheet with "P" and one rule on a rounded red square."""
    return ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">'
            + svg_paths([(rect(0, 0, 64, 64, 14), ACCENT), (rect(12, 8, 40, 48, 4), PAPER),
                         (glyph_path(32, 44, 31), INK), (rect(17, 47, 30, 3), INK)])
            + "</svg>\n")


# --- feature graphic


def feature_html(lang):
    claim, subline = FEATURE_TEXT[lang]
    squares = "".join(f'<span style="background:{c}"></span>' for c in SECTIONS)
    return f'''<!doctype html><html lang="{lang}"><head><meta charset="utf-8"><style>
@font-face{{font-family:PF;src:url(playfair-display-latin-700-normal.woff2);font-weight:700}}
@font-face{{font-family:AH;src:url(atkinson-hyperlegible-latin-400-normal.woff2);font-weight:400}}
@font-face{{font-family:AH;src:url(atkinson-hyperlegible-latin-ext-400-normal.woff2);font-weight:400}}
@font-face{{font-family:AH;src:url(atkinson-hyperlegible-latin-700-normal.woff2);font-weight:700}}
html,body{{margin:0}} body{{width:1024px;height:500px;overflow:hidden;background:{PAPER};font-family:AH;position:relative}}
.band{{position:absolute;left:0;top:0;width:420px;height:500px;background:{ACCENT}}}
.band svg{{position:absolute;left:20px;top:50px;width:400px;height:400px}}
.txt{{position:absolute;left:480px;top:78px;right:48px;color:{INK}}}
h1{{font-family:PF;font-size:118px;line-height:1;margin:0 0 18px;letter-spacing:-.5px}}
.rule{{border-top:7px solid {INK};border-bottom:2.5px solid {INK};height:4px;margin-bottom:26px}}
p{{margin:0}} .a{{font-weight:700;font-size:33px;line-height:1.25;letter-spacing:.02em}}
.b{{font-size:22px;color:{MUTED};margin-top:12px;line-height:1.4;letter-spacing:.02em}}
.sq{{display:flex;gap:10px;margin-top:30px}} .sq span{{width:46px;height:12px;border-radius:3px}}
</style></head><body><div class="band">{play_icon_svg(background=False)}</div>
<div class="txt"><h1>Presserl</h1><div class="rule"></div><p class="a">{claim}</p><p class="b">{subline}</p><div class="sq">{squares}</div></div>
</body></html>
'''


def feature_graphic(lang, work):
    page = work / f"feature-{lang}.html"
    page.write_text(feature_html(lang), encoding="utf-8")
    shot = work / f"feature-{lang}.png"
    run("google-chrome-stable", "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
        f"--user-data-dir={work / 'chrome'}", "--window-size=1024,600", "--virtual-time-budget=5000",
        f"--screenshot={shot}", page.as_uri())
    # Google Play: 1024×500 PNG without alpha
    run("magick", shot, "-crop", "1024x500+0+0", "+repage", "-background", PAPER, "-alpha", "remove",
        "-alpha", "off", f"PNG24:{ICONS / f'feature-graphic-{lang}.png'}")


# --- Android adaptive icon (D4)


def android_vector(body):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            '<!-- Generated by icons/gen.py; do not edit, regenerate and run icons/sync.sh -->\n'
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{ANDROID_CANVAS}dp"\n    android:height="{ANDROID_CANVAS}dp"\n'
            f'    android:viewportWidth="{ANDROID_CANVAS}"\n    android:viewportHeight="{ANDROID_CANVAS}">\n'
            f'    <group android:rotation="{TILT}" android:pivotX="{ANDROID_CANVAS / 2:g}" '
            f'android:pivotY="{ANDROID_CANVAS / 2:g}">\n'
            f'        <group android:scaleX="{ANDROID_SCALE:.5f}" android:scaleY="{ANDROID_SCALE:.5f}" '
            f'android:translateX="{ANDROID_SHIFT:.4f}" android:translateY="{ANDROID_SHIFT:.4f}">\n'
            f'{body}'
            '        </group>\n    </group>\n</vector>\n')


def android_paths(shapes, indent="            "):
    return "".join(f'{indent}<path android:fillColor="{fill.upper()}" android:pathData="{d}" />\n'
                   for d, fill in shapes)


def sheet_reach():
    """Farthest point of the rounded sheet from its centre, in 512-grid units."""
    half_w, half_h = SHEET_W / 2 - SHEET_R, SHEET_H / 2 - SHEET_R
    return math.hypot(half_w, half_h) + SHEET_R


# dp per 512-grid unit so the sheet, whatever its rotation, stays inside the safe zone
ANDROID_SCALE = ANDROID_SAFE_ZONE / 2 / sheet_reach()
ANDROID_SHIFT = ANDROID_CANVAS / 2 - 256 * ANDROID_SCALE


def android_foreground():
    return android_vector(
        android_paths([(rect(SHEET_X, SHEET_Y, SHEET_W, SHEET_H, SHEET_R), PAPER)] + masthead() + section_bar())
        + "            <group>\n"
        + f'                <clip-path android:pathData="{rect(*PICTURE)}" />\n'
        + android_paths(drawing(), indent="                ")
        + "            </group>\n"
        + android_paths(text_lines()))


def android_monochrome():
    """One even-odd shape: the sheet with "P", rules, picture frame and text lines cut out."""
    cut_outs = [d for d, _ in masthead()] + [rect(*PICTURE)] + [d for d, _ in text_lines()]
    path = rect(SHEET_X, SHEET_Y, SHEET_W, SHEET_H, SHEET_R) + "".join(cut_outs)
    return android_vector(
        f'            <path android:fillColor="#FFFFFFFF" android:fillType="evenOdd" android:pathData="{path}" />\n')


# --- main


def run(*command):
    subprocess.run([str(part) for part in command], check=True, stdout=subprocess.DEVNULL)


def main():
    for tool in ("rsvg-convert", "magick", "google-chrome-stable"):
        if shutil.which(tool) is None:
            sys.exit(f"gen.py: {tool} not found on the PATH")

    (ICONS / "play-icon.svg").write_text(play_icon_svg(), encoding="utf-8")
    run("rsvg-convert", "-w", 512, "-h", 512, "-o", ICONS / "play-icon-512.png", ICONS / "play-icon.svg")

    (ICONS / "favicon.svg").write_text(favicon_svg(), encoding="utf-8")
    for size in FAVICON_SIZES:
        run("rsvg-convert", "-w", size, "-h", size, "-o", ICONS / f"favicon-{size}.png", ICONS / "favicon.svg")
    run("magick", *(ICONS / f"favicon-{size}.png" for size in ICO_SIZES), ICONS / "favicon.ico")

    with tempfile.TemporaryDirectory(prefix="presserl-icons-") as tmp:
        work = Path(tmp)
        for font in FONTS.glob("*.woff2"):
            shutil.copy(font, work)
        for lang in FEATURE_TEXT:
            feature_graphic(lang, work)

    android = ICONS / "android"
    android.mkdir(exist_ok=True)
    (android / "ic_launcher_foreground.xml").write_text(android_foreground(), encoding="utf-8")
    (android / "ic_launcher_monochrome.xml").write_text(android_monochrome(), encoding="utf-8")


if __name__ == "__main__":
    main()
