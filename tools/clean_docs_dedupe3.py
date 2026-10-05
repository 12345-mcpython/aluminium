"""Drop the earlier structural copy, proved lossless by SET containment (2026-10-02).

The first two attempts used a prefix test and both refused, which was right: the copies differ in grouping, not in content. The measured fact is `onlyA = 0` -- every
non-blank line of the earlier copy appears in the later one -- so the later copy supersedes it and dropping the earlier one loses nothing.
"""
import io
import sys

SRC = "GAPS.md"
lines = io.open(SRC, encoding="utf-8").read().split("\n")

T = "### \u2b50 \u7f3a\u53e3 \u00d7 \u7f3a\u5757"
THREE = "## \u4e09\u3001\u5df2\u6539\u6b63\u7684\u8fc7\u65f6\u767b\u8bb0"

def find(prefix, start=0):
    for i in range(start, len(lines)):
        if lines[i].startswith(prefix):
            return i
    return -1

t1 = find(T)
t2 = find(T, t1 + 1)
three = find(THREE, t2 + 1)
if min(t1, t2, three) < 0:
    sys.exit("REFUSING: could not find the three anchors")

A, B = lines[t1:t2], lines[t2:three]
setB = set(B)
onlyA = [l for l in A if l.strip() and l not in setB]
print("copy A lines %d..%d (%d) ; copy B lines %d..%d (%d) ; onlyA = %d"
      % (t1 + 1, t2, len(A), t2 + 1, three, len(B), len(onlyA)))
if onlyA:
    sys.exit("REFUSING: copy A has %d lines the later copy lacks -- dropping it would lose them" % len(onlyA))

out = lines[:t1] + lines[t2:]
io.open(SRC, "w", encoding="utf-8", newline="\n").write("\n".join(out).rstrip() + "\n")
print("dropped the earlier copy: lines %d..%d" % (t1 + 1, t2))
