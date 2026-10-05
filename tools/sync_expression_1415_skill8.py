"""Record the two measured conclusions of this round (round 16 of the goal).

1. 1415's memosprite skill 8 cannot be cast at all -- NOT because of a missing slot, but because it DELIVERS nothing:
   `skill_effects.json` has 21 cids and no 1415, the skill is tagged 辅助 (non-damaging), and `CAST_SKILL`'s guard refuses exactly
   "a commanded cast that would do nothing at all". So a skill whose whole job is to trigger other rules has no place in the model,
   and its 「对万敌施放时」 clause therefore has no trigger point. That is a NEW, sharper prerequisite.

2. The warehouse load point's wiring is now measured: the dispatch core asks `for (Character ally : characters)`, and `characters`
   IS the roster that acts (assigned from the queue at L520), so a listen-only unit cannot simply be put there. The clean shape is a
   SECOND loop over a listen-only table list inside the same dispatch, fed by its own loader.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**（`1415` 的忆灵技能 8 ✓） "
    "| ⭐⭐ **本轮测定：该技能根本无法被施放** ✗ —— ❗ 不是缺槽位 ✗，而是它**什么都不交付** ✗："
    "技能 8 是「**辅助**」（非伤害 ✓），而 `skill_effects.json`（21 个 cid ✓）**里没有 1415** ✗，"
    "而 `CAST_SKILL` 的护栏**拒绝的恰恰就是**“什么都不做的被命令施放” ✓ "
    "⇒ ⭐ **一个只触发规则、自身无交付的技能在当前模型里没有位置** ✗，"
    "因此它的「对万敌施放时」句子**没有触发点** ✗ "
    "| `1415`（1 位） "
    "| 一个**只触发规则**的技能能被承认（或给它一条 `skill_effects.json` 条目 ✓） |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the 1415 row now names what really blocks it")
