"""Correct 1407's row: the delayed down is NOT missing -- the ACTION-WIDE selection is (round 25 of the goal).

Measured this round, by reading rather than guessing:
  * `DeferredDeathBuff` exists, `EffectSpec.defersDeath` carries it, `APPLY_BUFF` builds it, and `Battle:1305` re-checks at the owner's
    turn -- so 「暂时延后陷入无法战斗状态，且可以正常行动」 is already implementable. The register's "needs a delayed down" was WRONG.
  * the resolution half is expressible too: `HEALED` and `SHIELD_GRANTED` both exist, so 「若…生命值提高或获得护盾，则解除【月茧】状态」 is a
    two-rule remove, and `Battle:1429` commits the death when the state is gone by the owner's turn.
  * what is NOT expressible is 「本次行动中**所有**受到致命攻击的我方角色」: the nearest thing is `ctx.attackHitTargets()` -- the targets ONE
    ATTACK hit -- and an ACTION may contain several attacks, so using it would be an approximation.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| \u4ed3\u5e93\u6280\u300c**\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548\uff0c\u65e0\u9700\u4e0a\u573a**\u300d"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| \u4ed3\u5e93\u6280\u300c**\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548\uff0c\u65e0\u9700\u4e0a\u573a**\u300d "
    "| \u2705 `1506` \u5168\u90e8\u51fa\u8d27 \u2713\u3002\u2757 `1407` \u6708\u8307\u4e4b\u5e87\uff1a\u2b50 **\u8ba2\u6b63\u2014\u2014\u201c\u5ef6\u8fdf\u5012\u4e0b\u201d\u5e76\u4e0d\u7f3a** \u2713"
    "\uff08`DeferredDeathBuff` \u2713 \uff0b `defers_death` \u2713 \uff0b `Battle:1305` \u5728\u56de\u5408\u91cd\u67e5 \u2713\uff09\uff1b"
    "\u7ed3\u7b97\u90a3\u4e00\u534a\u4e5f\u5199\u5f97\u51fa \u2713\uff08`HEALED`\uff0f`SHIELD_GRANTED` \uff0b `self has_state \u6708\u8307` \u21d2 `REMOVE_STATE` \u2713\uff09\u3002"
    "\u2757 **\u771f\u6b63\u7f3a\u7684\u662f**\u300c\u672c\u6b21**\u884c\u52a8**\u4e2d**\u6240\u6709**\u53d7\u5230\u81f4\u547d\u653b\u51fb\u7684\u6211\u65b9\u89d2\u8272\u300d\u2717"
    "\uff08\u6700\u8fd1\u7684\u662f `ctx.attackHitTargets()` \u2713 \uff1d\u201c\u8fd9\u4e00**\u6b21\u653b\u51fb**\u547d\u4e2d\u7684\u76ee\u6807\u6c60\u201d \u2717\uff1b"
    "\u800c\u4e00\u6b21**\u884c\u52a8**\u53ef\u542b\u591a\u6b21\u653b\u51fb \u2717\uff09 "
    "| `1407`\uff08\u6708\u8307\u4e4b\u5e87 \u2713\uff09 **1 \u4f4d**\uff08`1506` \u5df2\u51fa\u8d27 \u2713\uff09 "
    "| \u4e00\u4e2a\u201c**\u4e00\u6b21\u884c\u52a8**\u4e2d\u53d7\u5230\u81f4\u547d\u653b\u51fb\u7684\u5168\u4f53\u201d\u9009\u62e9\u5668 \u2713 |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   1407's row now names the real gap")
