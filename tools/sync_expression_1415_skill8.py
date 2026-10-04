"""Record the two measured conclusions of this round (round 16 of the goal).

1. 1415's memosprite skill 8 cannot be cast at all -- NOT because of a missing slot, but because it DELIVERS nothing:
   `skill_effects.json` has 21 cids and no 1415, the skill is tagged 辅助 (non-damaging), and `CAST_SKILL`'s guard refuses exactly
   "a commanded cast that would do nothing at all". So a skill whose whole job is to trigger other rules has no place in the model,
   and its 「对万敌施放时」 clause therefore has no trigger point. That is a NEW, sharper prerequisite.

2. The warehouse load point's wiring is now measured: the dispatch core asks `for (Character ally : characters)`, and `characters`
   IS the roster that acts (assigned from the queue at L520), so a listen-only unit cannot simply be put there. The clean shape is a
   SECOND loop over a listen-only table list inside the same dispatch, fed by its own loader.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")
PREFIX = "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| **\u300c\u4f7f\u4e07\u654c\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**\uff08`1415` \u7684\u5fc6\u7075\u6280\u80fd 8 \u2713\uff09 "
    "| \u2b50\u2b50 **\u672c\u8f6e\u6d4b\u5b9a\uff1a\u8be5\u6280\u80fd\u6839\u672c\u65e0\u6cd5\u88ab\u65bd\u653e** \u2717 \u2014\u2014 \u2757 \u4e0d\u662f\u7f3a\u69fd\u4f4d \u2717\uff0c\u800c\u662f\u5b83**\u4ec0\u4e48\u90fd\u4e0d\u4ea4\u4ed8** \u2717\uff1a"
    "\u6280\u80fd 8 \u662f\u300c**\u8f85\u52a9**\u300d\uff08\u975e\u4f24\u5bb3 \u2713\uff09\uff0c\u800c `skill_effects.json`\uff0821 \u4e2a cid \u2713\uff09**\u91cc\u6ca1\u6709 1415** \u2717\uff0c"
    "\u800c `CAST_SKILL` \u7684\u62a4\u680f**\u62d2\u7edd\u7684\u6070\u6070\u5c31\u662f**\u201c\u4ec0\u4e48\u90fd\u4e0d\u505a\u7684\u88ab\u547d\u4ee4\u65bd\u653e\u201d \u2713 "
    "\u21d2 \u2b50 **\u4e00\u4e2a\u53ea\u89e6\u53d1\u89c4\u5219\u3001\u81ea\u8eab\u65e0\u4ea4\u4ed8\u7684\u6280\u80fd\u5728\u5f53\u524d\u6a21\u578b\u91cc\u6ca1\u6709\u4f4d\u7f6e** \u2717\uff0c"
    "\u56e0\u6b64\u5b83\u7684\u300c\u5bf9\u4e07\u654c\u65bd\u653e\u65f6\u300d\u53e5\u5b50**\u6ca1\u6709\u89e6\u53d1\u70b9** \u2717 "
    "| `1415`\uff081 \u4f4d\uff09 "
    "| \u4e00\u4e2a**\u53ea\u89e6\u53d1\u89c4\u5219**\u7684\u6280\u80fd\u80fd\u88ab\u627f\u8ba4\uff08\u6216\u7ed9\u5b83\u4e00\u6761 `skill_effects.json` \u6761\u76ee \u2713\uff09 |")
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the 1415 row now names what really blocks it")
