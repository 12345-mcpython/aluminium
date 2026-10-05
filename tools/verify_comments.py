"""Verify the comment cleanup: no symbols, and no Chinese prose left in src/ Java comments (2026-10-02).

Quotation-only Chinese is EXPECTED to remain: those strings are the identifiers the content JSON's `source`/`note` also carry, so they keep their characters by decision.

This reports, so a leftover is a number to drive to zero rather than a guess:
  * comment lines whose CJK is only inside quotations (expected, large);
  * comment lines with CJK PROSE outside quotations (must go to zero);
  * comment lines still holding a decorative symbol (must go to zero).
"""
import io
import re
import subprocess

CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
QUOTED = re.compile(r'"[^"]*"|\[[^\]]*\]|\{@code[^}]*\}|\{@link[^}]*\}|`[^`]*`')
SYM = set("\u2b50\u2605\u2606\u2705\u274c\u26d4\u26a0\u2713\u2717\u2192\u21d2\u203b\u25cf\u25cb\u25c6\u25a0\u25a1\u25b2\u25b3\u00b7\u2022\u2014\u2013\u2018\u2019\u201c\u201d\u2265\u2264\u2260\u00d7\u00f7\u221a\u00b1\u2190\u2191\u2193\u300c\u300d\u3010\u3011\u2500\u2502\u2514\u2518\u2049\u2757\u2753\u2139")


def walk(text, want_line=True):
    """Yield lines that lie inside a Java comment."""
    st = {"block": False, "text": False}
    for ln in text.split("\n"):
        flagged = False
        j, n = 0, len(ln)
        while j < n:
            if st["block"]:
                k = ln.find("*/", j)
                flagged = True
                j = n if k < 0 else k + 2
                if k >= 0:
                    st["block"] = False
                continue
            if st["text"]:
                k = ln.find('"""', j)
                j = n if k < 0 else k + 3
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
                flagged = True
                j = n
                continue
            if ln.startswith("/*", j):
                k = ln.find("*/", j + 2)
                flagged = True
                if k < 0:
                    st["block"] = True
                    j = n
                else:
                    j = k + 2
                continue
            j += 1
        if flagged:
            yield ln


files = [f for f in subprocess.run(["git", "ls-files"], capture_output=True, text=True).stdout.split()
         if f.endswith(".java") and f.startswith("src/")]
quote_only = 0
prose = []
symbols = []
for f in files:
    try:
        text = io.open(f, encoding="utf-8").read()
    except Exception:
        continue
    for i, ln in enumerate(walk(text)):
        if any(c in SYM or ord(c) > 0x1F000 for c in ln):
            symbols.append("%s: %s" % (f, ln.strip()[:110]))
        if CJK.search(ln):
            if CJK.search(QUOTED.sub(" ", ln)):
                prose.append("%s: %s" % (f, ln.strip()[:110]))
            else:
                quote_only += 1

out = ["src/ java files scanned: %d" % len(files),
       "quotation-only Chinese comment lines (expected): %d" % quote_only,
       "PROSE with Chinese left (target 0): %d" % len(prose),
       "comment lines with a decorative symbol (target 0): %d" % len(symbols), ""]
out.append("=== prose left, first 30 ===")
out += ["  " + x for x in prose[:30]]
out.append("")
out.append("=== symbols left, first 15 ===")
out += ["  " + x for x in symbols[:15]]
io.open("tools/_verify.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("quote-only %d ; prose left %d ; symbols left %d" % (quote_only, len(prose), len(symbols)))
