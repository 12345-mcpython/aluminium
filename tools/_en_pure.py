"""List MESSAGE-classified literals that contain no ASCII letter (pure Han)."""
import io
import re

t = io.open("tools/_en_class.txt", encoding="utf-8").read()
sec = None
entries = []
for ln in t.split("\n"):
    if ln.startswith("=== "):
        sec = ln.split()[1]
        continue
    if sec != "MESSAGE":
        continue
    m = re.match(r"^(\S+):(\d+)  \[(.*?)\]$", ln)
    if m:
        entries.append([m.group(1), m.group(2), m.group(3), None, None])
        continue
    if entries and ln.startswith("   LIT "):
        entries[-1][3] = ln[7:]
    elif entries and ln.startswith("   LN  "):
        entries[-1][4] = ln[7:]

pure = [e for e in entries if e[3] and not re.search(r"[A-Za-z]", e[3])]
out = ["pure-Han MESSAGE literals: %d" % len(pure), ""]
for e in pure:
    out.append("%s:%s [%s]\n   LIT %s\n   LN  %s" % (e[0], e[1], e[2], e[3], (e[4] or "")[:200]))
io.open("tools/_en_pure.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print(out[0])
