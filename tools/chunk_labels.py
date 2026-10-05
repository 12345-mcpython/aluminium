"""List every comment line that still mentions a plan label, and split them into work batches (2026-10-02).

These are the spots where the label sits INSIDE a sentence, so removing it needs the sentence rewritten rather than the token deleted. The list is exact (file + line + text) so the next pass has no
guessing to do. Files are grouped 4 to a batch, balanced by count.
"""
import io
import re
import subprocess

LABEL = re.compile(r"\bP\d{1,2}(?:-\d{1,2})?\b|\bM-\d{1,3}\b|\bL-\d{1,3}\b|\bH-\d\b|Phase\s+\d+|ROADMAP")
MARKER = re.compile(r"^(\s*(?://|/\*+|\*+/?)[ \t]?)(.*)$")


def split_line(ln, st):
    code, comment = [], []
    i, n = 0, len(ln)
    while i < n:
        if st["block"]:
            k = ln.find("*/", i)
            end = n if k < 0 else k + 2
            comment.append(ln[i:end])
            i = end
            if k >= 0:
                st["block"] = False
            continue
        if st["text"]:
            k = ln.find('"""', i)
            end = n if k < 0 else k + 3
            code.append(ln[i:end])
            i = end
            if k >= 0:
                st["text"] = False
            continue
        if ln.startswith('"""', i):
            st["text"] = True
            code.append('"""')
            i += 3
            continue
        if ln[i] in "\"'":
            q = ln[i]
            k = i + 1
            while k < n:
                if ln[k] == "\\":
                    k += 2
                    continue
                if ln[k] == q:
                    k += 1
                    break
                k += 1
            code.append(ln[i:k])
            i = k
            continue
        if ln.startswith("//", i):
            comment.append(ln[i:])
            i = n
            continue
        if ln.startswith("/*", i):
            k = ln.find("*/", i + 2)
            if k < 0:
                st["block"] = True
                comment.append(ln[i:])
                i = n
            else:
                comment.append(ln[i:k + 2])
                i = k + 2
            continue
        k = i
        while k < n and ln[k] not in "\"'/":
            k += 1
        if k == i:
            k = i + 1
        code.append(ln[i:k])
        i = k
    return "".join(code), "".join(comment)


rows = []
for f in subprocess.run(["git", "ls-files"], capture_output=True, text=True).stdout.split():
    if not (f.endswith(".java") and f.startswith("src/")):
        continue
    try:
        text = io.open(f, encoding="utf-8").read()
    except Exception:
        continue
    st = {"block": False, "text": False}
    for i, ln in enumerate(text.split("\n")):
        code, comment = split_line(ln, st)
        if comment and LABEL.search(comment):
            rows.append((f, i + 1, ln.rstrip()))

by_file = {}
for f, n, ln in rows:
    by_file.setdefault(f, []).append((n, ln))
order = sorted(by_file.items(), key=lambda kv: -len(kv[1]))
BATCHES = 4
buckets = [[] for _ in range(BATCHES)]
load = [0] * BATCHES
for f, items in order:
    i = load.index(min(load))
    buckets[i].append((f, items))
    load[i] += len(items)
for i, b in enumerate(buckets, 1):
    lines = ["# batch %d: %d files, %d lines mentioning a plan label in a sentence\n" % (i, len(b), sum(len(x[1]) for x in b))]
    for f, items in b:
        lines.append("%s" % f)
        for n, ln in items:
            lines.append("  %d: %s" % (n, ln.strip()))
        lines.append("")
    io.open("tools/_labelbatch%d.txt" % i, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
    print("batch %d: %d files, %d lines" % (i, len(b), sum(len(x[1]) for x in b)))
print("total: %d lines in %d files" % (len(rows), len(by_file)))
