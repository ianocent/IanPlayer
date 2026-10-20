# noun_love_outline: same geometry as noun_love, stroked instead of filled
import re, sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__)))
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res", "drawable")
base = open(os.path.join(OUT, "noun_love.xml"), encoding="utf-8").read()
scale = float(re.search(r'android:scaleX="([\d.]+)"', base).group(1))
stroke_units = 2.0 / scale  # ~2px visual stroke
stroked = base.replace(
    'android:fillColor="#FF000000"',
    'android:fillColor="#00000000" android:strokeColor="#FFFFFFFF" android:strokeWidth="%.3f"' % stroke_units
)
open(os.path.join(OUT, "noun_love_outline.xml"), "w", encoding="utf-8", newline="\n").write(stroked)
print("wrote noun_love_outline.xml stroke_units=%.2f" % stroke_units)
