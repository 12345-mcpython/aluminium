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
PREFIX = "| \u4ed3\u5e93\u6280\u300c\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| \u4ed3\u5e93\u6280\u300c**\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548\uff0c\u65e0\u9700\u4e0a\u573a**\u300d\uff08\u5b9e\u6d4b\uff1a\u5168\u90e8\u89d2\u8272\u91cc**\u53ea\u6709 2 \u4f4d**\u6709 \u2713\uff09 "
    "| \u4e09\u4ef6\uff0c\u5404\u81ea\u90fd\u6838\u5230\u4e86\u5177\u4f53\u80fd\u529b \u2713\uff1a"
    "\u2460 **\u4e00\u4e2a\u201c\u62e5\u6709\u4f46\u672a\u4e0a\u573a\u201d\u7684\u88c5\u8f7d\u70b9** \u2717\uff08\u8868\u662f\u5728\u89d2\u8272**\u88ab\u521b\u5efa**\u65f6\u8bfb\u7684 \u2713 `CharacterFactory:240`\uff09\uff1b"
    "\u2461 **1407\u300c\u6708\u8307\u4e4b\u5e87\u300d**\u7684\u300c\u6682\u65f6**\u5ef6\u540e**\u9677\u5165\u65e0\u6cd5\u6218\u6597\u72b6\u6001\u2026\u5426\u5219\u5c06**\u7acb\u5373**\u9677\u5165\u300d\u2717"
    "\uff08\u4e00\u6b21**\u5ef6\u8fdf\u7684\u5012\u4e0b** \u2717\uff1b\u2757 \u89e6\u53d1\u4e8b\u4ef6 `LETHAL_DAMAGE` **\u5df2\u6709** \u2713\u3001"
    "\u300c\u6bcf\u4e2a\u6ce2\u6b21\u6700\u591a 1 \u6b21\u300d\u7684 `WAVE_START` **\u5df2\u6709** \u2713\uff09\uff1b"
    "\u2462 **1506\u300c999 \u5b89\u5168\u536b\u58eb\u300d**\u7684\u300c\u654c\u65b9\u5bf9\u6211\u65b9\u65bd\u52a0\u4e86**\u63a7\u5236\u7c7b**\u8d1f\u9762\u72b6\u6001\u300d\u2717"
    "\uff08\u4e8b\u4ef6 `DEBUFF_APPLIED` **\u5df2\u6709** \u2713 \u21d2 \u7f3a\u7684\u662f**\u6309\u201c\u63a7\u5236\u7c7b\u201d\u7b5b\u9009** \u2717\uff09 "
    "| `1407`\uff08**\u6708\u8307\u4e4b\u5e87** \u2713\uff09\u3001`1506`\uff08**999 \u5b89\u5168\u536b\u58eb** \u2713\uff09 **\u5171 2 \u4f4d** \u2713 "
    "| \u2460 \u4e00\u4e2a\u88c5\u8f7d\u70b9 \u2713\uff1b\u2461 \u4e00\u6b21\u5ef6\u8fdf\u7684\u5012\u4e0b \u2713\uff1b\u2462 `DEBUFF_APPLIED` \u4e0a\u7684**\u63a7\u5236\u7c7b**\u7b5b\u9009 \u2713 "
    "|")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the warehouse row now names two readers, both effects and three pieces")
