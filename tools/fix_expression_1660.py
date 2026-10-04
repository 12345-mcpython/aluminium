"""Move the new stackable-state row from §3 (where it landed) to §2 (where it belongs).

The anchor I picked for §2 was actually a §3 row -- the elation-segment row was moved into §3 back in round 1649 -- so the
guard caught the misplaced row by demanding a reader count in §3. This re-inserts it after the §2 row about 笑点.
"""
import io

PATH = "EXPRESSION.md"
text = io.open(PATH, encoding="utf-8").read()

START = "| **\u5e26\u8ba1\u6570\u7684\u72b6\u6001**\uff08\u201c\u5c06 N \u8ba1\u5165\u8be5\u72b6\u6001\u201d\uff09"
lines = text.split("\n")
misplaced = [i for i, line in enumerate(lines) if line.startswith(START)]
if len(misplaced) != 1:
    raise SystemExit("REFUSING: %d misplaced rows" % len(misplaced))
row = lines.pop(misplaced[0])
print("ok   row removed from where it landed")

ANCHOR = "| \u300c\u83b7\u5f97 N \u4e2a**\u7b11\u70b9**\u300d"
target = [i for i, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    raise SystemExit("REFUSING: %d §2 anchors" % len(target))
lines.insert(target[0] + 1, row)
print("ok   row inserted into §2 after the 笑点 row")

io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
