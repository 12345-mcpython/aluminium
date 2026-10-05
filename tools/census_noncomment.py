"""Census: did any agent change something that is NOT a comment? (2026-10-02, read-only)

For every currently-modified file, compare HEAD against the working copy with the comment text REMOVED from both sides. Whatever remains is code: identifiers, statements, annotations and string
literals. If those differ, the change went beyond comments -- which the stated scope does not allow.

Line alignment survives comment-only edits because a line whose comment changed still contributes the same code part (an empty string when the line was a pure comment).
"""
import difflib
import io
import re
import subprocess
import sys


def code_only(text):
    """The code part of every line, with all comment text removed, keeping one entry per line."""
    st = {"block": False, "text": False}
    out = []
    for ln in text.split("\n"):
        pieces = []
        j, n = 0, len(ln)
        while j < n:
            if st["block"]:
                k = ln.find("*/", j)
                j = n if k < 0 else k + 2
                if k >= 0:
                    st["block"] = False
                continue
            if st["text"]:
                k = ln.find('"""', j)
                end = n if k < 0 else k + 3
                pieces.append(ln[j:end])
                j = end
                if k >= 0:
                    st["text"] = False
                continue
            if ln.startswith('"""', j):
                st["text"] = True
                pieces.append('"""')
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
                pieces.append(ln[j:k])
                j = k
                continue
            if ln.startswith("//", j):
                j = n
                continue
            if ln.startswith("/*", j):
                k = ln.find("*/", j + 2)
                if k < 0:
                    st["block"] = True
                    j = n
                else:
                    j = k + 2
                continue
            k = j
            while k < n and ln[k] not in "\"'/":
                k += 1
            if k == j:
                k = j + 1
            pieces.append(ln[j:k])
            j = k
        out.append("".join(pieces))
    return out


modified = subprocess.run(["git", "diff", "--name-only"], capture_output=True, text=True).stdout.split()
files = [f for f in modified if f.endswith(".java")]
bad = []
for f in files:
    head = subprocess.run(["git", "show", "HEAD:" + f], capture_output=True, text=True,
                          encoding="utf-8", errors="replace").stdout
    try:
        work = io.open(f, encoding="utf-8").read()
    except Exception:
        continue
    a, b = code_only(head), code_only(work)
    if a == b:
        continue
    sm = difflib.SequenceMatcher(None, a, b, autojunk=False)
    diffs = []
    for tag, i1, i2, j1, j2 in sm.get_opcodes():
        if tag == "equal":
            continue
        for x, y in zip(a[i1:i2], b[j1:j2]):
            if x != y:
                diffs.append((x.strip()[:96], y.strip()[:96]))
        if i2 - i1 != j2 - j1:
            diffs.append(("(line count %d)" % (i2 - i1), "(line count %d)" % (j2 - j1)))
    if diffs:
        bad.append((f, len(diffs), diffs[:4]))

print("modified java files: %d ; files with a NON-comment difference: %d" % (len(files), len(bad)))
out = ["modified java files: %d" % len(files),
       "files whose change is NOT comment-only: %d" % len(bad), ""]
for f, n, diffs in bad:
    out.append("%s  (%d differences)" % (f, n))
    for x, y in diffs:
        out.append("   - was: %s" % x.encode("ascii", "replace").decode("ascii"))
        out.append("     now: %s" % y.encode("ascii", "replace").decode("ascii"))
io.open("tools/_noncomment.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
