"""Bring EXPRESSION.md in step with the tree (round 1660).

Two edits:
  * §2 gains the capability that shipped this segment -- a STACKABLE state (a state that carries a count), with the class
    and the judge that prove it;
  * §3 gains 1505's 「开不败」 as the registered sentence it is, with what is missing, who the reader is, and the
    prerequisites -- including the fact measured last round (STATE_ENDED does reach content tables; the hand-built-table
    route could not be used to judge it).
"""
import io

PATH = "EXPRESSION.md"
text = io.open(PATH, encoding="utf-8").read()

# 1. a §2 row, right after the "counter as a condition" row
ANCHOR = "| \u6b22\u4e50\u6280\u300c**8 \u6b21**\u968f\u673a\u5355\u4f53\u2026\u300d\u7684**\u6309\u6b21\u6570\u7ed3\u7b97**"
NEW_ROW = ("| **\u5e26\u8ba1\u6570\u7684\u72b6\u6001**\uff08\u201c\u5c06 N \u8ba1\u5165\u8be5\u72b6\u6001\u201d\uff09 | `APPLY_BUFF` \u4e0a\u7684 `\"stackable\": true` \uff0b `max_stacks`"
           "\uff08\u540c\u540d\u5b9e\u4f8b\u7d2f\u52a0\uff0c\u4e14\u4ecd\u4f1a\u516c\u544a\u81ea\u5df1\u7684\u7ed3\u675f\uff09 "
           "| `src/main/java/com/laosun/aluminium/models/buff/StackableStateBuff.java` | `AttachedBuffProbeTest` |\n")
if ANCHOR not in text:
    raise SystemExit("REFUSING: the §2 anchor is absent")
text = text.replace(ANCHOR, NEW_ROW + ANCHOR, 1)

# 2. a §3 row for 1505's clause
ANCHOR3 = "| \u300c\u6309\u6570\u91cf\uff0f\u8ba1\u6570**\u7f29\u653e\u5c5e\u6027**"
NEW_ROW3 = ("| \u300c\u961f\u53cb\u6301\u6709\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u3011**\u7ed3\u675f\u65f6**\uff0c\u7eef\u82f1\u4f1a\u5c06\u5176\u4e2d\u7684 **50%** \u8f6c\u5316\u4e3a\u81ea\u8eab\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u300d "
           "| \u4e09\u4ef6\uff1a\u2460 `STATE_ENDED` **\u8981\u5e26\u4e0a\u7ed3\u675f\u65f6\u7684\u91cf**\uff08\u5df2\u5199\u597d\u3001\u5df2\u56de\u6eda\uff1a\u53ea\u6709\u5185\u5bb9\u8868\u80fd\u5f53\u5224\u636e\uff09\uff1b"
           "\u2461 \u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u5728\u6811\u91cc**\u540c\u540d\u4e24\u6a21\u578b\u5e76\u5b58**\uff08`1513` \u5f53\u72b6\u6001\u3001`1505`\uff0f`8009`\uff0f`8010` \u5f53\u8d44\u6e90\uff09\uff1b"
           "\u2462 \u201c\u5c06\u7b11\u70b9\u8ba1\u5165\u8be5\u72b6\u6001\u201d\u7684**\u6570\u91cf\u8fd8\u6ca1\u6709\u6765\u6e90** "
           "| `1505`\uff081 \u4f4d\uff09 | \u4e00\u6761\u5185\u5bb9\u5224\u636e\uff08\u624b\u5de5\u8868\u5230\u4e0d\u4e86 `STATE_ENDED`\uff09\uff0b\u6a21\u578b\u7edf\u4e00 |\n")
if ANCHOR3 not in text:
    raise SystemExit("REFUSING: the §3 anchor is absent")
text = text.replace(ANCHOR3, NEW_ROW3 + ANCHOR3, 1)

io.open(PATH, "w", encoding="utf-8", newline="\n").write(text)
print("ok   EXPRESSION.md: one new §2 row and one new §3 row")
