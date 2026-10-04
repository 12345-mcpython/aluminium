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
PREFIX = "| \u4ed3\u5e93\u6280\u300c**\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548\uff0c\u65e0\u9700\u4e0a\u573a**\u300d"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| \u4ed3\u5e93\u6280\u300c**\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548\uff0c\u65e0\u9700\u4e0a\u573a**\u300d "
    "| \u2705 **\u5168\u90e8\u51fa\u8d27** \u2713\uff1a\u88c5\u8f7d\u70b9 \u2713\uff08`registerWarehouseListener` \u4e0d\u52a8\u5bf9\u65b9\u81ea\u5df1\u7684\u8868 \u2713\uff09\uff0b"
    "\u4ece\u961f\u4f0d\u81ea\u52a8\u767b\u8bb0 \u2713\uff08`startBattle` \u626b\u63cf\u961f\u4f0d\u4e2d\u5e26 `warehouse/<cid>.json` \u7684\u6210\u5458 \u2713\uff09\uff0b"
    "\u5185\u5bb9 `warehouse/1506.json` \u2713\uff08\u63a7\u5236\u7c7b\u7b5b\u9009 \u2713 \uff0b `RESIST_DEBUFF percent:1` \u2713 \uff0b `once_per_wave` \u2713\uff09\u3002"
    "\u2757 \u4ec5\u5269 **1407\u300c\u6682\u65f6\u5ef6\u540e\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u300d** \u2717 "
    "| `1407`\uff08\u6708\u8307\u4e4b\u5e87 \u2713\uff09 **1 \u4f4d**\uff08`1506` \u5df2\u51fa\u8d27 \u2713\uff09 "
    "| \u4e00\u6b21**\u5ef6\u8fdf\u7684\u5012\u4e0b** \u2713 |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the warehouse row is fully shipped; only 1407's delayed down is left")
