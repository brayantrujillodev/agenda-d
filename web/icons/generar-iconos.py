#!/usr/bin/env python3
"""
Genera los íconos PNG de la PWA sin dependencias externas (solo la stdlib).

    python web/icons/generar-iconos.py

Dibuja un calendario blanco sobre fondo teal (#0d9488, el theme_color).
Se versiona el script, no hace falta commitear a mano cada PNG si cambia el diseño.
"""
import math
import struct
import zlib
from pathlib import Path

TEAL = (13, 148, 136)
WHITE = (255, 255, 255)
SS = 3  # supersampling para bordes suaves

AQUI = Path(__file__).resolve().parent


def inside_rrect(x, y, x0, y0, x1, y1, rtl, rtr, rbr, rbl):
    if x < x0 or x > x1 or y < y0 or y > y1:
        return False
    if x < x0 + rtl and y < y0 + rtl and (x - (x0 + rtl)) ** 2 + (y - (y0 + rtl)) ** 2 > rtl * rtl:
        return False
    if x > x1 - rtr and y < y0 + rtr and (x - (x1 - rtr)) ** 2 + (y - (y0 + rtr)) ** 2 > rtr * rtr:
        return False
    if x > x1 - rbr and y > y1 - rbr and (x - (x1 - rbr)) ** 2 + (y - (y1 - rbr)) ** 2 > rbr * rbr:
        return False
    if x < x0 + rbl and y > y1 - rbl and (x - (x0 + rbl)) ** 2 + (y - (y1 - rbl)) ** 2 > rbl * rbl:
        return False
    return True


def dist_seg(x, y, a, b):
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    l2 = dx * dx + dy * dy or 1.0
    t = max(0.0, min(1.0, ((x - ax) * dx + (y - ay) * dy) / l2))
    return math.hypot(x - (ax + t * dx), y - (ay + t * dy))


def color_at(x, y, size, full_bleed, scale):
    r_bg = 0 if full_bleed else size * 0.22
    cs = size * scale
    ox = (size - cs) / 2
    oy = (size - cs) / 2

    if full_bleed:
        col = TEAL
    elif inside_rrect(x, y, 0, 0, size, size, r_bg, r_bg, r_bg, r_bg):
        col = TEAL
    else:
        return (0, 0, 0, 0)

    cx0, cx1 = ox + 0.13 * cs, ox + 0.87 * cs
    cy0, cy1 = oy + 0.26 * cs, oy + 0.85 * cs
    rr = 0.06 * cs

    # aros de la carpeta: blancos, atraviesan el borde superior del calendario
    hw = 0.035 * cs
    for hxc in (ox + 0.36 * cs, ox + 0.64 * cs):
        if inside_rrect(x, y, hxc - hw, oy + 0.17 * cs, hxc + hw, oy + 0.32 * cs, hw, hw, hw, hw):
            col = WHITE

    if inside_rrect(x, y, cx0, cy0, cx1, cy1, rr, rr, rr, rr):
        col = WHITE
        band = inside_rrect(x, y, cx0, cy0, cx1, oy + 0.40 * cs, rr, rr, 0, 0)
        if band:
            col = TEAL
            for hxc in (ox + 0.36 * cs, ox + 0.64 * cs):
                if inside_rrect(x, y, hxc - hw, oy + 0.17 * cs, hxc + hw, oy + 0.32 * cs, hw, hw, hw, hw):
                    col = WHITE
        else:
            p1 = (ox + 0.31 * cs, oy + 0.63 * cs)
            p2 = (ox + 0.44 * cs, oy + 0.73 * cs)
            p3 = (ox + 0.71 * cs, oy + 0.51 * cs)
            d = min(dist_seg(x, y, p1, p2), dist_seg(x, y, p2, p3))
            if d <= 0.052 * cs:
                col = TEAL

    return (col[0], col[1], col[2], 255)


def render(size, full_bleed, scale):
    raw = bytearray()
    for py in range(size):
        raw.append(0)  # filtro 0
        for px in range(size):
            r = g = b = a = 0
            for sy in range(SS):
                for sx in range(SS):
                    x = px + (sx + 0.5) / SS
                    y = py + (sy + 0.5) / SS
                    cr, cg, cb, ca = color_at(x, y, size, full_bleed, scale)
                    # premultiplicado para mezclar bien el borde
                    r += cr * ca
                    g += cg * ca
                    b += cb * ca
                    a += ca
            n = SS * SS
            if a == 0:
                raw += bytes((0, 0, 0, 0))
            else:
                raw += bytes((round(r / a), round(g / a), round(b / a), round(a / n)))
    return bytes(raw)


def write_png(path, size, raw):
    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    idat = zlib.compress(raw, 9)
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b""))
    print(f"  {path.name}  ({path.stat().st_size} bytes)")


ICONOS = [
    ("favicon-32.png", 32, False, 0.98),
    ("icon-192.png", 192, False, 0.86),
    ("icon-512.png", 512, False, 0.86),
    ("icon-maskable-512.png", 512, True, 0.62),
]

if __name__ == "__main__":
    print("Generando íconos en", AQUI)
    for nombre, size, full_bleed, scale in ICONOS:
        write_png(AQUI / nombre, size, render(size, full_bleed, scale))
    print("Listo.")
