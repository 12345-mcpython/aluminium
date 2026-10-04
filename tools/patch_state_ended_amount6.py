"""Remove the in-loop announcement, accepting EITHER form of the call.

The tree at HEAD had `battle.fireStateEnded(instance, buff.getState());` (two arguments), so the earlier regex -- written for
the three-argument form -- matched nothing and the patch stopped after its first two edits. Both forms are matched here.
"""
import io
import re
import sys

MANAGER = "src/main/java/com/laosun/aluminium/models/buff/BuffManager.java"
text = io.open(MANAGER, encoding="utf-8").read()

BLOCK = re.compile(
    r"[ \t]*if \(battle != null\) \{\s*\n"
    r"[ \t]*battle\.fireStateEnded\(instance, buff\.getState\(\)[^;]*\);\s*\n"
    r"[ \t]*\}\n")
text, count = BLOCK.subn("", text)
print("in-loop blocks removed: %d" % count)
if count != 1:
    sys.stderr.write("REFUSING: expected exactly one\n")
    raise SystemExit(1)
io.open(MANAGER, "w", encoding="utf-8", newline="\n").write(text)
print("ok   the in-loop announcement is gone")

lines = io.open(MANAGER, encoding="utf-8").read().split("\n")
start = next(index for index, line in enumerate(lines) if "public int removeState" in line)
print("\n--- removeState, read back ---")
for index in range(start, min(start + 32, len(lines))):
    print("%d: %s" % (index + 1, lines[index].strip()[:118]))
