"""The stale §3 row becomes a §2 row (2026-10-02, round 2 of the goal).

§3 said 1217's 「使【禳命】的持续回合数 −1」 needs "a shorten-duration spelling, because EXTEND_BUFF only takes positive turns".
Measured: the clause is not about shortening at all -- 「藿藿**每回合开始时**持续回合数减 1」 is about WHOSE CLOCK spends the duration, and
`ticks_on: "self"` already says it (the same field 8006's 伴舞, 星期日's 蒙福者 and the 结界 half use).
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| 「使【禳命】的持续回合数"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d §3 rows start with that prefix" % len(hits))
lines.pop(hits[0])
print("ok   the stale row is out of §3")

ROW = ("| **「某个状态的持续回合数在**它自己**的回合开始时减 1」** "
       "| `APPLY_BUFF` ＋ `turns` ＋ **`ticks_on: \"self\"`**（谁的回合花掉时长 ✓） "
       "| `src/main/resources/characters/1217.json` "
       "| `HuohuoTalismanDurationTest` |")
ANCHOR = "| **队友的状态结束时取一部分**"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
lines.insert(target[0], ROW)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   it is a §2 row now")
