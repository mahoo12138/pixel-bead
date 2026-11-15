#!/usr/bin/env python3
"""Generate the Pixel Bead project icon from the MARD 291 colour table.

The icon is an abstract bead field: 291 squares arranged on a phyllotaxis
(golden-angle) spiral, one square per MARD colour, taken in colour-code order
(A1 .. ZG8). Because the radius grows with sqrt(index), hue advances outward and
the 15 MARD series read as a spiral ribbon instead of a flat rainbow.

The image is computed entirely from ``color-data/mard-291.csv`` with the Python
standard library: deterministic, reproducible, and free of any external artwork.
Anti-aliasing is analytic (exact axis-aligned rectangle coverage per pixel), so
the output is the same on every machine.

Usage:
    python tools/make_icon.py                     # 512x512 -> jar icon
    python tools/make_icon.py --size 1024 -o art/platform-icon.png
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import math
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CSV_PATH = os.path.join(ROOT, "color-data", "mard-291.csv")
DEFAULT_OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "pixelbead", "icon.png")

GOLDEN_ANGLE = math.pi * (3.0 - math.sqrt(5.0))  # 2.399963.. rad
EXPECTED_COLOURS = 291
EXPECTED_SERIES = 15


def load_palette(path: str) -> list[tuple[str, tuple[int, int, int]]]:
    """Read the MARD table as [(code, (r, g, b))] in file order."""
    with open(path, encoding="utf-8", newline="") as fh:
        rows = list(csv.DictReader(fh))
    if len(rows) != EXPECTED_COLOURS:
        raise SystemExit(f"expected {EXPECTED_COLOURS} colours, found {len(rows)}")
    series = {row["code"][0] for row in rows}
    if len(series) != EXPECTED_SERIES:
        raise SystemExit(f"expected {EXPECTED_SERIES} colour series, found {len(series)}")
    return [
        (row["code"].strip(), (int(row["r"]), int(row["g"]), int(row["b"])))
        for row in rows
    ]


def write_png(path: str, width: int, height: int, rows: list[bytearray]) -> None:
    """Write 8-bit truecolour PNG (filter 0, zlib level 9) with no metadata."""
    raw = bytearray()
    for row in rows:
        raw.append(0)
        raw += row

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + tag
            + data
            + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
        )

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    blob = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
        + chunk(b"IEND", b"")
    )
    os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
    with open(path, "wb") as fh:
        fh.write(blob)


def fill_rect(
    buf: list[float], size: int, x0: float, y0: float, x1: float, y1: float, colour: tuple[float, float, float]
) -> None:
    """Composite an axis-aligned rectangle with exact analytic edge coverage."""
    for py in range(max(0, int(math.floor(y0))), min(size, int(math.ceil(y1)))):
        cov_y = min(py + 1, y1) - max(py, y0)
        if cov_y <= 0.0:
            continue
        row = py * size * 3
        for px in range(max(0, int(math.floor(x0))), min(size, int(math.ceil(x1)))):
            cov_x = min(px + 1, x1) - max(px, x0)
            if cov_x <= 0.0:
                continue
            alpha = min(1.0, cov_x * cov_y)
            i = row + px * 3
            inv = 1.0 - alpha
            buf[i] = buf[i] * inv + colour[0] * alpha
            buf[i + 1] = buf[i + 1] * inv + colour[1] * alpha
            buf[i + 2] = buf[i + 2] * inv + colour[2] * alpha


def render(size: int, palette: list[tuple[str, tuple[int, int, int]]]) -> list[bytearray]:
    """Compose the bead field on a warm paper backdrop."""
    unit = size / 512.0  # the whole layout is authored at 512 and scaled
    buf = [0.0] * (size * size * 3)
    centre = size / 2.0

    # Backdrop: warm paper, a touch darker at the corners. A light ground keeps the
    # ~60 near-black MARD colours readable; every bead also carries its own rim, so
    # pale and dark colours both stay separated from the backdrop.
    inner = (247.0, 243.0, 234.0)
    outer = (226.0, 220.0, 208.0)
    max_d = math.hypot(centre, centre)
    for y in range(size):
        dy = y + 0.5 - centre
        base = y * size * 3
        for x in range(size):
            dx = x + 0.5 - centre
            t = min(1.0, math.hypot(dx, dy) / max_d * 1.18)
            t = t * t * (3.0 - 2.0 * t)  # smoothstep, avoids a visible disc edge
            i = base + x * 3
            buf[i] = inner[0] + (outer[0] - inner[0]) * t
            buf[i + 1] = inner[1] + (outer[1] - inner[1]) * t
            buf[i + 2] = inner[2] + (outer[2] - inner[2]) * t

    radius = 224.0 * unit
    bead = 20.4 * unit
    rim = 1.15 * unit
    half = bead / 2.0
    count = len(palette)

    for idx, (_code, rgb) in enumerate(palette):
        r = radius * math.sqrt((idx + 0.6) / count)
        theta = idx * GOLDEN_ANGLE
        cx = centre + r * math.cos(theta)
        cy = centre + r * math.sin(theta)

        # Gentle dome shading: beads settle slightly towards the rim, so the field
        # reads as a shallow bead bowl rather than a flat sticker.
        depth = 1.0 - 0.12 * (r / radius) ** 2
        colour = (rgb[0] * depth, rgb[1] * depth, rgb[2] * depth)
        # Per-bead rim: own hue pushed towards the paper side, so a white bead gets a
        # soft grey edge and a black bead a charcoal one instead of a uniform web.
        edge = tuple(c * 0.45 + 22.0 for c in colour)

        fill_rect(buf, size, cx - half, cy - half, cx + half, cy + half, edge)
        inset = half - rim
        fill_rect(buf, size, cx - inset, cy - inset, cx + inset, cy + inset, colour)

    rows: list[bytearray] = []
    for y in range(size):
        row = bytearray(size * 3)
        base = y * size * 3
        for x in range(size * 3):
            value = int(buf[base + x] + 0.5)
            row[x] = 0 if value < 0 else (255 if value > 255 else value)
        rows.append(row)
    return rows


def main() -> None:
    ap = argparse.ArgumentParser(description="Render the Pixel Bead icon from MARD colour data")
    ap.add_argument("-o", "--out", default=DEFAULT_OUT, help="output PNG path")
    ap.add_argument("--size", type=int, default=512, help="square edge in pixels (default 512)")
    ap.add_argument("--csv", default=CSV_PATH, help="path to mard-291.csv")
    args = ap.parse_args()

    if args.size < 64 or args.size % 8:
        raise SystemExit("--size must be a multiple of 8 and at least 64")

    palette = load_palette(args.csv)
    rows = render(args.size, palette)
    write_png(args.out, args.size, args.size, rows)

    with open(args.out, "rb") as fh:
        digest = hashlib.sha256(fh.read()).hexdigest()
    size_bytes = os.path.getsize(args.out)
    series = sorted({code[0] for code, _ in palette})
    print(f"wrote {os.path.relpath(args.out, ROOT)}  {args.size}x{args.size}  {size_bytes} bytes")
    print(f"  colours={len(palette)}  series={''.join(series)} ({len(series)})")
    print(f"  sha256={digest}")


if __name__ == "__main__":
    main()
