import os, re
ROOT = r'C:\Users\uzuma\AndroidStudioProjects\IanPlayer\app\src\main\java'
for dp, _, fs in os.walk(ROOT):
    for fn in fs:
        if not fn.endswith('.kt'):
            continue
        p = os.path.join(dp, fn)
        b = open(p, 'rb').read()
        t = b.decode('utf-8')
        imp = re.compile(r'^import [^\r\n]+(?:\r?\nimport(?![^\r\n]*:) [^\r\n]+)*', re.M)
        # collect exact import lines, dedupe stale repeats of identical lines
        lines = t.splitlines(keepends=True)
        seen = {}
        changed = False
        out = []
        for ln in lines:
            m = re.match(r'^import ([^\r\n]+)\r?\n?$', ln)
            if m:
                key = m.group(1)
                if key in seen:
                    changed = True
                    # skip duplicate (but keep newline structure by dropping line fully)
                    continue
                seen[key] = True
            out.append(ln)
        if changed:
            open(p, 'wb').write(''.join(out).encode('utf-8'))
            print('deduped', os.path.relpath(p, ROOT))
