"""Roll the unverified judge back, correct the register, and name the next step (round 14 of the goal).

Measured this round:
  * `castSkill` resolves its CASTER as `resolveTarget(effect, ctx)` -- NOT the rule owner -- and 1412's shipped `coup_de_main` writes
    `CAST_SKILL{skill: SKILL, target: "attacker"}`. So "command another unit to cast" is ALREADY shipped, and the §3 row's stated
    prerequisite for 1415's clause is wrong.
  * `holderOf` searches the OWNER'S WHOLE SIDE (`battle.getSideOf(ctx.owner())`), so `holder_of:<state>` can name any ally -- it is
    exactly the selector a sentence like 「对万敌施放时…」 needs.
  * a trap worth recording: `normalizeTarget` LOWERCASES the selector, so a state named `probeMark` is looked up as `probemark`.
  * and my first judge for this did not fire: neither the commander's CAST_SKILL nor the commanded unit's own watcher produced a mark.
    That is a READING problem of mine, not proof about the op -- 1412's shipped rule already exercises the op -- so the judge and its
    mutator come out rather than stay unverified, and the next step is to copy `CoupDeMainTest`'s drive.

The register therefore changes: 1415's remaining prerequisite is NOT "command another unit" (it exists) but the same 「控制类」 filter
its dispel half needs, so that row merges with the blocked one.
"""
import io
import os
import sys

for path in ("src/test/java/com/laosun/aluminium/test/CastSkillCommandsAnotherUnitTest.java",
             "tools/mut_command_another.py"):
    if os.path.exists(path):
        os.remove(path)
        print("ok   removed %s" % os.path.basename(path))

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**（`1415` 的忆灵技能 8 ✓） "
    "| ⭐ 订正：“命令**他人**施放”**不是**前置了 ✓ —— 它已经出货 ✓："
    "`castSkill` 的施放者就是 `resolveTarget(effect, ctx)` ✓（**不是**规则拥有者 ✗），而 `1412` 的 `coup_de_main` 写的就是 "
    "`CAST_SKILL{skill: SKILL, target: \"attacker\"}` ✓；且 `holder_of:<状态>` **搜的是整队** ✓（`battle.getSideOf(ctx.owner())` ✓）。"
    "❗ **真正剩下的前置**：同一句的另一半 「解除万敌陷入的所有**控制类**负面状态」✗ ⇒ 合并到下一行的筛选缺口 ✓。"
    "⚠ 小陷阱：`normalizeTarget` **会把选择器小写化** ✗ ⇒ 状态名要小写 ✓ "
    "| `1415`（1 位） "
    "| 同下一行（一个**“控制类”筛选** ✓） |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the row is corrected and merged with the blocked filter")
