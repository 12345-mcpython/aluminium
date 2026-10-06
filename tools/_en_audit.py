"""Audit: for every fix entry, list each occurrence line and flag the ones that
fall inside a comment or a text block (those must not be touched)."""
import io
import os
import re
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


def comment_spans(text):
    """[(start, end)] for // and /* */ comments and text blocks."""
    spans = []
    n = len(text)
    i = 0
    while i < n:
        c = text[i]
        if c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            i = j + 1
            continue
        if c == "'":
            j = i + 1
            while j < n and text[j] != "'":
                j += 2 if text[j] == "\\" else 1
            i = j + 1
            continue
        if text.startswith('"""', i):
            j = text.find('"""', i + 3)
            j = n if j < 0 else j + 3
            spans.append((i, j))
            i = j
            continue
        if text.startswith("//", i):
            j = text.find("\n", i)
            j = n if j < 0 else j
            spans.append((i, j))
            i = j
            continue
        if text.startswith("/*", i):
            j = text.find("*/", i + 2)
            j = n if j < 0 else j + 2
            spans.append((i, j))
            i = j
            continue
        i += 1
    return spans


def line_of(text, pos):
    return text.count("\n", 0, pos) + 1


bad = 0
for src, path, old, new in FIXES:
    full = os.path.join(root, path)
    if not os.path.exists(full):
        continue
    text = io.open(full, encoding="utf-8", newline="").read()
    spans = comment_spans(text)
    hits = []
    start = 0
    while True:
        k = text.find(old, start)
        if k < 0:
            break
        in_comment = any(a <= k < b for a, b in spans)
        hits.append((line_of(text, k), in_comment))
        start = k + 1
    if len(hits) > 1:
        bad += 1
        print("%s : %s" % (path, old[:70]))
        for line, com in hits:
            print("     line %d %s" % (line, "COMMENT" if com else "code"))
print("entries with more than one occurrence: %d" % bad)
