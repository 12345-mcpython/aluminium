"""Checklist sync for the shipped class filter (round 15 of the goal).

Ships: `debuff_class:control|dot` -- the family of the debuff that just landed, recorded at the one chokepoint every landed debuff
passes through (and already read there for the resistance roll). Two-way judged: the 控制类 rule fires (ATTACK +273.42), the 持续伤害类
rule does not (DEFENCE +0.0), with the landing copied from the green `ControlImmunityTest`.

That closes the biggest blocked row: 1506's warehouse skill needed three things, and the class filter was one. It also makes 1415's
「解除万敌陷入的所有控制类负面状态」 expressible.

What the row still waits on is now only: whether that memosprite skill can be cast at all (its `skill_effects.json` entry), which is a
thing to MEASURE rather than assume.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

# §3: the merged row's blocker is gone for the filter, so the row is rewritten around what is actually left
PREFIX = "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**（`1415` 的忆灵技能 8 ✓） "
    "| ⭐ 两个曾经的前置**都已出货** ✓：“命令他人施放” ✓（`CAST_SKILL` 的施放者就是 `target` ✓，`1412` 在用 ✓）"
    "与“**控制类**筛选” ✓（`debuff_class:control` ✓，本轮出货 ✓）。"
    "❗ 真正剩下的：该**忆灵技能 8 本身能不能被施放** ✗（它需要 `skill_effects.json` 条目 ✓）—— ⚠ 这件要**测**，不能假设 ✓ "
    "| `1415`（1 位） "
    "| 一次测量：该技能能否施放 ✓ |")

# §2: the shipped spelling
ANCHOR = "| **「**未**处于【X】状态时…」**"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
ROW = ("| **「施加的是**控制类**／**持续伤害类**负面状态」** "
       "| **`debuff_class:control`**／**`debuff_class:dot`**（刚落地的负面状态的**族** ✓；"
       "事实在咽喉点记录 ✓，而那里本来就读它算抗性 ✓） "
       "| `src/main/java/com/laosun/aluminium/Battle.java`、`src/main/java/com/laosun/aluminium/models/TriggerTable.java` "
       "| `DebuffClassConditionTest` |")
lines.insert(target[0] + 1, ROW)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   §2 gains the class filter; the blocked row now names one measurement")
