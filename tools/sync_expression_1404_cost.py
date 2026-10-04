"""Checklist sync for the cost half and the swap blocker (round 5 of the goal).

Shipped this round: 1404's turn-start rule gates on 【血仇】 and pays the skill's cost, through a new CURRENT-HP share. That is a
§2 row.
Still blocked, now precisely: `REPLACE_SKILL` installs the row into the map, but a `CAST_SKILL` later in the SAME rule runs the
OLD row -- measured in one scene: the content's cast deals 767.585, a direct cast of slot 9 deals 383.793. That is what keeps
「自动施放【弑王成王】/【弑神登神】」 from being expressible, and it has three readers.
"""
import io
import sys

PATH = "EXPRESSION.md"
lines = io.open(PATH, encoding="utf-8").read().split("\n")

# ---- 1. §3: the row records the real blocker
PREFIX = "| **\u300c\u5145\u80fd\u8fbe\u5230 150 \u70b9\u65f6"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d §3 rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| **\u300c\u81ea\u52a8\u65bd\u653e\u3010\u5f3a\u5316\u6218\u6280\u3011\u300d**\uff08\u4e07\u654c\u7684\u3010\u5f11\u738b\u6210\u738b\u3011\u69fd 9 \u2713 \u4e0e\u3010\u5f11\u795e\u767b\u795e\u3011\u69fd 11 \u2713\uff1b"
    "\u4ee5\u53ca\u5145\u80fd 150 \u90a3\u53e5\uff09 "
    "| \u2b50 \u73b0\u5728\u53ea\u5269**\u4e00\u4ef6** \u2713\uff1a**\u8ba9 `REPLACE_SKILL` \u5728\u540c\u4e00\u6761\u89c4\u5219\u91cc\u5bf9\u968f\u540e\u7684 `CAST_SKILL` \u751f\u6548** \u2717 "
    "\u2014\u2014 \u5b9e\u6d4b\uff1a\u6362\u88c5**\u786e\u5b9e\u88c5\u8fdb\u4e86 map** \u2713\uff08`getSkills().get(SKILL).getSkillSlot()` \u8bfb\u5230 **9** \u2713\u3001category `BPSKILL` \u2713\uff09\uff0c"
    "\u4f46\u540c\u4e00\u573a\u666f\u91cc\u5185\u5bb9\u90a3\u6761\u9020\u6210 **767.585** \u2713 \u800c**\u76f4\u63a5**\u653e\u69fd 9 \u53ea\u6709 **383.793** \u2713"
    "\uff08\u4ed6\u653b\u51fb\u529b 426.888 \u2713\uff09\u21d2 \u540c\u89c4\u5219\u5185\u7684\u653e\u4ecd\u7528**\u65e7\u884c** \u2717\uff1b"
    "\u26a0 \u53e6\u4e00\u4ef6\u4e5f\u5df2\u5b9e\u6d4b\uff1a\u300c\u5145\u80fd \u2265 100\u300d\u90a3\u6761**\u6ca1\u6709\u3010\u8840\u4ec7\u3011\u95e8** \u2717 \u21d2 \u5b83\u4f1a**\u53cd\u590d**\u89e6\u53d1\u5e76\u628a\u5145\u80fd\u62bd\u5e72 \u2717\uff0c\u5145\u80fd\u6512\u4e0d\u5230 150 \u2717 "
    "| `1404`\uff08\u4e24\u53e5\uff09\u3001**`1415` \u7684\u5fc6\u7075\u6280\u80fd 8** \u2713 **\u5171 3 \u4f4d** \u2713 "
    "| `CAST_SKILL` \u5728\u89e3\u6790\u6280\u80fd\u65f6**\u91cd\u65b0\u8bfb\u69fd**\uff0c**\u6216** \u8ba9\u6362\u88c5\u5728\u89c4\u5219\u5f00\u59cb\u524d\u5b8c\u6210 \u2713 "
    "|")
print("ok   §3 names the one real blocker")

# ---- 2. §2: the half that ships
ROW = ("| **\u300c\u6d88\u8017\u7b49\u540c\u4e8e\u2026**\u5f53\u524d**\u751f\u547d\u503c X% \u7684\u751f\u547d\u503c\u300d** "
       "| `CONSUME_HP` \uff0b **`scale: \"target_current_hp\"`**\uff08\u65b0\u589e\uff1a**\u5f53\u524d**\u751f\u547d\u503c\u7684\u4efd\u989d \u2713\uff1b"
       "\u65e7\u8bcd\u6c47\u53ea\u6709 `owner_max_hp`\uff0f`target_max_hp`\uff0f`target_lost_hp` \u2717\uff09 "
       "| `src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java`\u3001`src/main/resources/characters/1404.json` "
       "| `MydeiBloodfeudSkillsTest` |")
ANCHOR = "| **\u300c\u67d0\u4e2a\u72b6\u6001\u7684\u6301\u7eed\u56de\u5408\u6570\u5728**"
target = [index for index, line in enumerate(lines) if line.startswith(ANCHOR)]
if len(target) != 1:
    sys.exit("REFUSING: %d §2 anchors" % len(target))
lines.insert(target[0] + 1, ROW)
io.open(PATH, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   §2 gains the current-HP share")
