"""Checklist sync for the shipped per-wave clause (round 20 of the goal).

Ships: 1309's 「领域展开期间进入战斗后，每个波次开始时知更鸟恢复 5 点能量」 -- `on: WAVE_START`, gated on the repo's own 秘技 convention
(`self has_state 秘技`, the same one 1408's technique rule uses), paying `GAIN_ENERGY{5, self}`. Judged two-way: 5.0 with the field,
0.0 without it.

It also records the measured shape of the family: 12 documents state a 「每个波次开始时…」 clause and only 1310 had a WAVE_START rule, so
this was an unshipped, plainly writable reader -- while the LIMIT form (「每个波次最多触发 1 次」, 1506) is the larger piece designed but
not yet built.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
ANCHOR = "| **\u300c\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
ROW = ("| **\u300c\u6bcf\u4e2a\u6ce2\u6b21\u5f00\u59cb\u65f6\u2026\u300d** "
       "| `on: \"WAVE_START\"`\uff08\u4e8b\u4ef6\u672c\u5c31\u5b58\u5728 \u2713\uff1b\u26a0 \u5b83\u4e0d\u643a\u5e26 actor\uff0ftarget \u2717 \u21d2 \u6761\u4ef6\u53ea\u80fd\u95ee `self`\uff0f\u8d44\u6e90 \u2713\uff09 "
       "| `src/main/resources/characters/1309.json` "
       "| `RobinWaveEnergyTest` |")
lines.insert(target[0], ROW)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   §2 gains the per-wave clause")
