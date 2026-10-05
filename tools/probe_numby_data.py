"""Does the GAME DATA state a panel for 账账 / Numby, even though her page does not?

The GAPS entry recorded that the four subjects have no id in `monster_config.json`, but that was one file. This sweeps the
whole data directory for the servant block the memosprite notes quote as 「ServantID 11413 · 仇恨: 125」.
"""
import io
import os
import re

ROOTS = ["E:/turnbasedgamedata"]
out = []
for root in ROOTS:
    if not os.path.isdir(root):
        out.append("missing root: %s" % root)
        continue
    for base, dirs, files in os.walk(root):
        dirs[:] = [d for d in dirs if not d.startswith(".")]
        for name in files:
            if not name.lower().endswith((".json", ".txt", ".md", ".csv")):
                continue
            path = os.path.join(base, name)
            try:
                body = io.open(path, encoding="utf-8", errors="replace").read()
            except OSError:
                continue
            for keyword in ("账账", "Numby"):
                if keyword in body:
                    rel = os.path.relpath(path, root)
                    out.append("HIT %-58s (%s, %d chars)" % (rel, keyword, len(body)))
                    for match in list(re.finditer(re.escape(keyword), body))[:2]:
                        snippet = body[max(0, match.start() - 200):match.start() + 260].replace("\n", " ")
                        out.append("     ...%s..." % snippet[:400])
                    break
io.open("tools/_tmp_numby_data.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out) or "NOTHING FOUND")
print("written", len(out), "lines")
