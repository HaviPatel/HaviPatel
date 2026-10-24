"""Convert the Lucide icons REA TV uses into normalized SVG path strings for Compose.

Usage: python convert.py <lucide icons dir> <output .kt file>
"""
import re
import sys
import xml.etree.ElementTree as ET

ICONS = {
    "Gear": "settings", "Moon": "moon", "Wifi": "wifi", "Bluetooth": "bluetooth", "Bolt": "zap",
    "Storage": "hard-drive", "Home": "house", "Info": "info", "EyeOff": "eye-off", "Trash": "trash-2",
    "Move": "move", "Star": "star", "Grid": "layout-grid", "Remote": "tv", "Sparkle": "sparkles",
    "Speed": "gauge", "Check": "check", "Play": "play", "Power": "power", "Display": "monitor",
    "Chevron": "chevron-right", "Apps": "grid-3x3",
}

NUM = re.compile(r"[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?")
CMDS = "MmLlHhVvCcSsQqTtAaZz"


def fmt(v):
    s = f"{v:.4f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def normalize(d):
    """Tokenize an SVG path (arc flags may be written without separators) and re-emit it spaced."""
    out, i, cmd, argi = [], 0, None, 0
    while i < len(d):
        c = d[i]
        if c in CMDS:
            out.append(c)
            cmd, argi = c, 0
            i += 1
        elif c in " ,\t\r\n":
            i += 1
        elif cmd in ("A", "a") and argi % 7 in (3, 4):
            if c not in "01":
                raise ValueError(f"bad arc flag {c!r} in {d}")
            out.append(c)
            argi += 1
            i += 1
        else:
            m = NUM.match(d, i)
            if not m:
                raise ValueError(f"cannot parse {d[i:i + 12]!r} in {d}")
            out.append(fmt(float(m.group())))
            argi += 1
            i = m.end()
    return " ".join(out)


def f(el, name, default=0.0):
    v = el.get(name)
    return float(v) if v is not None else default


def to_path(el):
    tag = el.tag.split("}")[-1]
    if tag == "path":
        return normalize(el.get("d"))
    if tag == "circle":
        cx, cy, r = f(el, "cx"), f(el, "cy"), f(el, "r")
        return f"M {fmt(cx - r)} {fmt(cy)} a {fmt(r)} {fmt(r)} 0 1 0 {fmt(2 * r)} 0 a {fmt(r)} {fmt(r)} 0 1 0 {fmt(-2 * r)} 0 Z"
    if tag == "ellipse":
        cx, cy, rx, ry = f(el, "cx"), f(el, "cy"), f(el, "rx"), f(el, "ry")
        return f"M {fmt(cx - rx)} {fmt(cy)} a {fmt(rx)} {fmt(ry)} 0 1 0 {fmt(2 * rx)} 0 a {fmt(rx)} {fmt(ry)} 0 1 0 {fmt(-2 * rx)} 0 Z"
    if tag == "rect":
        x, y, w, h = f(el, "x"), f(el, "y"), f(el, "width"), f(el, "height")
        rx = f(el, "rx", f(el, "ry", 0.0))
        ry = f(el, "ry", rx)
        if rx <= 0:
            return f"M {fmt(x)} {fmt(y)} H {fmt(x + w)} V {fmt(y + h)} H {fmt(x)} Z"
        return (
            f"M {fmt(x + rx)} {fmt(y)} H {fmt(x + w - rx)} A {fmt(rx)} {fmt(ry)} 0 0 1 {fmt(x + w)} {fmt(y + ry)} "
            f"V {fmt(y + h - ry)} A {fmt(rx)} {fmt(ry)} 0 0 1 {fmt(x + w - rx)} {fmt(y + h)} "
            f"H {fmt(x + rx)} A {fmt(rx)} {fmt(ry)} 0 0 1 {fmt(x)} {fmt(y + h - ry)} "
            f"V {fmt(y + ry)} A {fmt(rx)} {fmt(ry)} 0 0 1 {fmt(x + rx)} {fmt(y)} Z"
        )
    if tag == "line":
        return f"M {fmt(f(el, 'x1'))} {fmt(f(el, 'y1'))} L {fmt(f(el, 'x2'))} {fmt(f(el, 'y2'))}"
    if tag in ("polyline", "polygon"):
        nums = [fmt(float(n)) for n in NUM.findall(el.get("points"))]
        pts = [f"{nums[k]} {nums[k + 1]}" for k in range(0, len(nums), 2)]
        return "M " + pts[0] + "".join(f" L {p}" for p in pts[1:]) + (" Z" if tag == "polygon" else "")
    raise ValueError(f"unsupported element {tag}")


def main(icons_dir, out):
    lines = [
        "package com.reatv.launcher.ui",
        "",
        "// Generated from Lucide (https://lucide.dev, ISC licence, see licenses/Lucide-ISC.txt) by",
        "// scripts/lucide_to_kotlin.py: 24x24 viewport, stroke 2, round caps and joins, no fill.",
        "",
        "internal fun lucidePaths(icon: ReaIcon): Array<String> = when (icon) {",
    ]
    for name, lucide in ICONS.items():
        root = ET.parse(f"{icons_dir}/{lucide}.svg").getroot()
        paths = [to_path(el) for el in root]
        body = ", ".join(f'"{p}"' for p in paths)
        lines.append(f"    ReaIcon.{name} -> arrayOf({body}) // {lucide}")
    lines.append("}")
    lines.append("")
    open(out, "w").write("\n".join(lines))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
