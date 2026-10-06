"""Census of Chinese string literals still present in non-comment Java code.

Read-only helper for the English message pass: every string literal that
contains Han characters is reported with its file, line, raw text and a
classification hint based on the source line around it.
"""
import io
import os
import re
import sys

HAN = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")


def scan(path):
    """Yield (line, literal, whole_line) for every string literal with Han text."""
    text = io.open(path, encoding="utf-8", errors="replace").read()
    n = len(text)
    i = 0
    line = 1
    line_start = 0
    out = []
    while i < n:
        c = text[i]
        if c == "\n":
            line += 1
            i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            j = text.find("*/", i + 2)
            if j < 0:
                break
            line += text.count("\n", i, j)
            i = j + 2
            continue
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            line += text.count("\n", i, j)
            i = j
            continue
        if c == '"':
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == '"':
                    break
                if text[j] == "\n":
                    break
                j += 1
            lit = text[i:j + 1]
            if HAN.search(lit):
                ls = text.rfind("\n", 0, i) + 1
                le = text.find("\n", j)
                le = n if le < 0 else le
                out.append((line, lit, text[ls:le].strip()))
            i = j + 1
            continue
        if c == "'":
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == "'":
                    break
                j += 1
            i = j + 1
            continue
        i += 1
    return out


MSG = re.compile(r"(println|printf|print\()|(assert|Assertions|fail\()|(Exception|Error|throw)|(format\()")


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    rows = []
    for dirpath, dirnames, filenames in os.walk(os.path.join(root, "src")):
        dirnames[:] = [d for d in dirnames if d not in ("build", "target", ".git")]
        for fn in filenames:
            if not fn.endswith(".java"):
                continue
            p = os.path.join(dirpath, fn)
            rel = os.path.relpath(p, root).replace("\\", "/")
            for line, lit, whole in scan(p):
                rows.append((rel, line, lit, whole, bool(MSG.search(whole))))
    msg = [r for r in rows if r[4]]
    out = []
    out.append("total Han string literals in non-comment code: %d" % len(rows))
    out.append("on a message-ish line (println/assert/exception/format): %d" % len(msg))
    out.append("")
    out.append("=== message-ish lines ===")
    for rel, line, lit, whole, _ in msg:
        out.append("%s:%d\n    LIT  %s\n    LINE %s" % (rel, line, lit, whole[:300]))
    out.append("")
    out.append("=== other Han string literals (to check for data vs prose) ===")
    for rel, line, lit, whole, _ in rows:
        if not _:
            out.append("%s:%d\n    LIT  %s\n    LINE %s" % (rel, line, lit, whole[:300]))
    io.open(os.path.join(root, "tools", "_en_census.txt"), "w", encoding="utf-8",
            newline="\n").write("\n".join(out))
    print(out[0])
    print(out[1])


main()
