"""Correct the checklist row for the live-modifier family, from the GAPS sweep (round 1661).

Measured: the row said one reader (`23000`). The sweep of GAPS §2 found the real entry (2026-09-29, entry 39): 「每层 +X%」
with FOURTEEN documents, all sustained auras like Asta's 「每拥有 1 层蓄能，我方全体攻击力提高 14%，最多叠加 5 层」, whose number
has to be re-read as the stack count changes -- and the engine's `DoubleValue.Modifier` carries a rate, not a function. The
row is rewritten with the measured reader count, the exact shape it needs, and the fact that this round only READ the
registration rather than re-checking the tree.
"""
import io

PATH = "EXPRESSION.md"
START = "| \u300c\u6309\u6570\u91cf\uff0f\u8ba1\u6570**\u7f29\u653e\u5c5e\u6027**"
NEW = ("| \u300c**\u6bcf\u5c42 +X%**\u300d\u7684**\u6d3b\u503c\u4fee\u6b63\u5668**\uff08\u957f\u9a7b\u5149\u73af\uff1a\u6570\u503c\u968f\u5c42\u6570**\u88ab\u91cd\u65b0\u8bfb\u53d6**\uff09 "
       "| \u4e00\u4e2a\u5728**\u8bfb\u53d6\u65f6\u89e3\u6790\u5176\u4efd\u989d**\u7684\u4fee\u6b63\u5668\uff08`DoubleValue` \u7684\u4e00\u7c7b**\u6d3b**\u4fee\u6b63\uff09\uff1a"
       "\u5feb\u7167\u53ea\u80fd\u5728**\u5e94\u7528\u65f6\u523b**\u7b97\u4e00\u6b21 \u2717\uff0c\u800c\u91cd\u7b97\u53c8\u4f1a\u8ba9 buff \u81ea\u5df1\u53e0\u52a0\uff080.14 \u00d7 (1+2+3+4+5)\uff09\u2717\uff1b"
       "\u26a0 \u6811\u91cc\u53ea\u89c1\u5230\u6301\u6709**\u6570\u503c**\u7684\u4fee\u6b63\u5668\uff08`multiplyPercent(rate, \u2026)` \u2713\uff09 "
       "| **14 \u4e2a\u6587\u4ef6**\uff08GAPS \u7b2c 39 \u6761\uff0c2026-09-29 \u91cf\u6d4b \u2713\uff1b\u26a0 \u672c\u8f6e**\u53ea\u8bfb\u5230\u767b\u8bb0**\uff0c\u672a\u9010\u6761\u590d\u6838 \u2717\uff09 "
       "| \u4e00\u7c7b\u6d3b\u4fee\u6b63\u5668\uff08\u8bfb\u53d6\u65f6\u53d6\u5f53\u524d\u5c42\u6570\uff09\n")

lines = io.open(PATH, encoding="utf-8").read().split("\n")
hits = [i for i, line in enumerate(lines) if line.startswith(START)]
if len(hits) != 1:
    raise SystemExit("REFUSING: %d rows start with that prefix" % len(hits))
lines[hits[0]] = NEW.rstrip("\n")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the row now states 14 readers and the exact shape needed")
