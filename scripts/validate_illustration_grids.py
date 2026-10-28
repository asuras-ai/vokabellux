#!/usr/bin/env python3
"""Verify atlas gutters so a merged AI-generated panel cannot silently ship.

Uses FFmpeg only for PNG decoding; no image files are edited or generated.
"""
import argparse
import json
from pathlib import Path
import struct
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def check(path):
    data = path.read_bytes()
    width, height = struct.unpack(">II", data[16:24])
    pixels = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(path), "-frames:v", "1",
         "-f", "rawvideo", "-pix_fmt", "rgb24", "pipe:1"],
        capture_output=True, check=True,
    ).stdout

    def white(x, y):
        offset = (y * width + x) * 3
        rgb = pixels[offset:offset + 3]
        return min(rgb) >= 225 and max(rgb) - min(rgb) <= 25

    failures = []

    def boundaries(length, perpendicular, vertical):
        result = [{"start": 0, "end": -1, "center": 0}]
        for division in range(1, 4):
            nominal = division * length // 4
            samples = range(8, perpendicular - 8, 4)
            candidates = []
            # Generated panels can differ slightly in size. Measure their real
            # gutters instead of cutting through the art at nominal quarters.
            for position in range(nominal - length // 20, nominal + length // 20 + 1):
                score = sum(white(position, p) if vertical else white(p, position) for p in samples) / len(samples)
                if score >= .9:
                    candidates.append(position)
            groups = []
            for position in candidates:
                if not groups or position > groups[-1][-1] + 1:
                    groups.append([position])
                else:
                    groups[-1].append(position)
            if not groups:
                failures.append(f"missing {'vertical' if vertical else 'horizontal'} gutter {division}")
                result.append({"start": nominal, "end": nominal, "center": nominal})
            else:
                band = min(groups, key=lambda g: abs((g[0] + g[-1]) / 2 - nominal))
                result.append({"start": band[0], "end": band[-1], "center": (band[0] + band[-1]) // 2})
        result.append({"start": length, "end": length - 1, "center": length})
        return result

    columns = boundaries(width, height, True)
    rows = boundaries(height, width, False)
    for row in range(4):
        ys = range(rows[row]["end"] + 16, rows[row + 1]["start"] - 16, 4)
        for division in range(1, 4):
            center = columns[division]["center"]
            score = sum(white(center, y) for y in ys) / len(ys)
            if score < .75:
                failures.append(f"row {row + 1}, vertical division {division}: {score:.0%} gutter")
    for column in range(4):
        xs = range(columns[column]["end"] + 16, columns[column + 1]["start"] - 16, 4)
        for division in range(1, 4):
            center = rows[division]["center"]
            score = sum(white(x, center) for x in xs) / len(xs)
            if score < .75:
                failures.append(f"column {column + 1}, horizontal division {division}: {score:.0%} gutter")
    panels = []
    for row in range(4):
        for column in range(4):
            left = columns[column]["end"] + 1
            top = rows[row]["end"] + 1
            panels.append([left, top, columns[column + 1]["start"] - left,
                           rows[row + 1]["start"] - top])
    return failures, {"dimensions": [width, height], "panels": panels}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("images", type=Path, nargs="*")
    parser.add_argument("--write-layout", action="store_true", help="Save verified pixel bounds for content compilation")
    args = parser.parse_args()
    paths = args.images or sorted((ROOT / "app/src/main/assets/images").glob("board_*.png"))
    failed = False
    layouts = {}
    for path in paths:
        failures, layout = check(path)
        layouts[path.name] = layout
        if failures:
            failed = True
            print(f"FAIL {path.name}: " + "; ".join(failures))
        else:
            print(f"PASS {path.name}: 4x4 grid")
    if args.write_layout and not failed:
        (ROOT / "scripts/illustration-layout.json").write_text(json.dumps(layouts, indent=2) + "\n")
    return int(failed)


if __name__ == "__main__":
    sys.exit(main())
