"""Sharpen 1415's row into something measurable (round 27 of the goal).

Measured this round:
  * `SkillExecutor.canDeliver` is exactly `skill.getData().getEffect().isDamaging() || deliverableSpec(skill) != null` (L580) -- so a
    commanded cast is refused only when a skill is BOTH non-damaging AND has no `skill_effects.json` entry.
  * `SkillData.isLoaded()` (`maxLevel > 0`) is the engine's own name for "this is a real row, not the placeholder the loader hands back
    for an id the data lacks". 「键在」 and 「有这技能」 are different facts.
  * `skills.json` is keyed by GAME SKILL ID, and all nine of 1415's rows are HER OWN kit (`141501`…`141519`: 普攻/战技/终结技/天赋/秘技).
    The memosprite's skills are NOT under her cid.
  * `skill_effects.json` (the non-damaging table, which DOES carry `1409` -- another memosprite master) has no 1415 entry.

So the row's "缺什么" becomes a measurement instead of a judgement: where does a summon's skill data live, and does 德谬歌 have a skill 8
row at all.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows" % len(hits))
lines[hits[0]] = (
    "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**（`1415` 的忆灵技能 8 ✓） "
    "| ⭐ 本轮把它从“判断”改成**可测的一步** ✓：❗ **该技能在数据里没有行** ✓ —— "
    "实测：★ `skills.json` 的键是**游戏技能 id** ✓，而 `1415` 的**九个行全是她自己的**（`141501`…`141519`：普攻／战技／终结技／天赋／秘技 ✓）"
    "，**忆灵的技能不在她名下** ✗；★ `skill_effects.json`（**非伤害**表 ✓，它**确实带** `1409` ✓ —— "
    "另一位忆灵主人 ✓）**没有 1415 条目** ✗。"
    "⚠ 而 `canDeliver` 的判据已量清 ✓：`isDamaging() ∨ deliverableSpec != null` ✓（`SkillExecutor:580` ✓） "
    "| `1415`（1 位） "
    "| 一次测量：★ **忆灵的技能数据放在哪** ✗（它到底有没有 skill 8 这一行 ✓） |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   1415's row is now a measurement")
