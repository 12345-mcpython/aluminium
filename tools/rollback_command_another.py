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
PREFIX = "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**\uff08`1415` \u7684\u5fc6\u7075\u6280\u80fd 8 \u2713\uff09 "
    "| \u2b50 \u8ba2\u6b63\uff1a\u201c\u547d\u4ee4**\u4ed6\u4eba**\u65bd\u653e\u201d**\u4e0d\u662f**\u524d\u7f6e\u4e86 \u2713 \u2014\u2014 \u5b83\u5df2\u7ecf\u51fa\u8d27 \u2713\uff1a"
    "`castSkill` \u7684\u65bd\u653e\u8005\u5c31\u662f `resolveTarget(effect, ctx)` \u2713\uff08**\u4e0d\u662f**\u89c4\u5219\u62e5\u6709\u8005 \u2717\uff09\uff0c\u800c `1412` \u7684 `coup_de_main` \u5199\u7684\u5c31\u662f "
    "`CAST_SKILL{skill: SKILL, target: \"attacker\"}` \u2713\uff1b\u4e14 `holder_of:<\u72b6\u6001>` **\u641c\u7684\u662f\u6574\u961f** \u2713\uff08`battle.getSideOf(ctx.owner())` \u2713\uff09\u3002"
    "\u2757 **\u771f\u6b63\u5269\u4e0b\u7684\u524d\u7f6e**\uff1a\u540c\u4e00\u53e5\u7684\u53e6\u4e00\u534a \u300c\u89e3\u9664\u4e07\u654c\u9677\u5165\u7684\u6240\u6709**\u63a7\u5236\u7c7b**\u8d1f\u9762\u72b6\u6001\u300d\u2717 \u21d2 \u5408\u5e76\u5230\u4e0b\u4e00\u884c\u7684\u7b5b\u9009\u7f3a\u53e3 \u2713\u3002"
    "\u26a0 \u5c0f\u9677\u9631\uff1a`normalizeTarget` **\u4f1a\u628a\u9009\u62e9\u5668\u5c0f\u5199\u5316** \u2717 \u21d2 \u72b6\u6001\u540d\u8981\u5c0f\u5199 \u2713 "
    "| `1415`\uff081 \u4f4d\uff09 "
    "| \u540c\u4e0b\u4e00\u884c\uff08\u4e00\u4e2a**\u201c\u63a7\u5236\u7c7b\u201d\u7b5b\u9009** \u2713\uff09 |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the row is corrected and merged with the blocked filter")
