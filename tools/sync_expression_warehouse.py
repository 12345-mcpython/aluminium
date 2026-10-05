"""Rewrite the warehouse-skill row from what the data and the engine actually say (round 8 of the goal).

Measured:
  * the index's own 仓库技 column is non-empty for exactly TWO characters -- 1407 (月茧之庇) and 1506 (999安全卫士) -- so the row's
    "1 位" was an undercount;
  * both pages name the mechanism (`AvatarGlobalBuffConfig`, a global support skill) and give the exact effects and ids:
    1407 skillID 140704 / mapBuff 140703, 1506 skillID 150604 / mapBuff 150602;
  * the engine's event list already has `LETHAL_DAMAGE`, `DEBUFF_APPLIED` and `WAVE_START`, so the missing pieces are narrower than
    "a whole mechanism", and they can be named one by one;
  * tables are loaded when a character is CREATED (`CharacterFactory:240`), which is the load point that does not exist for a
    character who is owned but not deployed.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| 仓库技「获得该角色即生效"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| 仓库技「**获得该角色即生效，无需上场**」（实测：全部角色里**只有 2 位**有 ✓） "
    "| 三件，各自都核到了具体能力 ✓："
    "① **一个“拥有但未上场”的装载点** ✗（表是在角色**被创建**时读的 ✓ `CharacterFactory:240`）；"
    "② **1407「月茇之庇」**的「暂时**延后**陷入无法战斗状态…否则将**立即**陷入」✗"
    "（一次**延迟的倒下** ✗；❗ 触发事件 `LETHAL_DAMAGE` **已有** ✓、"
    "「每个波次最多 1 次」的 `WAVE_START` **已有** ✓）；"
    "③ **1506「999 安全卫士」**的「敌方对我方施加了**控制类**负面状态」✗"
    "（事件 `DEBUFF_APPLIED` **已有** ✓ ⇒ 缺的是**按“控制类”筛选** ✗） "
    "| `1407`（**月茇之庇** ✓）、`1506`（**999 安全卫士** ✓） **共 2 位** ✓ "
    "| ① 一个装载点 ✓；② 一次延迟的倒下 ✓；③ `DEBUFF_APPLIED` 上的**控制类**筛选 ✓ "
    "|")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the warehouse row now names two readers, both effects and three pieces")
