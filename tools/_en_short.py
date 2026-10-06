"""Compact view of the work list: one line per literal (path:line TAB literal)."""
import io
import re

t = io.open("tools/_en_todo.txt", encoding="utf-8").read()
out = []
for ln in t.split("\n"):
    if ln.startswith("--- ") or ln.startswith("literals"):
        out.append(ln)
        continue
    m = re.match(r"^  :(\d+) \[(.*?)\]$", ln)
    if m:
        cur = [m.group(1), m.group(2)]
        out.append(cur)
        continue
    if out and isinstance(out[-1], list) and ln.startswith("     LIT "):
        out[-1].append(ln[9:])
lines = []
for e in out:
    if isinstance(e, str):
        lines.append(e)
    else:
        lines.append("%s  [%s]  %s" % (e[0], e[1], e[2] if len(e) > 2 else ""))
io.open("tools/_en_todo_short.txt", "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print(len(lines))
