"""Apply the English-message fixes recorded in tools/_en_fix_*.py.

Every entry is (path, old, new) with RAW Java source text on both sides, so the
substitution is exact. Each file is only counted and reported; nothing is written
unless the old text is found at least once.
"""
import io
import os
import sys

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
tools = os.path.join(root, "tools")
sys.path.insert(0, tools)

FIXES = []
for name in sorted(os.listdir(tools)):
    if name.startswith("_en_fix_") and name.endswith(".py"):
        mod = __import__(name[:-3])
        for f in mod.FIXES:
            FIXES.append((name, f[0], f[1], f[2]))


def main(apply):
    ok = 0
    missing = []
    multi = []
    per_file = {}
    for src, path, old, new in FIXES:
        full = os.path.join(root, path)
        if not os.path.exists(full):
            missing.append((src, path, old, "NO FILE"))
            continue
        text = io.open(full, encoding="utf-8", newline="").read()
        n = text.count(old)
        if n == 0:
            missing.append((src, path, old, "NOT FOUND"))
            continue
        if n > 1:
            multi.append((path, n, old))
        if apply:
            if new in text and n == 1:
                missing.append((src, path, old, "ALREADY DONE"))
                continue
            text = text.replace(old, new)
            io.open(full, "w", encoding="utf-8", newline="").write(text)
        ok += 1
        per_file[path] = per_file.get(path, 0) + 1
    print(("APPLIED" if apply else "DRY-RUN") + ": %d entries, %d files" % (ok, len(per_file)))
    if multi:
        print("--- literals appearing more than once (all replaced) ---")
        for path, n, old in multi:
            print("  %dx %s : %s" % (n, path, old[:90]))
    if missing:
        print("--- NOT APPLIED (%d) ---" % len(missing))
        for src, path, old, why in missing:
            print("  %s %s : %s" % (why, path, old[:110]))


main("--apply" in sys.argv)
