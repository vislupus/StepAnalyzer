#!/usr/bin/env python3
"""Generate Android VectorDrawable XML files from Lucide icon primitives.

Lucide (https://lucide.dev) icons are 24x24, stroke based (stroke width 2, round caps and
joins). Each entry below lists the SVG primitives of one icon; the script converts circles,
lines, rects, polylines and polygons into path data and writes
app/src/main/res/drawable/ic_<name>.xml. Run it again after adding icons to ICONS.

    python3 tools/gen_icons.py
"""
import os
import re

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable")

# icon name -> list of primitives. Keys: path(d), circle(cx,cy,r), line(x1,y1,x2,y2),
# rect(x,y,w,h,rx), polyline(points), polygon(points), ellipse(cx,cy,rx,ry)
ICONS = {
    "activity": [("polyline", "22 12 18 12 15 21 9 3 6 12 2 12")],
    "footprints": [
        ("path", "M4 16v-2.38C4 11.5 2.97 10.5 3 8c.03-2.72 1.49-6 4.5-6C9.37 2 10 3.8 10 5.5c0 3.11-2 5.66-2 8.68V16a2 2 0 1 1-4 0Z"),
        ("path", "M20 20v-2.38c0-2.12 1.03-3.12 1-5.62-.03-2.72-1.49-6-4.5-6C14.63 6 14 7.8 14 9.5c0 3.11 2 5.66 2 8.68V20a2 2 0 1 0 4 0Z"),
        ("path", "M16 17h4"), ("path", "M4 13h4")],
    "history": [("path", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"), ("path", "M3 3v5h5"), ("path", "M12 7v5l4 2")],
    "settings": [
        ("path", "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z"),
        ("circle", 12, 12, 3)],
    "play": [("polygon", "6 3 20 12 6 21 6 3")],
    "square": [("rect", 3, 3, 18, 18, 2)],
    "pause": [("rect", 14, 4, 4, 16, 1), ("rect", 6, 4, 4, 16, 1)],
    "upload": [("path", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"), ("polyline", "17 8 12 3 7 8"), ("line", 12, 3, 12, 15)],
    "download": [("path", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"), ("polyline", "7 10 12 15 17 10"), ("line", 12, 15, 12, 3)],
    "trash_2": [("path", "M3 6h18"), ("path", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6"), ("path", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2"), ("line", 10, 11, 10, 17), ("line", 14, 11, 14, 17)],
    "pencil": [("path", "M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z"), ("path", "m15 5 4 4")],
    "chevron_right": [("path", "m9 18 6-6-6-6")],
    "chevron_down": [("path", "m6 9 6 6 6-6")],
    "chevron_up": [("path", "m18 15-6-6-6 6")],
    "chevron_left": [("path", "m15 18-6-6 6-6")],
    "arrow_left": [("path", "m12 19-7-7 7-7"), ("path", "M19 12H5")],
    "map_pin": [("path", "M20 10c0 4.993-5.539 10.193-7.399 11.799a1 1 0 0 1-1.202 0C9.539 20.193 4 14.993 4 10a8 8 0 0 1 16 0"), ("circle", 12, 10, 3)],
    "ruler": [("path", "M21.3 15.3a2.4 2.4 0 0 1 0 3.4l-2.6 2.6a2.4 2.4 0 0 1-3.4 0L2.7 8.7a2.41 2.41 0 0 1 0-3.4l2.6-2.6a2.41 2.41 0 0 1 3.4 0Z"), ("path", "m14.5 12.5 2-2"), ("path", "m11.5 9.5 2-2"), ("path", "m8.5 6.5 2-2"), ("path", "m17.5 15.5 2-2")],
    "gauge": [("path", "m12 14 4-4"), ("path", "M3.34 19a10 10 0 1 1 17.32 0")],
    "chart_column": [("path", "M3 3v16a2 2 0 0 0 2 2h16"), ("path", "M18 17V9"), ("path", "M13 17V5"), ("path", "M8 17v-3")],
    "chart_line": [("path", "M3 3v16a2 2 0 0 0 2 2h16"), ("path", "m19 9-5 5-4-4-3 3")],
    "sliders_horizontal": [("line", 21, 4, 14, 4), ("line", 10, 4, 3, 4), ("line", 21, 12, 12, 12), ("line", 8, 12, 3, 12), ("line", 21, 20, 16, 20), ("line", 12, 20, 3, 20), ("line", 14, 2, 14, 6), ("line", 8, 10, 8, 14), ("line", 16, 18, 16, 22)],
    "sun": [("circle", 12, 12, 4), ("path", "M12 2v2"), ("path", "M12 20v2"), ("path", "m4.93 4.93 1.41 1.41"), ("path", "m17.66 17.66 1.41 1.41"), ("path", "M2 12h2"), ("path", "M20 12h2"), ("path", "m6.34 17.66-1.41 1.41"), ("path", "m19.07 4.93-1.41 1.41")],
    "moon": [("path", "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z")],
    "monitor": [("rect", 2, 3, 20, 14, 2), ("line", 8, 21, 16, 21), ("line", 12, 17, 12, 21)],
    "languages": [("path", "m5 8 6 6"), ("path", "m4 14 6-6 2-3"), ("path", "M2 5h12"), ("path", "M7 2h1"), ("path", "m22 22-5-10-5 10"), ("path", "M14 18h6")],
    "type": [("polyline", "4 7 4 4 20 4 20 7"), ("line", 9, 20, 15, 20), ("line", 12, 4, 12, 20)],
    "palette": [("path", "M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.926 0 1.648-.746 1.648-1.688 0-.437-.18-.835-.437-1.125-.29-.289-.438-.652-.438-1.125a1.64 1.64 0 0 1 1.668-1.668h1.996c3.051 0 5.555-2.503 5.555-5.554C21.965 6.012 17.461 2 12 2z"), ("circle", 13.5, 6.5, 0.9), ("circle", 17.5, 10.5, 0.9), ("circle", 8.5, 7.5, 0.9), ("circle", 6.5, 12.5, 0.9)],
    "info": [("circle", 12, 12, 10), ("path", "M12 16v-4"), ("path", "M12 8h.01")],
    "check": [("path", "M20 6 9 17l-5-5")],
    "x": [("path", "M18 6 6 18"), ("path", "m6 6 12 12")],
    "plus": [("path", "M5 12h14"), ("path", "M12 5v14")],
    "refresh_cw": [("path", "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8"), ("path", "M21 3v5h-5"), ("path", "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16"), ("path", "M8 16H3v5")],
    "rotate_ccw": [("path", "M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8"), ("path", "M3 3v5h5")],
    "share_2": [("circle", 18, 5, 3), ("circle", 6, 12, 3), ("circle", 18, 19, 3), ("line", 8.59, 13.51, 15.42, 17.49), ("line", 15.41, 6.51, 8.59, 10.49)],
    "file_text": [("path", "M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"), ("path", "M14 2v4a2 2 0 0 0 2 2h4"), ("path", "M10 9H8"), ("path", "M16 13H8"), ("path", "M16 17H8")],
    "database": [("ellipse", 12, 5, 9, 3), ("path", "M3 5V19A9 3 0 0 0 21 19V5"), ("path", "M3 12A9 3 0 0 0 21 12")],
    "user": [("path", "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"), ("circle", 12, 7, 4)],
    "save": [("path", "M15.2 3a2 2 0 0 1 1.4.6l3.8 3.8a2 2 0 0 1 .6 1.4V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"), ("path", "M17 21v-7a1 1 0 0 0-1-1H8a1 1 0 0 0-1 1v7"), ("path", "M7 3v4a1 1 0 0 0 1 1h7")],
    "circle_help": [("circle", 12, 12, 10), ("path", "M9.09 9a3 3 0 0 1 5.83 1c0 2-3 3-3 3"), ("path", "M12 17h.01")],
    "zap": [("path", "M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z")],
    "navigation": [("polygon", "3 11 22 2 13 21 11 13 3 11")],
    "timer": [("line", 10, 2, 14, 2), ("line", 12, 14, 15, 11), ("circle", 12, 14, 8)],
    "clock": [("circle", 12, 12, 10), ("polyline", "12 6 12 12 16 14")],
    "route": [("circle", 6, 19, 3), ("path", "M9 19h8.5a3.5 3.5 0 0 0 0-7h-11a3.5 3.5 0 0 1 0-7H15"), ("circle", 18, 5, 3)],
    "folder": [("path", "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z")],
    "ellipsis_vertical": [("circle", 12, 12, 1), ("circle", 12, 5, 1), ("circle", 12, 19, 1)],
    "flask_conical": [("path", "M10 2v7.527a2 2 0 0 1-.211.896L4.72 20.55a1 1 0 0 0 .9 1.45h12.76a1 1 0 0 0 .9-1.45l-5.069-10.127A2 2 0 0 1 14 9.527V2"), ("path", "M8.5 2h7"), ("path", "M7 16h10")],
    "smartphone": [("rect", 5, 2, 14, 20, 2), ("path", "M12 18h.01")],
    "circle_check": [("circle", 12, 12, 10), ("path", "m9 12 2 2 4-4")],
    "triangle_alert": [("path", "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"), ("path", "M12 9v4"), ("path", "M12 17h.01")],
    "eye": [("path", "M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0"), ("circle", 12, 12, 3)],
    "list": [("path", "M3 12h.01"), ("path", "M3 18h.01"), ("path", "M3 6h.01"), ("path", "M8 12h13"), ("path", "M8 18h13"), ("path", "M8 6h13")],
    "book_open": [("path", "M12 7v14"), ("path", "M3 18a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1h5a4 4 0 0 1 4 4 4 4 0 0 1 4-4h5a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1h-6a3 3 0 0 0-3 3 3 3 0 0 0-3-3z")],
    "cpu": [("rect", 4, 4, 16, 16, 2), ("rect", 9, 9, 6, 6, 1), ("path", "M15 2v2"), ("path", "M15 20v2"), ("path", "M2 15h2"), ("path", "M2 9h2"), ("path", "M20 15h2"), ("path", "M20 9h2"), ("path", "M9 2v2"), ("path", "M9 20v2")],
    "audio_waveform": [("path", "M2 13a2 2 0 0 0 2-2V7a2 2 0 0 1 4 0v13a2 2 0 0 0 4 0V4a2 2 0 0 1 4 0v13a2 2 0 0 0 4 0v-4a2 2 0 0 1 2-2")],
    "locate_fixed": [("line", 2, 12, 5, 12), ("line", 19, 12, 22, 12), ("line", 12, 2, 12, 5), ("line", 12, 19, 12, 22), ("circle", 12, 12, 7), ("circle", 12, 12, 3)],
    "hard_drive": [("line", 22, 12, 2, 12), ("path", "M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z"), ("line", 6, 16, 6.01, 16), ("line", 10, 16, 10.01, 16)],
    "target": [("circle", 12, 12, 10), ("circle", 12, 12, 6), ("circle", 12, 12, 2)],
    "calendar": [("path", "M8 2v4"), ("path", "M16 2v4"), ("rect", 3, 4, 18, 18, 2), ("path", "M3 10h18")],
    "mountain": [("path", "m8 3 4 8 5-5 5 15H2L8 3z")],
    "trending_up": [("polyline", "22 7 13.5 15.5 8.5 10.5 2 17"), ("polyline", "16 7 22 7 22 13")],
    "sigma": [("path", "M18 7V5a1 1 0 0 0-1-1H6.5a.5.5 0 0 0-.4.8l4.5 6a2 2 0 0 1 0 2.4l-4.5 6a.5.5 0 0 0 .4.8H17a1 1 0 0 0 1-1v-2")],
    "percent": [("line", 19, 5, 5, 19), ("circle", 6.5, 6.5, 2.5), ("circle", 17.5, 17.5, 2.5)],
    "layers": [("path", "m12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83Z"), ("path", "m22 17.65-9.17 4.16a2 2 0 0 1-1.66 0L2 17.65"), ("path", "m22 12.65-9.17 4.16a2 2 0 0 1-1.66 0L2 12.65")],
    "move_horizontal": [("path", "m18 8 4 4-4 4"), ("path", "M2 12h20"), ("path", "m6 8-4 4 4 4")],
    "chevrons_up_down": [("path", "m7 15 5 5 5-5"), ("path", "m7 9 5-5 5 5")],
    "scale": [("path", "m16 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z"), ("path", "m2 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z"), ("path", "M7 21h10"), ("path", "M12 3v18"), ("path", "M3 7h2c2 0 5-1 7-2 2 1 5 2 7 2h2")],
    "wifi_off": [("path", "M12 20h.01"), ("path", "M8.5 16.429a5 5 0 0 1 7 0"), ("path", "M5 12.859a10 10 0 0 1 5.17-2.69"), ("path", "M19 12.859a10 10 0 0 0-2.007-1.523"), ("path", "M2 8.82a15 15 0 0 1 4.177-2.643"), ("path", "M22 8.82a15 15 0 0 0-11.288-3.764"), ("path", "m2 2 20 20")],
    "satellite": [("path", "M13 7 9 3 5 7l4 4"), ("path", "m17 11 4 4-4 4-4-4"), ("path", "m8 12 4 4 6-6-4-4Z"), ("path", "m16 8 3-3"), ("path", "M9 21a6 6 0 0 0-6-6")],
}

def f(v):
    s = ("%.3f" % float(v)).rstrip("0").rstrip(".")
    return s if s not in ("", "-0") else "0"

def circle(cx, cy, r):
    return f"M{f(cx - r)},{f(cy)} a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0 a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0"

def ellipse(cx, cy, rx, ry):
    return f"M{f(cx - rx)},{f(cy)} a{f(rx)},{f(ry)} 0 1,0 {f(2 * rx)},0 a{f(rx)},{f(ry)} 0 1,0 {f(-2 * rx)},0"

def rect(x, y, w, h, rx):
    if rx <= 0:
        return f"M{f(x)},{f(y)} h{f(w)} v{f(h)} h{f(-w)} z"
    return (f"M{f(x + rx)},{f(y)} h{f(w - 2 * rx)} a{f(rx)},{f(rx)} 0 0 1 {f(rx)},{f(rx)} v{f(h - 2 * rx)} "
            f"a{f(rx)},{f(rx)} 0 0 1 {f(-rx)},{f(rx)} h{f(-(w - 2 * rx))} a{f(rx)},{f(rx)} 0 0 1 {f(-rx)},{f(-rx)} "
            f"v{f(-(h - 2 * rx))} a{f(rx)},{f(rx)} 0 0 1 {f(rx)},{f(-rx)} z")

def poly(points, close):
    nums = [float(v) for v in re.split(r"[ ,]+", points.strip())]
    pts = list(zip(nums[0::2], nums[1::2]))
    d = "M" + " L".join(f"{f(x)},{f(y)}" for x, y in pts)
    return d + (" z" if close else "")

def to_path(prim):
    kind = prim[0]
    if kind == "path":
        return prim[1]
    if kind == "circle":
        return circle(*prim[1:])
    if kind == "ellipse":
        return ellipse(*prim[1:])
    if kind == "line":
        x1, y1, x2, y2 = prim[1:]
        return f"M{f(x1)},{f(y1)} L{f(x2)},{f(y2)}"
    if kind == "rect":
        return rect(*prim[1:])
    if kind == "polyline":
        return poly(prim[1], False)
    if kind == "polygon":
        return poly(prim[1], True)
    raise ValueError(kind)

TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- Lucide icon "{lucide}" (https://lucide.dev, ISC license), converted by tools/gen_icons.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
{paths}
</vector>
"""

PATH = """    <path
        android:fillColor="#00000000"
        android:strokeColor="#FF000000"
        android:strokeWidth="2"
        android:strokeLineCap="round"
        android:strokeLineJoin="round"
        android:pathData="{d}" />"""

os.makedirs(OUT, exist_ok=True)
for name, prims in ICONS.items():
    paths = "\n".join(PATH.format(d=to_path(p)) for p in prims)
    with open(os.path.join(OUT, f"ic_{name}.xml"), "w") as fh:
        fh.write(TEMPLATE.format(lucide=name.replace("_", "-"), paths=paths))
print(f"wrote {len(ICONS)} icons to {os.path.abspath(OUT)}")
