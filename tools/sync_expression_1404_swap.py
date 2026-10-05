"""Checklist sync: the turn-start sentence ships, and what is left is named (round 6 of the goal).

Ships now: 1404's 「【血仇】状态期间…自身回合开始时自动施放【弑王成王】」 -- gate + swap slot 9 + pay the cost + cast, with the swap proven
to reach the commanded cast by a same-level comparison (767.585 both ways; 628.024 without it).

Left, each with its reader and prerequisite:
  * 「充能 150」 needs the ≥100 rule to stop draining the charge (measured: that rule has no 【血仇】 gate);
  * 1415's memosprite skill 8 needs a COMMANDED CAST ON ANOTHER UNIT -- `CAST_SKILL` casts the rule owner's own slot, so a rule in
    another character's table cannot make 万敌 cast.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

PREFIX = "| **「自动施放【强化战技】」**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d §3 rows start with that prefix" % len(hits))
lines.pop(hits[0])
print("ok   the shipped half leaves §3")

# the shipped row
ROW = ("| **「【血仇】状态期间…自身回合开始时自动施放【弑王成王】」** "
       "| `TURN_START` ＋ `self has_state 血仇` ＋ **`REPLACE_SKILL{skill: SKILL, skill_id: 9, turns: 1}`** ＋ "
       "`CONSUME_HP{scale: target_current_hp, percent: 0.35}` ＋ `CAST_SKILL{skill: SKILL}` "
       "| `src/main/resources/characters/1404.json` "
       "| `MydeiBloodfeudSkillsTest` |")
ANCHOR = "| **「消耗等同于…**当前**生命值 X% 的生命值」**"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
lines.insert(target[0] + 1, ROW)
print("ok   §2 gains the turn-start autocast")

# what remains, as its own §3 row
ROW3 = ("| **「充能 150 时自动施放【弑神登神】」** 与 **「使万敌自动施放1次不消耗充能的【弑神登神】」** "
       "| 两件 ✗：① 「充能 ≥ 100」那条**没有【血仇】门** ✗ ⇒ **反复**触发、把充能抽干 ✗"
       "（实测 ✓）⇒ 攒不到 150 ✗；"
       "② 在**别人的表**里命令**他**施放 ✗ —— `CAST_SKILL` 放的是**规则拥有者自己**的槽 ✓（`requireCharacterOwner` ✓） "
       "| `1404`（1 位）、**`1415` 的忆灵技能 8**（1 位） **共 2 位** ✓ "
       "| ① 一个**否定条件**（如 `self not_state 血仇`），或让那条只在**跨过**阈值时触发 ✓；"
       "② 一种**命令他人施放**的写法（`CAST_SKILL` 加一个“谁来放”的寻址 ✓） |")
lines.append(ROW3)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   §3 names the two remaining blockers")
