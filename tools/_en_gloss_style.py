"""How are Chinese terms glossed inside message strings today?

Counts, over every Han-containing string literal, the three shapes:
  A) English term followed by Chinese in parens   -> "Ice Edge (冰锋)"
  B) Chinese term followed by English in parens   -> "解除 (dispelling)"
  C) bare Chinese term, no gloss
"""
import io
import os
import re

HAN = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")
A = re.compile(r"[A-Za-z][A-Za-z0-9'’\u00b7 \-]{0,24}\(([\u4e00-\u9fff]{1,12})\)")
B = re.compile(r"([\u4e00-\u9fff]{1,8})\s*\(([A-Za-z][^()]{0,60})\)")

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
a = b = 0
ex_a, ex_b = [], []
for dirpath, dirnames, filenames in os.walk(os.path.join(root, "src")):
    for fn in filenames:
        if not fn.endswith(".java"):
            continue
        p = os.path.join(dirpath, fn)
        rel = os.path.relpath(p, root).replace("\\", "/")
        for i, ln in enumerate(io.open(p, encoding="utf-8", errors="replace"), 1):
            if not HAN.search(ln):
                continue
            if ln.lstrip().startswith(("*", "//")):
                continue
            for m in A.finditer(ln):
                a += 1
                if len(ex_a) < 25:
                    ex_a.append("%s:%d  %s" % (rel, i, m.group(0)))
            for m in B.finditer(ln):
                b += 1
                if len(ex_b) < 25:
                    ex_b.append("%s:%d  %s" % (rel, i, m.group(0)))
print("A) english (chinese) glosses: %d" % a)
print("B) chinese (english) glosses: %d" % b)
print()
print("--- A examples ---")
print("\n".join(ex_a))
print()
print("--- B examples ---")
print("\n".join(ex_b))
