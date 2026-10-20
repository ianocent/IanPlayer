#!/usr/bin/env python3
"""Convert Noun Project SVGs to symmetric 24dp Android vector drawables.

- Strips author-credit <text> nodes (paths only).
- Normalizes every icon to a 24x24 viewport, uniformly scaled and optically
  centered (same optical padding across the whole set -> symmetric sizes).
- Keeps pathData verbatim (Android VectorDrawable supports the full SVG path
  grammar incl. arcs); only placement is adjusted via <group> transform.
"""
import math
import os
import re

SRC = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "icons")
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable")

TOKEN_RE = re.compile(r"([MmLlHhVvCcSsQqTtAaZz])|([-+]?[0-9]*\.?[0-9]+(?:[eE][-+]?[0-9]+)?)")


def tokenize(d):
    toks = []
    for m in TOKEN_RE.finditer(d):
        if m.group(1):
            toks.append(("cmd", m.group(1)))
        else:
            toks.append(("num", float(m.group(2))))
    return toks


def _bez(p0, p1, p2, p3, steps=24):
    pts = []
    for k in range(1, steps + 1):
        t = k / steps
        u = 1 - t
        pts.append((u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0],
                    u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1]))
    return pts


def _quad(p0, p1, p2, steps=16):
    pts = []
    for k in range(1, steps + 1):
        t = k / steps
        u = 1 - t
        pts.append((u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0],
                    u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1]))
    return pts


def full_path_bbox(d):
    toks = tokenize(d)
    i = 0
    cx = cy = sx = sy = 0.0
    xs, ys = [], []
    prev_c2 = None
    prev_q = None
    cmd = None
    ARGN = dict(M=2, L=2, H=1, V=1, C=6, S=4, Q=4, T=2, A=7)

    def emit(x, y):
        xs.append(x)
        ys.append(y)

    def read_group(n):
        nonlocal i
        vals = []
        while i < len(toks) and toks[i][0] == "num" and len(vals) < n:
            vals.append(toks[i][1])
            i += 1
        return vals

    while i < len(toks):
        if toks[i][0] == "cmd":
            cmd = toks[i][1]
            i += 1
            if cmd in "Zz":
                cx, cy = sx, sy
                emit(cx, cy)
                continue
        c = cmd
        if c is None or c in "Zz":
            break
        rel = c.islower()
        base = c.upper()

        # consume all implicit repeats: "m x y x y ..." or "l x y x y ..."
        while True:
            nums = read_group(ARGN[base])
            if len(nums) < ARGN[base]:
                break
            if base == "M":
                x, y = (cx + nums[0], cy + nums[1]) if rel else (nums[0], nums[1])
                cx, cy, sx, sy = x, y, x, y
                emit(x, y)
                rel = c.islower()  # repeats of m are relative
            elif base == "L":
                x, y = (cx + nums[0], cy + nums[1]) if rel else (nums[0], nums[1])
                emit(x, y)
                cx, cy = x, y
            elif base == "H":
                cx = cx + nums[0] if rel else nums[0]
                emit(cx, cy)
            elif base == "V":
                cy = cy + nums[0] if rel else nums[0]
                emit(cx, cy)
            elif base == "C":
                p1 = (cx + nums[0], cy + nums[1]) if rel else (nums[0], nums[1])
                p2 = (cx + nums[2], cy + nums[3]) if rel else (nums[2], nums[3])
                p3 = (cx + nums[4], cy + nums[5]) if rel else (nums[4], nums[5])
                for px, py in _bez((cx, cy), p1, p2, p3):
                    emit(px, py)
                prev_c2 = p2
                cx, cy = p3
            elif base == "S":
                p1 = (2 * cx - prev_c2[0], 2 * cy - prev_c2[1]) if prev_c2 else (cx, cy)
                p2 = (cx + nums[0], cy + nums[1]) if rel else (nums[0], nums[1])
                p3 = (cx + nums[2], cy + nums[3]) if rel else (nums[2], nums[3])
                for px, py in _bez((cx, cy), p1, p2, p3):
                    emit(px, py)
                prev_c2 = p2
                cx, cy = p3
            elif base == "Q":
                p1 = (cx + nums[0], cy + nums[1]) if rel else (nums[0], nums[1])
                p2 = (cx + nums[2], cy + nums[3]) if rel else (nums[2], nums[3])
                for px, py in _quad((cx, cy), p1, p2):
                    emit(px, py)
                prev_q = p1
                cx, cy = p2
            elif base == "T":
                p1 = (2 * cx - prev_q[0], 2 * cy - prev_q[1]) if prev_q else (cx, cy)
                p2 = (cx + nums[0], cy + nums[1]) if rel else (nums[0], nums[1])
                for px, py in _quad((cx, cy), p1, p2):
                    emit(px, py)
                prev_q = p1
                cx, cy = p2
            elif base == "A":
                rx, ry = abs(nums[0]), abs(nums[1])
                laf, sf = int(nums[3]), int(nums[4])  # [rot, laf, sf] after rx ry
                p2 = (cx + nums[5], cy + nums[6]) if rel else (nums[5], nums[6])
                for px, py in arc_pts(cx, cy, rx, ry, laf, sf, p2[0], p2[1]):
                    emit(px, py)
                emit(*p2)
                cx, cy = p2
            if i >= len(toks) or toks[i][0] != "num":
                break

    if not xs:
        return None
    return min(xs), min(ys), max(xs), max(ys)


def arc_pts(x0, y0, rx, ry, laf, sf, x, y, steps=24):
    rx, ry = abs(rx), abs(ry)
    if rx == 0 or ry == 0:
        return []
    x1p = (x0 - x) / 2.0
    y1p = (y0 - y) / 2.0
    lam = (x1p / rx) ** 2 + (y1p / ry) ** 2
    if lam > 1:
        lam = lam ** 0.5
        rx *= lam
        ry *= lam
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    if den == 0:
        return []
    sign = 1 if laf != sf else -1  # SVG F.6.5.9: co = +sqrt when fA != fS
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    if num < 0:
        num = 0.0
    co = sign * (num / den) ** 0.5
    cxp = co * rx * y1p / ry
    cyp = -co * ry * x1p / rx
    theta1 = math.atan2((y1p - cyp) / ry, (x1p - cxp) / rx)
    dtheta = math.atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx) - theta1
    if not sf and dtheta > 0:
        dtheta -= 2 * math.pi
    elif sf and dtheta < 0:
        dtheta += 2 * math.pi
    pts = []
    mx = (x0 + x) / 2.0
    my = (y0 + y) / 2.0
    for k in range(1, steps + 1):
        a = theta1 + dtheta * k / steps
        pts.append((mx + cxp + rx * math.cos(a), my + cyp + ry * math.sin(a)))
    return pts


def transform_d(d, dx=0.0, dy=0.0):
    """Apply a pure translation to a path. dx/dy shift absolute x/y coords;
    relative deltas unchanged. Returns a rewritten path string."""
    toks = tokenize(d)
    ARG_PAIRS = dict(M=1, L=1, H=0, V=0, C=3, S=2, Q=2, T=1, A=0)
    out = []
    i = 0
    cmd = None
    n_segs = 0
    N = dict(M=2, L=2, H=1, V=1, C=6, S=4, Q=4, T=2, A=7)
    seen_first_moveto = False
    while i < len(toks):
        if toks[i][0] == "cmd":
            cmd = toks[i][1]
            out.append(("cmd", cmd))
            i += 1
            if cmd in "Zz":
                continue
        c = cmd
        if c is None or c in "Zz":
            break
        base = c.upper()
        rel = c.islower()
        n = N[base]
        nums = []
        while i < len(toks) and toks[i][0] == "num":
            nums.append(toks[i][1])
            i += 1
        k = 0
        while k + n <= len(nums):
            g = nums[k:k + n]
            first_pending = not seen_first_moveto and base == "M"
            if first_pending:
                seen_first_moveto = True
            if base == "M" or base == "L":
                if not rel or first_pending:
                    g[0] += dx
                    g[1] += dy
            elif base == "H":
                if not rel:
                    g[0] += dx
            elif base == "V":
                if not rel:
                    g[0] += dy
            elif base in ("C", "S", "Q"):
                if not rel:
                    for j in (0, 2, 4):
                        g[j] += dx
                    for j in (1, 3, 5):
                        g[j] += dy
            elif base == "T":
                if not rel:
                    g[0] += dx
                    g[1] += dy
            elif base == "A":
                if not rel:
                    g[5] += dx
                    g[6] += dy
            out += [("num", v) for v in g]
            k += n
    # serialize
    parts = []
    last = None
    for kind, v in out:
        if kind == "cmd":
            parts.append(v)
            last = v
        else:
            s = f"{v:.4f}".rstrip("0").rstrip(".")
            if s == "-0":
                s = "0"
            parts.append((" " if last not in ("-", "") else "") + s if False else s)
    s = " ".join(parts)
    return re.sub(r"\s+([A-Za-z])", r"\1", s)


def mirror_d(d, axis, vertical_mirror=False, dy_axis=0.0):
    """Mirror a path horizontally across x=axis (or vertically across y).
    Arcs flip sweep direction; x-axis-rotation stays 0 in these assets."""
    toks = tokenize(d)
    out = []
    i = 0
    cmd = None
    N = dict(M=2, L=2, H=1, V=1, C=6, S=4, Q=4, T=2, A=7)
    pairs = dict(M=1, L=1, H=0, V=0, C=3, S=2, Q=2, T=1)
    while i < len(toks):
        if toks[i][0] == "cmd":
            cmd = toks[i][1]
            out.append(("cmd", cmd))
            i += 1
            if cmd in "Zz":
                continue
        c = cmd
        if c is None or c in "Zz":
            break
        base = c.upper()
        rel = c.islower()
        n = N[base]
        nums = []
        while i < len(toks) and toks[i][0] == "num":
            nums.append(toks[i][1])
            i += 1
        k = 0
        while k + n <= len(nums):
            g = nums[k:k + n]
            if vertical_mirror:
                # y -> 2*dy_axis - y : flips sweep, negate rotation, leave rx/ry
                if base == "V":
                    g[0] = (2 * dy_axis - g[0]) if not rel else -g[0]
                elif rel:
                    if base in ("C", "S", "Q"):
                        for j in (1, 3, 5):
                            g[j] = -g[j]
                    elif base in ("M", "L", "T"):
                        g[1] = -g[1]
                    elif base == "A":
                        g[6] = -g[6]
                        g[4] = 1 - g[4]
                        if g[2]:
                            g[2] = -g[2]
                else:
                    if base in ("C", "S", "Q"):
                        for j in (1, 3, 5):
                            g[j] = 2 * dy_axis - g[j]
                    elif base in ("M", "L", "T"):
                        g[1] = 2 * dy_axis - g[1]
                    elif base == "A":
                        g[6] = 2 * dy_axis - g[6]
                        g[4] = 1 - g[4]
                        if g[2]:
                            g[2] = -g[2]
            else:
                # x -> 2*axis - x : flips sweep, negate rotation, leave rx/ry
                if base == "H":
                    g[0] = (2 * axis - g[0]) if not rel else -g[0]
                elif rel:
                    if base in ("C", "S", "Q"):
                        for j in (0, 2, 4):
                            g[j] = -g[j]
                    elif base in ("M", "L", "T"):
                        g[0] = -g[0]
                    elif base == "A":
                        g[5] = -g[5]
                        g[4] = 1 - g[4]
                        if g[2]:
                            g[2] = -g[2]
                else:
                    if base in ("C", "S", "Q"):
                        for j in (0, 2, 4):
                            g[j] = 2 * axis - g[j]
                    elif base in ("M", "L", "T"):
                        g[0] = 2 * axis - g[0]
                    elif base == "A":
                        g[5] = 2 * axis - g[5]
                        g[4] = 1 - g[4]
                        if g[2]:
                            g[2] = -g[2]
            out += [("num", v) for v in g]
            k += n
    parts = []
    for kind, v in out:
        if kind == "cmd":
            parts.append(v)
        else:
            s = f"{v:.4f}".rstrip("0").rstrip(".")
            if s == "-0":
                s = "0"
            parts.append(s)
    return " ".join(parts)


def write_vector(paths, out_path, s, tx, ty):
    body = []
    for d, even_odd in paths:
        feo = ' android:fillType="evenOdd"' if even_odd else ""
        dd = d.replace('"', "'")
        body.append(f'      <path android:fillColor="#FF000000"{feo} android:pathData="{dd}"/>')
    xml = (
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp"\n'
        '    android:height="24dp"\n'
        '    android:viewportWidth="24"\n'
        '    android:viewportHeight="24">\n'
        f'  <group android:scaleX="{s:.6f}" android:scaleY="{s:.6f}"\n'
        f'      android:translateX="{tx:.3f}" android:translateY="{ty:.3f}">\n'
        + "\n".join(body)
        + "\n  </group>\n</vector>\n"
    )
    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(xml)


def normalize_and_write(svg_text, out_path, name, prepend_paths=None):
    paths, boxes = [], []
    # svg-level transform (noun assets put translate/matrix on a <g> wrapper)
    svg_t = None
    for tm in re.finditer(r'transform="([^"]+)"', svg_text):
        svg_t = tm.group(1)
    for m in re.finditer(r"<path([^>]*)>", svg_text):
        attrs = m.group(1)
        dm = re.search(r'd\s*=\s*"([^"]+)"', attrs)
        if not dm:
            continue
        dd = dm.group(1)
        # translations: matrix(1,0,0,1,tx,ty) / translate(tx[,ty]) — noun assets only use these
        tlist = re.findall(r'(?P<t>translate|matrix)\(([^)]*)\)', attrs)
        if not tlist and svg_t:
            tlist = re.findall(r'(?P<t>translate|matrix)\(([^)]*)\)', svg_t)
        for fn, raw in tlist:
            args = [float(v) for v in re.split(r"[\s,]+", raw.strip())]
            if fn == "translate":
                if len(args) >= 1:
                    dd = transform_d(dd, args[0], args[1] if len(args) > 1 else 0.0)
            else:
                if len(args) == 6 and args[0] == 1 and args[1] == 0 and args[2] == 0 and args[3] == 1:
                    dd = transform_d(dd, args[4], args[5])
                else:
                    print(f"  ! unsupported matrix in {name}: {args}")
        even_odd = 'fill-rule="evenodd"' in attrs
        paths.append((dd, even_odd))
        b = full_path_bbox(dd)
        if b:
            boxes.append(b)
    if not boxes:
        print(f"  ! empty bbox in {name}")
        return False
    x0 = min(b[0] for b in boxes)
    y0 = min(b[1] for b in boxes)
    x1 = max(b[2] for b in boxes)
    y1 = max(b[3] for b in boxes)
    w = x1 - x0
    h = y1 - y0
    if w <= 0 or h <= 0:
        print(f"  ! degenerate bbox in {name}")
        return False
    s = (24.0 - 2.5) / max(w, h) * 0.985
    tx = 12.0 - s * (x0 + w / 2.0)
    ty = 12.0 - s * (y0 + h / 2.0)
    write_vector(paths, out_path, s, tx, ty)
    print(f"  {name}.xml  scale={s:.3f}  bbox=({x0:.1f},{y0:.1f})-({x1:.1f},{y1:.1f})")
    return True


def convert(svg_path, out_path, name):
    with open(svg_path, "r", encoding="utf-8") as f:
        svg = f.read()
    if not re.search(r"<path[^>]*d\s*=", svg):
        print(f"  ! no paths in {svg_path}")
        return False
    return normalize_and_write(svg, out_path, name)


def main():
    os.makedirs(SRC, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    # The Noun Project pack ships one arrow glyph twice (the "flipped" file is
    # byte-identical); synthesize the true right-arrow by mirroring the path.
    left = os.path.join(SRC, "noun_arrow_left.svg")
    right = os.path.join(SRC, "noun_arrow_right.svg")
    if os.path.exists(left):
        txt = open(left, encoding="utf-8").read()
        d = re.search(r'd="([^"]+)"', txt).group(1)
        md = mirror_d(d, axis=12.0)
        svg = re.sub(r'd="[^"]+"', f'd="{md}"', txt)
        open(right, "w", encoding="utf-8", newline="\n").write(svg)
    svgs = [f for f in sorted(os.listdir(SRC)) if f.lower().endswith(".svg")]
    ok = 0
    for f in svgs:
        name = os.path.splitext(f)[0].lower()
        name = re.sub(r"\d{4,}", "", name)          # strip trailing noun id
        name = re.sub(r"[^a-z0-9]+", "_", name).strip("_")
        print(f"-> {f}")
        if convert(os.path.join(SRC, f), os.path.join(OUT, name + ".xml"), name):
            ok += 1
    print(f"{ok}/{len(svgs)} converted")


if __name__ == "__main__":
    main()
