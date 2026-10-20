import os, re
ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "java")
for dirpath, _, files in os.walk(ROOT):
    for fn in files:
        if not fn.endswith(".kt"):
            continue
        p = os.path.join(dirpath, fn)
        with open(p, "rb") as f:
            src = f.read()
        changed = False
        # remove material icon imports whose symbol is no longer referenced
        for m in re.finditer(rb"import androidx\.compose\.material\.icons\.(\w+)\.(\w+)(\.[\w.]+)?\r?\n", src):
            pkg, name, extra = m.group(1).decode(), m.group(2).decode(), m.group(3)
            sym = (rb"Icons\." + m.group(1) + rb"\." + m.group(2) + rb"\b")
            if not re.search(sym, src):
                src = src.replace(m.group(0), b"", 1)
                changed = True
        # drop the Icons wrapper import if unused
        if b"Icons." not in src:
            for m in re.finditer(rb"import androidx\.compose\.material\.icons\.Icons\r?\n", src):
                src = src.replace(m.group(0), b"", 1)
                changed = True
        if changed:
            with open(p, "wb") as f:
                f.write(src)
            print("cleaned", os.path.relpath(p, ROOT))
