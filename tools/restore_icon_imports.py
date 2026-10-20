import os, re
ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "java")
# map of material icon package dirs from usage package tokens
PKG_DIR = {
    "Rounded": "rounded", "Filled": "filled", "Outlined": "outlined",
    "Sharp": "sharp", "TwoTone": "twotone", "AutoMirrored": "automirrored",
}
USAGE_RE = re.compile(rb"Icons\.([A-Za-z]+)\.([A-Za-z]\w*)")
# AutoMirrored has two segment chain: Icons.AutoMirrored.Filled.Name
USAGE_RE_AM = re.compile(rb"Icons\.AutoMirrored\.([A-Za-z]+)\.([A-Za-z]\w*)")

for dirpath, _, files in os.walk(ROOT):
    for fn in files:
        if not fn.endswith(".kt"):
            continue
        p = os.path.join(dirpath, fn)
        with open(p, "rb") as f:
            src = f.read()
        need = set()
        for m in USAGE_RE.finditer(src):
            if m.group(1) == b"AutoMirrored":
                continue
            need.add((m.group(2).decode(),))
        missing = []
        for pkg_name in sorted(need):
            key = pkg_name[0]
            if "." in key:
                style, name = key.split(".")
                path = "automirrored." + style.lower()
            else:
                name = key
                path = "rounded"
            imp = b"import androidx.compose.material.icons.%s.%s" % (path.encode(), name.encode())
            pat = rb"import androidx\.compose\.material\.icons\." + re.escape(path.encode()) + rb"\." + name.encode() + rb"\b"
            if not re.search(pat, src):
                missing.append(imp)
        if missing:
            eol = b"\r\n" if b"\r\n" in src[:2000] else b"\n"
            insert = eol.join(missing) + eol
            anchor = re.search(rb"import [^\r\n]+", src)
            if anchor:
                src = src[:anchor.start()] + insert + src[anchor.start():]
            else:
                src = re.sub(rb"(package [^\r\n]+)", lambda m: m.group(1) + eol + insert, src, count=1)
            with open(p, "wb") as f:
                f.write(src)
            print("fixed", os.path.relpath(p, ROOT), "->", [m.decode() for m in missing])
