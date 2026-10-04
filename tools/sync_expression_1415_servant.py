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
PREFIX = "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows" % len(hits))
lines[hits[0]] = (
    "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**\uff08`1415` \u5fc6\u7075\u6280\u80fd 8 \u2713\uff09 "
    "| \u2b50 **\u672c\u8f6e\u628a\u7f3a\u4ec0\u4e48\u91cd\u65b0\u9489\u5230\u6839\u4e0a** \u2713\uff1a\u2757 **\u5fc6\u7075\u7684\u6280\u80fd\u5728\u6211\u4eec\u5f15\u64ce\u91cc\u4e0d\u662f `Skill` \u5bf9\u8c61** \u2717 \u2014\u2014 "
    "\u2605 `SummonFactory.servantWith` \u53ea\u7ed9\u5fc6\u7075**\u4e00\u4e2a `COMMON` \u653b\u51fb** \u2713\uff08\u7531\u9762\u677f\u7684 `attack` \u5757\u7f16\u8bd1 \u2713\uff09\uff0c"
    "\u6e38\u620f\u91cc\u90a3 **1\u202618** \u4e2a\u5fc6\u7075\u6280\u80fd**\u6ca1\u6709\u884c** \u2717\uff1b"
    "\u2605 \u800c `skill_effects.json` \u6309 **\u6280\u80fd\u81ea\u5df1\u7684 `cid` \uff0b \u69fd\u4f4d** \u67e5\u8868 \u2713\uff08`SkillEffects.forSkill` \u2713\uff09"
    "\u21d2 **\u5199 `\"1415\": {\"8\": \u2026}` \u6c38\u8fdc\u67e5\u4e0d\u5230** \u2717\uff0c\u4e14\u4f1a**\u6302\u9519**\u5230\u5979\u81ea\u5df1\u7684 slot 8 \u2014\u2014 "
    "\u90a3\u662f\u300c\u5411\u7740\u7231\u4e0e\u660e\u5929\u266a\u300d\u2713\uff08`attack_type: Normal` \u2713\u3001`skill_effect: AoEAttack` \u2713\uff0c**\u662f\u4f24\u5bb3\u7c7b** \u2717\uff09\u3002"
    "\u26a0 \u6e38\u620f\u4fa7\u7684\u5f62\u72b6\u5df2\u8bfb\u51fa \u2713\uff1a\u5fc6\u7075\u6280\u80fd 8 \uff1d **\u732e\u4e88\u300c\u7eb7\u4e89\u300d\u4e4b\u8bd7 / Ode to Strife** \u2713\uff0c"
    "\u6548\u679c `\u8f85\u52a9`\u3001\u5143\u7d20 `\u65e0`\u3001\u6700\u9ad8\u7b49\u7ea7 10\u3001**\u6548\u679cID `[10000001, 10000011]`** \u2713\u3002"
    "\u2b50 **\u4e0d\u9020**\uff08\u2757 \u8bfb\u8005 1 \u4f4d \u2717\uff0c\u4e14\u8fd9\u662f\u4e00\u4ef6**\u5f15\u64ce\u80fd\u529b** \u2717 \u800c\u975e\u6570\u636e\u884c \u2713\uff09 "
    "| `1415`\uff081 \u4f4d\uff09 "
    "| \u8ba9\u5fc6\u7075\u7684\u6280\u80fd\u6210\u4e3a `Skill` \u5bf9\u8c61\uff08\u2605 \u4ee5 `11415` \u4e3a cid \u2713\u3001\u6280\u80fd\u53f7\u4e3a\u69fd\u4f4d \u2713\uff09\uff1b\u2757**\u8bfb\u8005\u4e0d\u8db3 2 \u4f4d\u524d\u4e0d\u9020** \u2713 |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   1415's row now names the real prerequisite")
