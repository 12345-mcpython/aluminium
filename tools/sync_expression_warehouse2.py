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

PREFIX = "| \u4ed3\u5e93\u6280\u300c**\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548\uff0c\u65e0\u9700\u4e0a\u573a**\u300d"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| \u4ed3\u5e93\u6280\u300c**\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548\uff0c\u65e0\u9700\u4e0a\u573a**\u300d\uff08\u5b9e\u6d4b\uff1a\u5168\u90e8\u89d2\u8272\u91cc**\u53ea\u6709 2 \u4f4d**\u6709 \u2713\uff09 "
    "| \u2b50 **\u88c5\u8f7d\u70b9\u5df2\u51fa\u8d27** \u2713\uff08\u672c\u8f6e \u2713\uff09\uff1b\u2757 \u5269\u4e0b\u4e24\u4ef6\uff0c\u5404\u81ea\u90fd\u662f**\u4e00\u4e2a\u8bed\u4e49** \u2717\uff1a"
    "\u2460 **1407\u300c\u6708\u8307\u4e4b\u5e87\u300d\u9700\u4e00\u6b21\u5ef6\u8fdf\u7684\u5012\u4e0b** \u2717\uff08\u300c\u6682\u65f6\u5ef6\u540e\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u2026\u5426\u5219\u5c06\u7acb\u5373\u9677\u5165\u300d\uff09\uff1b"
    "\u2461 **1506\u300c999 \u5b89\u5168\u536b\u58eb\u300d\u9700\u4e00\u4e2a\u6bcf\u6ce2\u6b21\u9650\u989d** \u2717\uff08\u5b9e\u6d4b\uff1a`TriggerSpec` \u53ea\u6709 `cooldown`\uff0f`per_turn`\uff0f`per_subject`\uff0f"
    "`once_per_battle`\uff0f`once_per_attack` \u2717\uff0c**\u6ca1\u6709\u6bcf\u6ce2\u6b21\u7684\u5b57\u6bb5**\uff09 "
    "| `1407`\uff08**\u6708\u8307\u4e4b\u5e87** \u2713\uff09\u3001`1506`\uff08**999 \u5b89\u5168\u536b\u58eb** \u2713\uff0c\u5176\u63a7\u5236\u7c7b\u7b5b\u9009\u5df2\u51fa\u8d27 \u2713\uff09 **\u5171 2 \u4f4d** \u2713 "
    "| \u2460 \u4e00\u6b21\u5ef6\u8fdf\u7684\u5012\u4e0b \u2713\uff1b\u2461 `TriggerSpec` \u4e0a\u4e00\u4e2a**\u6bcf\u6ce2\u6b21**\u9650\u989d \u2713 "
    "|")

ANCHOR = "| **\u300c\u65bd\u52a0\u7684\u662f**\u63a7\u5236\u7c7b**\uff0f**\u6301\u7eed\u4f24\u5bb3\u7c7b**\u8d1f\u9762\u72b6\u6001\u300d**"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
ROW = ("| **\u300c\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548\uff0c\u65e0\u9700\u4e0a\u573a\u300d\u7684\n**\u88c5\u8f7d\u70b9** "
       "| **`battle.registerWarehouseListener(character)`**\uff08\u53ea\u88ab\u95ee\u3001**\u4e0d\u8fdb\u961f\u5217\u3001\u4e0d\u88ab\u9009\u4e2d** \u2713\uff1b"
       "\u4e3a\u4ec0\u4e48\u4e0d\u80fd\u585e\u8fdb `characters`\uff1a\u90a3\u4efd\u540d\u5355\u5c31\u662f\u961f\u5217\u7684\u6765\u6e90 \u2713\uff09 "
       "| `src/main/java/com/laosun/aluminium/Battle.java` "
       "| `WarehouseListenerTest` |")
lines.insert(target[0] + 1, ROW)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   §2 gains the load point; the warehouse row names its two remaining pieces")
