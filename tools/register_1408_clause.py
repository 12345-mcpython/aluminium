"""Add the 1408 clause to §3's register (round 1691).

① now stands at four of five readers: 1408's three 「变身结束时」 sentences are named and their machinery exists, but the third of
them cannot be written yet. Registered with what is missing, the reader, and the prerequisite -- the shape §3 requires.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
ROW = ("| \u300c\u53d8\u8eab\u7ed3\u675f\u65f6\u2026\u300d\uff08\u767d\u5384\u90a3\u4e09\u53e5\uff1a\u5168\u961f\u901f\u5ea6 +15% \u2713\u3001\u83b7\u5f97 3 \u70b9\u3010\u706b\u79cd\u3011\u2713\u3001"
       "\u300c\u8fdb\u5165\u6218\u6597**\u6216**\u53d8\u8eab\u7ed3\u675f\u65f6\u653b\u51fb\u529b +50%\u300d\uff09 "
       "| \u7b2c\u4e09\u53e5\u9700\u8981\uff1a\u4e24\u6761**\u4e0d\u540c\u89c4\u5219**\u6302\u5728**\u540c\u4e00\u5c5e\u6027**\u4e0a\u65f6**\u5171\u5b58** \u2717"
       "\uff08\u5b9e\u6d4b\uff1a`StatModifierBuff.isSameKind` \u662f `(attribute, modifierType, sourceRole)` \u21d2 \u540c\u7c7b \u21d2 \u540e\u6302\u7684**\u9876\u6389**\u5148\u6302\u7684 \u2717\uff1b"
       "\u800c\u8bed\u6599\u8bf4\u4e24\u8005**\u540c\u65f6\u751f\u6548** \u2713\uff09 "
       "| `1408`\uff081 \u4f4d\uff09 | \u4e00\u4e2a**\u6536\u7a84\u7684\u3001\u53ef\u9009\u5165**\u7684\u8bed\u4e49 "
       "\uff08\u26a0 \u201c\u4e0d\u540c\u89c4\u5219\u4e00\u5f8b\u5171\u5b58\u201d\u5df2\u5b9e\u6d4b**\u7834\u574f 7 \u4f8b\u5df2\u51fa\u8d27\u8bfb\u6570** \u2717 \u21d2 \u6324\u51fa\u662f**\u8f7d\u8377** \u2713\uff09 |")
ANCHOR = "| \u4ed3\u5e93\u6280\u300c\u83b7\u5f97\u8be5\u89d2\u8272\u5373\u751f\u6548"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §3 anchor rows" % len(target))
lines.insert(target[0], ROW)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the 1408 clause is registered in §3")
