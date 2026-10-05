"""1407's gap is CONFIRMED, and now has a mechanism (round 28 of the goal).

For once the register was right, and this round supplies the reason -- which is worth more than the verdict:

  * `LETHAL_DAMAGE` is announced PER INSTANCE: `fireTriggersForAlly(TriggerEvent.LETHAL_DAMAGE, target, target, 0)` (Battle:1425), i.e.
    one victim at a time, with actor == target == that victim.
  * the clause is 「该效果**单场战斗中最多触发 1 次**」, so it may fire ONCE for the whole battle.
  * multiplied together: only the FIRST victim of a lethal action would ever get 【月茧】, and its siblings would die. So the needed
    vocabulary is "every ally that received a lethal blow in this ACTION", not a different cap -- which is exactly the selector the row
    already names.

Also measured, and recorded because it bounds what exists: the engine's widest "the units that were hit" carrier is per-ATTACK. The
`random_hit_enemy` selector reads either the ATTACK_FINISHED frozen set or the damage instance's own snapshot (TriggerInterpreter:1507-1512),
and nothing in main ever passes a non-empty `attackHitTargets` -- that carrier's readers are in tests.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| 仓库技「**获得该角色即生效，无需上场**」"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows" % len(hits))
lines[hits[0]] = (
    "| 仓库技「**获得该角色即生效，无需上场**」 "
    "| ✅ `1506` 全部出货 ✓。❗ `1407` 月茇之庇：⭐ **缺口本轮被确认** ✓（而且机制已找到 ✓）—— "
    "⭐ `LETHAL_DAMAGE` **按实例**公告 ✓（`fireTriggersForAlly(LETHAL_DAMAGE, target, target, 0)` ✓，一次一个受害者 ✓），"
    "而该句是「该效果**单场战斗中最多触发 1 次**」✓ ⇒ 相乘之后 **只有第一个受害者拿到【月茇】** ✗，同行动的其余受害者会死 ✗。"
    "⚠ 已有的“命中集合”最宽到**一次攻击** ✓（`random_hit_enemy` 读 `ATTACK_FINISHED` 的冻结集 ✓ 或伤害实例快照 ✓；"
    "且主干代码**没人传**非空 `attackHitTargets` ✗）。"
    "⚠ 另外两半都写得出 ✓：「延后倒下」✓（`defers_death` ✓）与结算 ✓（`HEALED`／`SHIELD_GRANTED` ⇒ `REMOVE_STATE` ✓） "
    "| `1407`（月茇之庇 ✓） **1 位**（`1506` 已出货 ✓） "
    "| 一个“**一次行动**中受到致命攻击的全体”选择器 ✓（❗ 而非改限额 ✗） |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   1407's row now records WHY, not just that")
