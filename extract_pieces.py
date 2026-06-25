#!/usr/bin/env python3
"""
Extract individual chess piece SVGs from design-reference.svg
and create Xcode xcassets structure.

Run from the Chess/ folder:
    python3 extract_pieces.py
"""

import re
import json
from pathlib import Path

SRC = Path("design-reference.svg")
ASSETS_DIR = Path("Chessko/Assets.xcassets")

if not SRC.exists():
    print(f"ERROR: {SRC} not found. Make sure design-reference.svg is in this folder.")
    exit(1)

src = SRC.read_text(encoding="utf-8")

# ── Strip background and board-square rects ──────────────────────────────────
# Remove: navy background, light squares (st0), dark squares (st1), selected (st5)
clean = re.sub(r'[ \t]*<rect class="st[0125]"[^/]*/>\n?', '', src)

# ── Board geometry ───────────────────────────────────────────────────────────
SQ = 232.96   # square size in SVG units
PAD = 8       # padding around each piece viewBox

col_left = [68.15 + i * SQ for i in range(8)]   # a=0 … h=7
row_top  = [68.15 + i * SQ for i in range(8)]   # rank8=0 … rank1=7

# (col_index, row_index) for each unique piece on the starting board
# Row 0 = rank 8 (black back rank), Row 7 = rank 1 (white back rank)
PIECES = {
    # Black pieces
    "piece_black_rook":   (0, 0),   # a8
    "piece_black_knight": (1, 0),   # b8
    "piece_black_bishop": (2, 0),   # c8
    "piece_black_queen":  (3, 0),   # d8
    "piece_black_king":   (4, 0),   # e8
    "piece_black_pawn":   (0, 1),   # a7 (any pawn column works)
    # White pieces
    "piece_white_rook":   (0, 7),   # a1
    "piece_white_knight": (1, 7),   # b1
    "piece_white_bishop": (2, 7),   # c1
    "piece_white_queen":  (3, 7),   # d1
    "piece_white_king":   (4, 7),   # e1
    "piece_white_pawn":   (0, 6),   # a2 (any pawn column works)
}

# ── xcassets Contents.json template ─────────────────────────────────────────
def imageset_contents(svg_filename):
    return {
        "images": [{"filename": svg_filename, "idiom": "universal"}],
        "info": {"author": "xcode", "version": 1},
        "properties": {"preserves-vector-representation": True, "template-rendering-intent": "original"}
    }

root_contents = {
    "info": {"author": "xcode", "version": 1}
}

# ── Create Assets.xcassets if it doesn't exist ───────────────────────────────
ASSETS_DIR.mkdir(parents=True, exist_ok=True)
root_json = ASSETS_DIR / "Contents.json"
if not root_json.exists():
    root_json.write_text(json.dumps(root_contents, indent=2))
    print(f"Created {root_json}")

# ── Extract each piece ────────────────────────────────────────────────────────
for name, (col, row) in PIECES.items():
    x = col_left[col] - PAD
    y = row_top[row]  - PAD
    w = SQ + 2 * PAD
    h = SQ + 2 * PAD

    # Replace the viewBox in the SVG
    piece_svg = re.sub(
        r'viewBox="[^"]*"',
        f'viewBox="{x:.2f} {y:.2f} {w:.2f} {h:.2f}"',
        clean
    )

    # Create imageset folder
    imageset_dir = ASSETS_DIR / f"{name}.imageset"
    imageset_dir.mkdir(exist_ok=True)

    svg_path = imageset_dir / f"{name}.svg"
    svg_path.write_text(piece_svg, encoding="utf-8")

    contents_path = imageset_dir / "Contents.json"
    contents_path.write_text(json.dumps(imageset_contents(f"{name}.svg"), indent=2))

    print(f"✓  {name}  (viewBox {x:.0f} {y:.0f} {w:.0f} {h:.0f})")

print(f"\n✅  Done — {len(PIECES)} piece SVGs created in {ASSETS_DIR}/")
print("\nNext steps in Xcode:")
print("  1. Add the Chessko/Assets.xcassets folder to your project if not already there")
print("  2. Xcode will auto-detect all .imageset folders")
print("  3. Build & run — pieces will render using your design SVG")
