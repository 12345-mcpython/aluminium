"""Residual prose finder: which Han text inside MESSAGE-classified string literals
is NOT a known content spelling and NOT already carrying an English gloss?

Compares against: tools/content_names.tsv keys, every Han literal the classifier
called DATA, every Han string value in src/main/resources, and quoted spans
(「」 【】 [] , {@code ...}) inside the message itself.
"""
import io
import json
import os
import re

HAN = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]+")
QUOTED = re.compile(r"[\u300c\u300e][^\u300d\u300f]*[\u300d\u300f]"      # 「」 『』
                    r"|[\u3010][^\u3011]*[\u3011]"                        # 【】
                    r"|\{[^}]*\}"                                        # {@code ...} / ...
                    r"|\[[^\[\]]*\]")                                    # [ ... ]
GLOSS = re.compile(r"\([\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]+\)")
ASCII_WORD = re.compile(r"[A-Za-z]")


def han_runs(s):
    return HAN.findall(s)


def load_known(root):
    known = set()
    p = os.path.join(root, "tools", "content_names.tsv")
    for ln in io.open(p, encoding="utf-8"):
        if ln.startswith("#"):
            continue
        parts = ln.rstrip("\n").split("\t")
        if parts and parts[0]:
            known.add(parts[0])
    # every Han literal anywhere in main resources (data spellings)
    res = os.path.join(root, "src", "main", "resources")
    for dirpath, dirnames, filenames in os.walk(res):
        for fn in filenames:
            fp = os.path.join(dirpath, fn)
            try:
                txt = io.open(fp, encoding="utf-8", errors="replace").read()
            except Exception:
                continue
            for m in re.finditer(r'"((?:[^"\\]|\\.)*)"', txt):
                v = m.group(1)
                if HAN.search(v):
                    known.add(v)
    # every Han literal in java main+test that is not a message: use the DATA census
    return known


def main():
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    known = load_known(root)
    # add DATA literals only (never the messages themselves) from the classifier report
    rep = io.open(os.path.join(root, "tools", "_en_class.txt"), encoding="utf-8").read()
    sec = None
    for ln in rep.split("\n"):
        if ln.startswith("=== "):
            sec = ln.split()[1]
            continue
        if sec == "DATA" and ln.startswith("   LIT "):
            known.add(ln[7:])

    # collect MESSAGE entries
    entries = []
    cur = None
    for ln in rep.split("\n"):
        m = re.match(r"^(\S+):(\d+)  \[(.*?)\]$", ln)
        if m:
            cur = [m.group(1), int(m.group(2)), m.group(3), None, None]
            entries.append(cur)
            continue
        if cur is not None and ln.startswith("   LIT "):
            cur[3] = ln[7:]
        elif cur is not None and ln.startswith("   LN  "):
            cur[4] = ln[7:]
    # only the first section (MESSAGE) is delimited by === headers; rebuild properly
    sec = None
    entries = []
    for ln in rep.split("\n"):
        if ln.startswith("=== "):
            sec = ln.split()[1]
            continue
        if sec != "MESSAGE":
            continue
        m = re.match(r"^(\S+):(\d+)  \[(.*?)\]$", ln)
        if m:
            cur = [m.group(1), int(m.group(2)), m.group(3), None, None]
            entries.append(cur)
            continue
        if entries and ln.startswith("   LIT "):
            entries[-1][3] = ln[7:]
        elif entries and ln.startswith("   LN  "):
            entries[-1][4] = ln[7:]

    out = []
    n_res = 0
    byfile = {}
    for rel, line, callee, lit, whole in entries:
        stripped = QUOTED.sub(" ", lit)
        stripped = GLOSS.sub(" ", stripped)
        for k in sorted(known, key=len, reverse=True):
            if k and k in stripped:
                stripped = stripped.replace(k, " ")
        res = han_runs(stripped)
        if not res:
            continue
        n_res += 1
        byfile.setdefault(rel, []).append((line, callee, lit, whole, res))
    out.append("MESSAGE literals with residual Han: %d in %d files" % (n_res, len(byfile)))
    out.append("")
    for rel in sorted(byfile):
        out.append("--- %s (%d)" % (rel, len(byfile[rel])))
        for line, callee, lit, whole, res in byfile[rel]:
            out.append("  :%d [%s] residual=%s" % (line, callee, "|".join(res)))
            out.append("     LIT %s" % lit)
            out.append("     LN  %s" % (whole[:240] if whole else ""))
    io.open(os.path.join(root, "tools", "_en_residual.txt"), "w", encoding="utf-8",
            newline="\n").write("\n".join(out))
    print(out[0])


main()
