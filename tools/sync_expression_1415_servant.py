"""Register item 2 with the mechanism measured this round (round 12 of the goal).

Measured, all by reading:
  * `SkillEffects.forSkill` keys the table by `skill.getCid()` AND `skill.getSkillSlot()` -- the SKILL's own cid, not the master's.
  * `SummonFactory.servantWith` gives a memosprite exactly ONE skill: `summon.setSkill(SkillType.COMMON, attackOf(spec, ...))`, compiled
    from the panel's `attack` block. The game's 忆灵技能 1…18 have no rows in our engine at all.
  * so a `skill_effects.json` row for 1415's memosprite skill 8 would never be found -- and `"1415": {"8": …}` would attach to HER OWN
    slot 8, which is 「向着爱与明天♪」 (attack_type Normal, effect AoEAttack: a damaging skill), i.e. the wrong skill.
  * the game's shape, read from tbgd: 忆灵技能 8 is 「献予「纷争」之诗 / Ode to Strife」, 效果 辅助, 元素 无, 最高等级 10, 效果ID [10000001, 10000011].

So the row records what is actually missing: the memosprite's skills are not `Skill` objects, which is the prerequisite for any of this.
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
    "| **「使万敌自动施放1次不消耗充能的【弑神登神】」**（`1415` 忆灵技能 8 ✓） "
    "| ⭐ **本轮把缺什么重新钉到根上** ✓：❗ **忆灵的技能在我们引擎里不是 `Skill` 对象** ✗ —— "
    "★ `SummonFactory.servantWith` 只给忆灵**一个 `COMMON` 攻击** ✓（由面板的 `attack` 块编译 ✓），"
    "游戏里那 **1…18** 个忆灵技能**没有行** ✗；"
    "★ 而 `skill_effects.json` 按 **技能自己的 `cid` ＋ 槽位** 查表 ✓（`SkillEffects.forSkill` ✓）"
    "⇒ **写 `\"1415\": {\"8\": …}` 永远查不到** ✗，且会**挂错**到她自己的 slot 8 —— "
    "那是「向着爱与明天♪」✓（`attack_type: Normal` ✓、`skill_effect: AoEAttack` ✓，**是伤害类** ✗）。"
    "⚠ 游戏侧的形状已读出 ✓：忆灵技能 8 ＝ **献予「纷争」之诗 / Ode to Strife** ✓，"
    "效果 `辅助`、元素 `无`、最高等级 10、**效果ID `[10000001, 10000011]`** ✓。"
    "⭐ **不造**（❗ 读者 1 位 ✗，且这是一件**引擎能力** ✗ 而非数据行 ✓） "
    "| `1415`（1 位） "
    "| 让忆灵的技能成为 `Skill` 对象（★ 以 `11415` 为 cid ✓、技能号为槽位 ✓）；❗**读者不足 2 位前不造** ✓ |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   1415's row now names the real prerequisite")
