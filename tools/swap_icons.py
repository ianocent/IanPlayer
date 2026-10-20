"""Icon substitution: swap Material icon usages for the project's Noun vector set.
Byte-accurate (preserves line endings), adds/cleans imports automatically."""
import os
import re

ROOT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "java")

# name -> drawable; ordered longest-first so $word-boundary keeps RepeatOne/RepeatOn before Repeat
MAPPING = [
    ("RepeatOne", "noun_repeat_one"),
    ("RepeatOn", "noun_repeat_on"),
    ("Repeat", "noun_repeat"),
    ("SkipNext", "noun_next"),
    ("SkipPrevious", "noun_prev"),
    ("PlayArrow", "noun_play"),
    ("Pause", "noun_pause"),
    ("Shuffle", "noun_shuffle"),
    ("Search", "noun_search"),
    ("Settings", "noun_gear"),
    ("MoreVert", "noun_threedot"),
    ("FavoriteBorder", "noun_love_outline"),
    ("Favorite", "noun_love"),
    ("Delete", "noun_delete"),
    ("Add", "noun_add"),
    ("Edit", "noun_edit"),
    ("Photo", "noun_image"),
    ("Image", "noun_image"),
    ("Person", "noun_artist"),
    ("Analytics", "noun_chart"),
    ("PlaylistAdd", "noun_queue"),
    ("ChevronLeft", "noun_arrow_left"),
    ("ChevronRight", "noun_arrow_right"),
    ("ArrowBackIosNew", "noun_arrow_left"),
    ("FileOpen", "noun_import"),
    ("FileUpload", "noun_import"),
    ("FiberManualRecord", "noun_record"),
    ("Sort", "noun_sort"),
]

VECTOR_RES_IMPORT = b"import androidx.compose.ui.res.vectorResource"
R_IMPORT = b"import com.ianocent.musicplayer.R"


def process(path):
    with open(path, "rb") as f:
        src = f.read()
    orig = src
    changed_counts = []
    for name, drawable in MAPPING:
        pat = br"Icons\.Rounded\." + name.encode() + rb"\b"
        src, n = re.subn(pat, br"vectorResource(R.drawable." + drawable.encode() + br")", src)
        if n:
            changed_counts.append((name, n))
    if not changed_counts:
        return None
    # drop imports for fully-replaced icons
    for name, _ in changed_counts:
        icon_pat = re.compile(
            rb"\r?\nimport androidx\.compose\.material\.icons\.rounded\." + name.encode() + rb"\r?\n")
        if not re.search(br"Icons\.Rounded\." + name.encode() + rb"\b", src):
            src = icon_pat.sub(b"\n", src)
    if b"Icons.Rounded." not in src and b"Icons.AutoMirrored." not in src:
        src = re.sub(rb"\r?\nimport androidx\.compose\.material\.icons\.Icons\r?\n", b"\n", src)
    # insert imports on their own newline style
    eol = b"\r\n" if b"\r\n" in src[:2000] else b"\n"
    nl_avail = eol + VECTOR_RES_IMPORT + eol
    if b"vectorResource(" in src and VECTOR_RES_IMPORT not in src:
        src = re.sub(rb"(package [^\r\n]+)", rb"\1" + nl_avail, src, count=1)
    if b"R.drawable." in src and R_IMPORT not in src:
        m = re.search(rb"^package ([\w.]+)$", src, re.M)
        if m and m.group(1) != b"com.ianocent.musicplayer":
            with_r = eol + R_IMPORT + eol
            src = re.sub(rb"(package [^\r\n]+)", rb"\1" + with_r, src, count=1)
    with open(path, "wb") as f:
        f.write(src)
    return changed_counts


def main():
    total = {}
    for dirpath, _, files in os.walk(ROOT):
        for fn in files:
            if fn.endswith(".kt"):
                p = os.path.join(dirpath, fn)
                res = process(p)
                if res:
                    rel = os.path.relpath(p, ROOT)
                    print(rel)
                    for name, n in res:
                        print(f"   {name}: {n}")
                        total[name] = total.get(name, 0) + n
    print("TOTALS:", {k: v for k, v in sorted(total.items(), key=lambda kv: -kv[1])})


if __name__ == "__main__":
    main()
