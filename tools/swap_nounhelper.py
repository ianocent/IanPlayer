"""Pass 2: swap vectorResource( -> nounIcon( and add the helper import.
nounIcon caches the resolved ImageVector with remember(id) (see GlassIconKit.kt)."""
import os
import re

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "java")
HELPER = b"import com.ianocent.musicplayer.ui.glass.nounIcon"
VEC = b"import androidx.compose.ui.res.vectorResource"
R = b"import com.ianocent.musicplayer.R"


def process(path):
    with open(path, "rb") as f:
        src = f.read()
    src, n = re.subn(br"vectorResource\(R\.drawable\.", br"nounIcon(R.drawable.", src)
    if not n:
        return False
    eol = b"\r\n" if b"\r\n" in src[:2000] else b"\n"
    def add_import(imp):
        nonlocal src
        if imp in src:
            return
        src = re.sub(rb"(package [^\r\n]+)", lambda m: m.group(1) + eol + imp + eol, src, count=1)
    add_import(HELPER)
    add_import(VEC)
    m = re.search(rb"^package ([\w.]+)$", src, re.M)
    if m and m.group(1) != b"com.ianocent.musicplayer":
        add_import(R)
    with open(path, "wb") as f:
        f.write(src)
    return True


def main():
    changed = 0
    for dirpath, _, files in os.walk(ROOT):
        for fn in files:
            if fn.endswith(".kt") and process(os.path.join(dirpath, fn)):
                changed += 1
    print(f"{changed} files updated")


if __name__ == "__main__":
    main()
