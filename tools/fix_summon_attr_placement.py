"""Put the `summon_attr` load-time branch where it belongs (the first attempt hit a doc comment).

`// The spelling and (for the attribute family) the name; ...` appears twice -- once inside `scaleAttribute`'s body and once in the javadoc
of the scale helper near the DAMAGE path -- so the insertion landed in a comment and broke the file. The anchor here is
`String scale = effect.getScale().trim();`, the first line of `requireDerivedScale`'s body, which is checked for uniqueness.
"""
import io
import re
import sys

T = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
txt = io.open(T, encoding="utf-8").read()

# 1) take the misplaced block back out
block_start = txt.find("        // The owner MEMOSPRITE's attribute (2026-10-02).")
if block_start < 0:
    sys.exit("REFUSING: the misplaced block is not there")
block_end = txt.find("        // The spelling and (for the attribute family) the name", block_start)
if block_end < 0:
    sys.exit("REFUSING: could not find the end of the misplaced block")
block = txt[block_start:block_end]
txt = txt[:block_start] + txt[block_end:]
print("ok   took the misplaced block back out (%d chars)" % len(block))

# 2) put it inside requireDerivedScale, right after the scale is read
anchor = "        String scale = effect.getScale().trim();"
if txt.count(anchor) != 1:
    sys.exit("REFUSING: the requireDerivedScale anchor has %d occurrences" % txt.count(anchor))
i = txt.index(anchor)
line_end = txt.index("\n", i) + 1
txt = txt[:line_end] + block + txt[line_end:]
io.open(T, "w", encoding="utf-8", newline="\n").write(txt)
print("ok   the load-time branch now sits inside requireDerivedScale")
