"""Drop the duplicated structural copy from GAPS.md -- with the anchors read correctly this time (2026-10-02).

The measured order is: table(1199) -> section 二(1605) -> table(1643) -> section 二(2049) -> section 三(2128) -> appendix(2141).

So the two copies are the SPANS [table1 .. table2) and [table2 .. section三), not two tables on their own. The first attempt paired the wrong anchors and its guard refused,
which is exactly why the guard is there. This version compares the two spans' TEXT and only drops one when they are equal (or when the shorter is a prefix of the longer, in
which case the longer is the one to keep).
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

T = "### \u2b50 \u7f3a\u53e3 \u00d7 \u7f3a\u5757"
S = "## \u4e8c\u3001\u4ecd\u7136\u53d7\u963b\u7684\u7f3a\u53e3"
THREE = "## \u4e09\u3001\u5df2\u6539\u6b63\u7684\u8fc7\u65f6\u767b\u8bb0"

t1 = find(T)
t2 = find(T, t1 + 1)
three = find(THREE, t2 + 1)
if min(t1, t2, three) < 0:
    sys.exit("REFUSING: could not find the three anchors")
spanA = lines[t1:t2]
spanB = lines[t2:three]
print("copy A = lines %d..%d (%d lines) ; copy B = lines %d..%d (%d lines)"
      % (t1 + 1, t2, len(spanA), t2 + 1, three, len(spanB)))

if spanA == spanB:
    drop = (t1, t2)
    print("the two copies are IDENTICAL -- dropping the earlier one")
elif spanA == spanB[:len(spanA)]:
    drop = (t1, t2)
    print("copy A is a PREFIX of copy B -- dropping the shorter (earlier) one")
elif spanB == spanA[:len(spanB)]:
    drop = (t2, three)
    print("copy B is a PREFIX of copy A -- dropping the shorter (later) one")
else:
    sys.exit("REFUSING: neither is a prefix of the other -- dropping either would lose something")

out = lines[:drop[0]] + lines[drop[1]:]
io.open(SRC, "w", encoding="utf-8", newline="\n").write("\n".join(out).rstrip() + "\n")
print("dropped lines %d..%d" % (drop[0] + 1, drop[1]))
