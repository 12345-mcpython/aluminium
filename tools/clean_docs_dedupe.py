"""Drop the duplicated structural copy from GAPS.md (2026-10-02).

After the log split, GAPS.md still carried the same two sections TWICE -- a consolidation script evidently ran twice:

    line  1199: ### 缺口 x 缺块 x 读者数（对账表）
    line  1605: ## 二、仍然受阻的缺口            (38 lines)
    line  1643: ### 缺口 x 缺块 x 读者数（对账表）  (the same table again)
    line  2049: ## 二、仍然受阻的缺口            (79 lines -- the fuller one)
    line  2128: ## 三、已改正的过时登记
    line  2141: ## 附：本段收束

The LATER `## 二` is the one that sits in sequence before `## 三`, so the EARLIER pair is the copy. This script reports whether the two tables are identical (so the
deletion is provably lossless) and then removes the earlier pair.
"""
import io
import sys

SRC = "GAPS.md"
lines = io.open(SRC, encoding="utf-8").read().split("\n")

def find(prefix, start=0):
    for i in range(start, len(lines)):
        if lines[i].startswith(prefix):
            return i
    return -1

# 0-based indices; anchors are the heading lines themselves
t1 = find("### \u2b50 \u7f3a\u53e3 \u00d7 \u7f3a\u5757")
t2 = find("### \u2b50 \u7f3a\u53e3 \u00d7 \u7f3a\u5757", t1 + 1)
s1 = find("## \u4e8c\u3001\u4ecd\u7136\u53d7\u963b\u7684\u7f3a\u53e3", t1 + 1)
s2 = find("## \u4e8c\u3001\u4ecd\u7136\u53d7\u963b\u7684\u7f3a\u53e3", s1 + 1)
three = find("## \u4e09\u3001\u5df2\u6539\u6b63\u7684\u8fc7\u65f6\u767b\u8bb0", s2 + 1)
print("tables at lines %d and %d ; section 二 at %d and %d ; section 三 at %d"
      % (t1 + 1, t2 + 1, s1 + 1, s2 + 1, three + 1))
if min(t1, t2, s1, s2, three) < 0:
    sys.exit("REFUSING: could not find the duplicate pair")

tableA = lines[t1:t2]
tableB = lines[t2:s1]
print("the two tables are %s (%d vs %d lines)"
      % ("IDENTICAL" if tableA == tableB else "DIFFERENT", len(tableA), len(tableB)))
if tableA != tableB:
    sys.exit("REFUSING: the two tables differ -- dropping either would lose something")

# remove the EARLIER pair: from the first table heading up to (not including) the second table heading
out = lines[:t1] + lines[t2:]
io.open(SRC, "w", encoding="utf-8", newline="\n").write("\n".join(out).rstrip() + "\n")
print("dropped lines %d..%d (the earlier copy)" % (t1 + 1, t2))
