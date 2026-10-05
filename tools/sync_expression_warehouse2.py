"""Checklist sync for the warehouse load point (round 17 of the goal).

Ships the LOAD POINT half: `battle.registerWarehouseListener(character)` -- a unit owned but not deployed, whose rules are asked and
which never acts. Judged on three claims at once (its rule runs, the mark lands on ITS panel, it is never in the queue).

What the two warehouse sentences still wait on, now measured:
  * 1407's 月茧之庇 needs a DELAYED DOWN (「暂时延后陷入无法战斗状态…否则将立即陷入」);
  * 1506's 999安全卫士 needs a PER-WAVE limit (「每个波次最多触发 1 次」) -- measured: `TriggerSpec` has cooldown / per_turn / per_subject /
    once_per_battle / once_per_attack and NO per-wave field;
  * both need their own clauses written, and the control-class filter they lean on now ships.
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
    "| 仓库技「**获得该角色即生效，无需上场**」（实测：全部角色里**只有 2 位**有 ✓） "
    "| ⭐ **装载点已出货** ✓（本轮 ✓）；❗ 剩下两件，各自都是**一个语义** ✗："
    "① **1407「月茇之庇」需一次延迟的倒下** ✗（「暂时延后陷入无法战斗状态…否则将立即陷入」）；"
    "② **1506「999 安全卫士」需一个每波次限额** ✗（实测：`TriggerSpec` 只有 `cooldown`／`per_turn`／`per_subject`／"
    "`once_per_battle`／`once_per_attack` ✗，**没有每波次的字段**） "
    "| `1407`（**月茇之庇** ✓）、`1506`（**999 安全卫士** ✓，其控制类筛选已出货 ✓） **共 2 位** ✓ "
    "| ① 一次延迟的倒下 ✓；② `TriggerSpec` 上一个**每波次**限额 ✓ "
    "|")

ANCHOR = "| **「施加的是**控制类**／**持续伤害类**负面状态」**"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
ROW = ("| **「获得该角色即生效，无需上场」的\n**装载点** "
       "| **`battle.registerWarehouseListener(character)`**（只被问、**不进队列、不被选中** ✓；"
       "为什么不能塞进 `characters`：那份名单就是队列的来源 ✓） "
       "| `src/main/java/com/laosun/aluminium/Battle.java` "
       "| `WarehouseListenerTest` |")
lines.insert(target[0] + 1, ROW)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   §2 gains the load point; the warehouse row names its two remaining pieces")
