"""Checklist sync: the 150-charge sentence ships, the negation gets a row, and one piece remains (round 7 of the goal).

Ships:
  * 1404's 「【血仇】状态期间充能达到 150 点时，万敌立即获得 1 个额外回合并自动施放【弑神登神】」 -- spend 150, extra turn, swap slot 11, cast.
    Readings: charge 0, the enemy takes 1953.85 (against 767.585 for the plain row, i.e. 110% of max HP), and the slot reads 11.
  * the ≥100 rule's gate `!self has_state 血仇`. Measured before it: the charge arriving in 【血仇】 was drained again, so 150 was
    unreachable and the sentence above could never fire.
  * the negation spelling itself is worth a §2 row: it was ALREADY supported (the `!` prefix is parsed, and `has_state` reads the
    unit's buffs, which is the negatable family) -- but nothing in the tree used it until now.

Remains (one piece, one reader): 1415's memosprite skill 8 needs a COMMANDED CAST ON ANOTHER UNIT.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

PREFIX = "| **「充能 150 时自动施放【弑神登神】」**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d §3 rows start with that prefix" % len(hits))
lines.pop(hits[0])
print("ok   the shipped sentence leaves §3")

ANCHOR = "| **「【血仇】状态期间…自身回合开始时自动施放【弑王成王】」**"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
ROWS = [
    ("| **「【血仇】状态期间充能达到 **150** 点时，立即获得 1 个额外回合并自动施放【弑神登神】」** "
     "| `SPEND_RESOURCE{150}` ＋ `EXTRA_TURN` ＋ **`REPLACE_SKILL{skill: SKILL, skill_id: 11, turns: 1}`** ＋ "
     "`CAST_SKILL{skill: SKILL, target: self}` "
     "| `src/main/resources/characters/1404.json` "
     "| `MydeiBloodfeudSkillsTest` |"),
    ("| **「**未**处于【X】状态时…」**（否定一个状态条件） "
     "| **`!self has_state X`**（`!` 前缀 ✓，只允许否定 `PartyCondition` ✓，而 `has_state` 读的就是单位的增益 ✓） "
     "| `src/main/resources/characters/1404.json` "
     "| `MydeiBloodfeudSkillsTest` |"),
]
for offset, row in enumerate(ROWS):
    lines.insert(target[0] + 1 + offset, row)
print("ok   §2 gains the 150-charge sentence and the negation spelling")

ROW3 = ("| **「使万敌自动施放1次不消耗充能的【弑神登神】」**（`1415` 的忆灵技能 8 ✓） "
        "| 在**别人的表**里命令**他**施放 ✗ —— `CAST_SKILL` 放的是**规则拥有者自己**的槽 ✓"
        "（`requireCharacterOwner` ✓；实测：把 `target` 写成事件的目标时它会命令**敌人**施放 ✗ —— 装载器报 *“冰锋 has no SKILL skill”* ✓） "
        "| `1415`（1 位） "
        "| 一种**命令他人施放**的写法（`CAST_SKILL` 加一个“谁来放”的寻址 ✓） |")
lines.append(ROW3)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   §3 keeps the one remaining piece with its reader")
