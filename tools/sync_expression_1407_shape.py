"""Item 4's registration was too pessimistic; this corrects it to what was measured (2026-10-02).

What section 3 said: 「1407 月茇之庇」 needs a five-part capability (an action boundary, the in-action victims, an action-end
announcement, a selector, a limit).

What the tree says:
  * `TURN_START` is fired at Battle.java:1031 and `TURN_END` at Battle.java:1301, while `performAction` is at :1245 and `afterMove` at
    :1276 -- so the two events ALREADY bracket the action, and the "action boundary" piece exists.
  * the limit machinery exists too: 1217's own clause uses the `救主计数` counter.
  * what is genuinely missing is ONE concept -- the set of units that took a lethal blow during the current action -- plus the selector
    that reads it. `TURN_END` cannot stand in for it either: the save has to happen at the moment of the blow, not at the end.
So the row's "前置" becomes two named pieces instead of five.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| \u4ed3\u5e93\u6280\u300c**\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548\uff0c\u65e0\u9700\u4e0a\u573a**\u300d"
hits = [i for i, l in enumerate(lines) if l.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows for the warehouse family" % len(hits))
lines[hits[0]] = (
    "| \u4ed3\u5e93\u6280\u300c**\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548\uff0c\u65e0\u9700\u4e0a\u573a**\u300d "
    "| \u2705 `1506` \u5168\u90e8\u51fa\u8d27 \u2713\u3002\u2757 `1407` \u6708\u8309\u4e4b\u5e87\uff1a\u53ea\u5dee\u300c**\u4e00\u6b21\u884c\u52a8**\u4e2d\u53d7\u5230\u81f4\u547d\u653b\u51fb\u7684**\u5168\u4f53**\u300d\u9009\u62e9\u5668 \u2713\u3002"
    "\u2b50 **\u672c\u8f6e\u628a\u201c\u4e94\u4f4d\u4e00\u4f53\u201d\u6539\u6210\u4e86\u4e24\u4ef6** \u2713\uff08\u91cf\u8fc7 \u2713\uff09\uff1a"
    "\u2460 \u884c\u52a8\u8fb9\u754c **\u5df2\u6709** \u2713 \u2014\u2014 `TURN_START`\uff08`Battle:1031`\uff09\u4e0e `TURN_END`\uff08`Battle:1301`\uff09"
    "\u6b63\u597d\u5939\u4f4f\u4e86 `performAction`\uff081245\uff09\u4e0e `afterMove`\uff081276\uff09 \u2713\uff1b"
    "\u2461 \u9650\u989d\u914d\u5408 **\u4e5f\u5df2\u6709** \u2713\uff08`1217` \u81ea\u5df1\u90a3\u6761\u5c31\u7528\u7684 `\u6551\u4e3b\u8ba1\u6570` \u8ba1\u6570\u5668 \u2713\uff09\u3002"
    "\u2757 **\u771f\u6b63\u7f3a\u7684\u53ea\u6709\u4e00\u4e2a\u6982\u5ff5** \u2713\uff1a\u300c**\u672c\u6b21\u884c\u52a8\u5185\u53d7\u5230\u81f4\u547d\u653b\u51fb\u7684\u5355\u4f4d\u96c6\u5408**\u300d \u2717\uff0c"
    "\u52a0\u4e0a\u8bfb\u5b83\u7684\u9009\u62e9\u5668 \u2717\u3002\u26a0 `TURN_END` **\u4e0d\u80fd\u4ee3\u66ff** \u2717\uff1a\u90a3\u53e5\u8bdd\u8981\u5728**\u81f4\u547d\u7684\u90a3\u4e00\u523b**\u5c31\u6551 \u2713\uff0c"
    "\u800c\u4e0d\u662f\u5230\u884c\u52a8\u672b\u5c3e \u2717\u3002 "
    "| `1407`\uff081 \u4f4d\uff09 "
    "| \u2460 \u4e00\u4e2a\u201c\u672c\u6b21\u884c\u52a8\u5185\u53d7\u8fc7\u81f4\u547d\u653b\u51fb\u7684\u5168\u4f53\u201d\u9009\u62e9\u5668\uff08\u542b\u90a3\u4e2a\u96c6\u5408\u672c\u8eab\uff09 "
    "|")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   item 4's row is corrected")
