#!/usr/bin/env python3
"""
Genera los íconos PNG de la PWA sin dependencias externas (solo la stdlib).

    python web/icons/generar-iconos.py

Dibuja un calendario blanco sobre fondo teal (#0d9488, el theme_color).
Se versiona el script, no hace falta commitear a mano cada PNG si cambia el diseño.
"""
import struct
import zlib
from pathlib import Path

TEAL = (47, 77, 60)   # verde bosque, el color de marca de la PWA
WHITE = (251, 250, 246)
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


GOLD = (201, 162, 75)
STRIPE = (178, 59, 59)   # rojo clásico de las franjas del poste


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

    # --- poste de barbero, centrado ---
    pw = 0.235 * cs                     # ancho del cilindro
    pxc = ox + 0.50 * cs
    px0, px1 = pxc - pw / 2, pxc + pw / 2
    body_y0, body_y1 = oy + 0.15 * cs, oy + 0.85 * cs
    cap_h = 0.072 * cs
    cap_w = pw + 0.115 * cs

    # tapas doradas
    for cyc in (body_y0 + cap_h / 2, body_y1 - cap_h / 2):
        if inside_rrect(x, y, pxc - cap_w / 2, cyc - cap_h / 2, pxc + cap_w / 2, cyc + cap_h / 2,
                        cap_h / 2, cap_h / 2, cap_h / 2, cap_h / 2):
            col = GOLD

    # cilindro blanco con franjas diagonales verdes
    cyl_y0, cyl_y1 = body_y0 + cap_h, body_y1 - cap_h
    if inside_rrect(x, y, px0, cyl_y0, px1, cyl_y1, pw / 2, pw / 2, pw / 2, pw / 2):
        periodo = 0.135 * cs
        fase = ((x + y) % (2 * periodo)) / periodo
        col = STRIPE if fase < 1.0 else WHITE

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
