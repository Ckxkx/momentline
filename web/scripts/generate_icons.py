from pathlib import Path
import struct
import zlib

OUTPUT = Path(__file__).resolve().parents[1] / "public" / "icons"


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def inside_rounded_square(x, y, size, radius):
    left = x < radius and y < radius
    right = x >= size - radius and y < radius
    bottom_left = x < radius and y >= size - radius
    bottom_right = x >= size - radius and y >= size - radius
    if not (left or right or bottom_left or bottom_right):
        return True
    cx = radius if x < radius else size - radius
    cy = radius if y < radius else size - radius
    return (x - cx) ** 2 + (y - cy) ** 2 <= radius**2


def write_icon(path, size, maskable):
    rows = []
    for py in range(size):
        row = bytearray([0])
        y = (py + 0.5) / size
        for px in range(size):
            x = (px + 0.5) / size
            color = (39, 95, 85) if maskable or inside_rounded_square(px, py, size, int(size * 0.22)) else (244, 243, 238)
            line = abs(x - 0.32) < 0.008 and 0.24 < y < 0.76
            dot1 = (x - 0.32) ** 2 + (y - 0.33) ** 2 < 0.045**2
            dot2 = (x - 0.32) ** 2 + (y - 0.51) ** 2 < 0.036**2
            dot3 = (x - 0.32) ** 2 + (y - 0.67) ** 2 < 0.028**2
            bars = any(abs(y - yy) < 0.012 and 0.45 < x < end for yy, end in [(0.33, 0.74), (0.51, 0.68), (0.67, 0.60)])
            if line or dot1 or dot3 or bars:
                color = (246, 243, 233)
            if dot2:
                color = (217, 167, 108)
            row.extend(color)
        rows.append(bytes(row))
    data = b"".join(rows)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(data, 9)) + chunk(b"IEND", b"")
    path.write_bytes(png)


OUTPUT.mkdir(parents=True, exist_ok=True)
write_icon(OUTPUT / "icon-192.png", 192, False)
write_icon(OUTPUT / "icon-512.png", 512, False)
write_icon(OUTPUT / "icon-512-maskable.png", 512, True)