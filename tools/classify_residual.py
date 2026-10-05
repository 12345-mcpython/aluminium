"""Split the residual Chinese into "deliberate proper-noun gloss" and "prose still to translate" (2026-10-02).

An `English (中文)` gloss is the repo's own convention and the user's rule 2 keeps quotations; a Chinese sentence that is not a gloss is prose that still owes a translation. This tells the two
apart so the remaining work is a number, not an impression.
"""
import io
import re
import subprocess

CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]")
MASK = re.compile(r'"[^"]*"|\[[^\]]*\]|\{@code[^}]*\}|\{@link[^}]*\}|`[^`]*`')
GLOSS = re.compile(r"\(\s*[\u3400-\u4dbf\u4e00-\u9fff][^()]*\)")      # (中文...)
SYM = set("\u2b50\u2605\u2606\u2705\u274c\u26d4\u26a0\u2713\u2717\u2192\u21d2\u203b\u25cf\u25cb\u25c6\u25a0\u25a1\u25b2\u25b3\u00b7\u2022\u2014\u2013\u2018\u2019\u201c\u201d\u2265\u2264\u2260\u00d7\u00f7\u221a\u00b1\u2190\u2191\u2193\u300c\u300d\u3010\u3011\u2500\u2502\u2514\u2518\u2049")


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
        if flag: yield i + 1, ln


files = [f for f in subprocess.run(["git", "ls-files"], capture_output=True, text=True).stdout.split()
         if f.endswith(".java") and f.startswith("src/")]
gloss_only = []
real = []
symbols = []
by_file = {}
for f in files:
    try:
        text = io.open(f, encoding="utf-8").read()
    except Exception:
        continue
    for ln_no, ln in comments(text):
        if any(c in SYM or ord(c) > 0x1F000 for c in ln):
            symbols.append("%s:%d: %s" % (f, ln_no, ln.strip()[:110]))
        if not CJK.search(ln):
            continue
        # remove every quotation-ish span, then every gloss, then look for Chinese again
        stripped = GLOSS.sub(" ", MASK.sub(" ", ln))
        if CJK.search(stripped):
            real.append("%s:%d: %s" % (f, ln_no, ln.strip()[:118]))
            by_file[f] = by_file.get(f, 0) + 1
        else:
            gloss_only.append("%s:%d: %s" % (f, ln_no, ln.strip()[:100]))

out = ["comment lines whose Chinese is only a proper-noun gloss (deliberate): %d" % len(gloss_only),
       "comment lines with Chinese prose left (work): %d" % len(real),
       "comment lines with a decorative symbol (work): %d" % len(symbols), "",
       "=== prose left, by file (top 20) ==="]
for f, n in sorted(by_file.items(), key=lambda kv: -kv[1])[:20]:
    out.append("  %4d  %s" % (n, f))
out.append("")
out.append("=== prose left, first 30 lines ===")
out += ["  " + x for x in real[:30]]
out.append("")
out.append("=== symbols left ===")
out += ["  " + x for x in symbols[:10]]
io.open("tools/_classify.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("gloss-only %d ; prose %d ; symbols %d ; files with prose %d"
      % (len(gloss_only), len(real), len(symbols), len(by_file)))
