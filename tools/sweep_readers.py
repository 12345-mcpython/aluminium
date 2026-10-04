"""Sweep GAPS.md for every registered gap that claims two or more readers.

Objective ④ is about GAPS.md's second section, and previous rounds only sampled it. This scans the whole file: a line that
mentions readers with a number >= 2 is reported together with the nearest preceding heading, so the list can be walked
against the tree. Output to a UTF-8 file; console mangles Chinese.
"""
import io
import re

ROOT = "GAPS.md"
lines = io.open(ROOT, encoding="utf-8").read().split("\n")

heading = ""
found = []
NUMBER = re.compile(r"(?:读者|readers)[^\n]{0,24}?(\d+)\s*(?:位|个|名|files?|条)")
for index, line in enumerate(lines, start=1):
    stripped = line.strip()
    if stripped.startswith("#") or (stripped.startswith(">") and re.match(r"^>\s*\*\*20\d\d-", stripped)):
        heading = stripped[:110]
    match = NUMBER.search(line)
    if match and int(match.group(1)) >= 2:
        found.append((index, int(match.group(1)), heading, stripped[:200]))

out = ["lines: %d | entries with readers >= 2: %d" % (len(lines), len(found)), ""]
for index, count, head, text in found:
    out.append("L%-6d readers=%d" % (index, count))
    out.append("    HEAD %s" % head)
    out.append("    %s" % text)
io.open("tools/_tmp_readers.txt", "w", encoding="utf-8", newline="\n").write("\n".join(out))
print("written", len(found), "entries")
