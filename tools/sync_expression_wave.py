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
ANCHOR = "| **「获得该角色即生效"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
ROW = ("| **「每个波次开始时…」** "
       "| `on: \"WAVE_START\"`（事件本就存在 ✓；⚠ 它不携带 actor／target ✗ ⇒ 条件只能问 `self`／资源 ✓） "
       "| `src/main/resources/characters/1309.json` "
       "| `RobinWaveEnergyTest` |")
lines.insert(target[0], ROW)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   §2 gains the per-wave clause")
