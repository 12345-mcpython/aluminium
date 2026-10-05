"""Checklist sync: the warehouse row is now fully shipped (round 24 of the goal).

Two things changed since that row was written:
  * `Battle.startBattle` scans the party and listens to any member with a `warehouse/<cid>.json` -- 「获得该角色后，或该角色在队伍中时」 needs
    no manual registration, and the listener keeps her OWN battle table (the map holds owner -> table, so nothing is swapped).
  * the 1506 clause itself is in the tree and judged (`WarehouseAutoListenerTest`: firewall granted, next control refused, owner still
    in the queue).

What the row still waits on is only 1407's 「暂时延后陷入无法战斗状态」.
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
    "| ✅ **全部出货** ✓：装载点 ✓（`registerWarehouseListener` 不动对方自己的表 ✓）＋"
    "从队伍自动登记 ✓（`startBattle` 扫描队伍中带 `warehouse/<cid>.json` 的成员 ✓）＋"
    "内容 `warehouse/1506.json` ✓（控制类筛选 ✓ ＋ `RESIST_DEBUFF percent:1` ✓ ＋ `once_per_wave` ✓）。"
    "❗ 仅剩 **1407「暂时延后陷入无法战斗状态」** ✗ "
    "| `1407`（月茇之庇 ✓） **1 位**（`1506` 已出货 ✓） "
    "| 一次**延迟的倒下** ✓ |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the warehouse row is fully shipped; only 1407's delayed down is left")
