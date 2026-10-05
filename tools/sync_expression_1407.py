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
PREFIX = "| 仓库技「**获得该角色即生效，无需上场**」"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| 仓库技「**获得该角色即生效，无需上场**」 "
    "| ✅ `1506` 全部出货 ✓。❗ `1407` 月茇之庇：⭐ **订正——“延迟倒下”并不缺** ✓"
    "（`DeferredDeathBuff` ✓ ＋ `defers_death` ✓ ＋ `Battle:1305` 在回合重查 ✓）；"
    "结算那一半也写得出 ✓（`HEALED`／`SHIELD_GRANTED` ＋ `self has_state 月茇` ⇒ `REMOVE_STATE` ✓）。"
    "❗ **真正缺的是**「本次**行动**中**所有**受到致命攻击的我方角色」✗"
    "（最近的是 `ctx.attackHitTargets()` ✓ ＝“这一**次攻击**命中的目标池” ✗；"
    "而一次**行动**可含多次攻击 ✗） "
    "| `1407`（月茇之庇 ✓） **1 位**（`1506` 已出货 ✓） "
    "| 一个“**一次行动**中受到致命攻击的全体”选择器 ✓ |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   1407's row now names the real gap")
