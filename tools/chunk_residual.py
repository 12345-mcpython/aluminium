"""Split the files with residual Chinese prose into balanced batches, and keep the glossary as a reference (2026-10-02)."""
import io
import re
import subprocess

# 1. the glossary, as a durable reference the agents can consult
rows = []
for ln in io.open("tools/_glossary.txt", encoding="utf-8").read().split("\n"):
    m = re.match(r"^\s+(\S+)\s+-> (.+)$", ln)
    if m:
        rows.append((m.group(1), m.group(2).strip()))
io.open("tools/content_names.tsv", "w", encoding="utf-8", newline="\n").write(
    "# chinese\tofficial english (from tbgd TextMapCHS -> TextMapEN)\n" +
    "\n".join("%s\t%s" % (a, b) for a, b in rows) + "\n")
print("glossary: %d names -> tools/content_names.tsv" % len(rows))

# 2. batches over the files that still hold prose
CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
MASK = re.compile(r'"[^"]*"|\[[^\]]*\]|\{@code[^}]*\}|\{@link[^}]*\}|`[^`]*`')
GLOSS = re.compile(r"\(\s*[\u3400-\u4dbf\u4e00-\u9fff][^()]*\)")


def comments(text):
    st = {"block": False, "text": False}
    for i, ln in enumerate(text.split("\n")):
        flag = False
        j, n = 0, len(ln)
        while j < n:
            if st["block"]:
                k = ln.find("*/", j); flag = True; j = n if k < 0 else k + 2
                if k >= 0: st["block"] = False
                continue
            if st["text"]:
                k = ln.find('"""', j); j = n if k < 0 else k + 3
                if k >= 0: st["text"] = False
                continue
            if ln.startswith('"""', j): st["text"] = True; j += 3; continue
            if ln[j] in "\"'":
                q = ln[j]; k = j + 1
                while k < n:
                    if ln[k] == "\\": k += 2; continue
                    if ln[k] == q: k += 1; break
                    k += 1
                j = k; continue
            if ln.startswith("//", j): flag = True; j = n; continue
            if ln.startswith("/*", j):
                k = ln.find("*/", j + 2); flag = True
                if k < 0: st["block"] = True; j = n
                else: j = k + 2
                continue
            j += 1
        if flag: yield ln


rows = []
for f in subprocess.run(["git", "ls-files"], capture_output=True, text=True).stdout.split():
    if not (f.endswith(".java") and f.startswith("src/")):
        continue
    try:
        text = io.open(f, encoding="utf-8").read()
    except Exception:
        continue
    n = 0
    for ln in comments(text):
        if CJK.search(ln) and CJK.search(GLOSS.sub(" ", MASK.sub(" ", ln))):
            n += 1
    if n:
        rows.append((n, f))
rows.sort(reverse=True)
BATCHES = 5
buckets = [[] for _ in range(BATCHES)]
load = [0] * BATCHES
for n, f in rows:
    i = load.index(min(load))
    buckets[i].append((n, f))
    load[i] += n
for i, b in enumerate(buckets, 1):
    io.open("tools/_batch%d.txt" % i, "w", encoding="utf-8", newline="\n").write(
        "# batch %d: %d files, %d lines still holding Chinese prose\n\n" % (i, len(b), sum(x[0] for x in b)) +
        "\n".join("%s  (%d)" % (f, n) for n, f in b))
    print("batch %d: %d files, %d lines" % (i, len(b), sum(x[0] for x in b)))
print("total files %d ; lines %d" % (len(rows), sum(r[0] for r in rows)))
