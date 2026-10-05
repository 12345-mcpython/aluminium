"""Build a Chinese -> official English glossary for the terms our comments quote (2026-10-02, read-only).

Every Chinese term in a comment can be resolved authoritatively: TextMapCHS maps a hash to the Chinese string, TextMapEN maps the same hash to the official English one. This collects the CJK runs
that actually appear in src/ comments and resolves the ones the game's own text map knows, so the glosses the agents had to guess can be replaced with the official wording.
"""
import io
import json
import os
import re
import subprocess

ROOT = r"E:\turnbasedgamedata"
CHS = json.load(io.open(os.path.join(ROOT, "TextMap", "TextMapCHS.json"), encoding="utf-8"))
EN = json.load(io.open(os.path.join(ROOT, "TextMap", "TextMapEN.json"), encoding="utf-8"))

CJK_RUN = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]{2,14}")


def comments(text):
    st = {"block": False, "text": False}
    for ln in text.split("\n"):
        flag = False
        j, n = 0, len(ln)
        while j < n:
            if st["block"]:
                k = ln.find("*/", j)
                flag = True
                j = n if k < 0 else k + 2
                if k >= 0:
                    st["block"] = False
                continue
            if st["text"]:
                k = ln.find('"""', j)
                end = n if k < 0 else k + 3
                j = end
                if k >= 0:
                    st["text"] = False
                continue
            if ln.startswith('"""', j):
                st["text"] = True
                j += 3
                continue
            if ln[j] in "\"'":
                q = ln[j]
                k = j + 1
                while k < n:
                    if ln[k] == "\\":
                        k += 2
                        continue
                    if ln[k] == q:
                        k += 1
                        break
                    k += 1
                j = k
                continue
            if ln.startswith("//", j):
                flag = True
                j = n
                continue
            if ln.startswith("/*", j):
                k = ln.find("*/", j + 2)
                flag = True
                if k < 0:
                    st["block"] = True
                    j = n
                else:
                    j = k + 2
                continue
            j += 1
        if flag:
            yield ln


terms = set()
files = [f for f in subprocess.run(["git", "ls-files"], capture_output=True, text=True).stdout.split()
         if f.endswith(".java") and f.startswith("src/")]
for f in files:
    try:
        text = io.open(f, encoding="utf-8").read()
    except Exception:
        continue
    for ln in comments(text):
        for m in CJK_RUN.finditer(ln):
            terms.add(m.group(0))

# reverse index, built only for the terms we need
want = {}
for h, v in CHS.items():
    if isinstance(v, str) and 2 <= len(v) <= 14 and v in terms:
        want.setdefault(v, h)

rows = []
for t in sorted(terms, key=lambda s: (-len(s), s)):
    h = want.get(t)
    if h is None:
        continue
    e = EN.get(h)
    if isinstance(e, str) and e and not re.search(r"[\u3400-\u4dbf\u4e00-\u9fff]", e):
        rows.append((t, e))
out = ["terms found in src/ comments: %d" % len(terms),
       "terms the game's text map resolves to an English string: %d" % len(rows), "",
       "  chinese            -> official english"]
seen = set()
for t, e in rows:
    if (t, e) in seen:
        continue
    seen.add((t, e))
    out.append("  %-18s -> %s" % (t, e[:64]))
io.open("tools/_glossary.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out[:400]))
print("terms %d ; resolved %d" % (len(terms), len(rows)))
