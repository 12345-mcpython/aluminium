"""Roll back the two content edits, and register what was measured (round 4 of the goal).

Kept: nothing unverified. Recorded: the slot identification (9/11 by toughness) and the two failures, each with its own reading,
because those are the facts the next attempt needs.
"""
import io
import json
import os
import sys

CHAR = "src/main/resources/characters/1404.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/MydeiBloodfeudSkillsTest.java"
EXPRESSION = "EXPRESSION.md"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rules = doc["rules"]

# ---- 1. the turn-start rule goes back to exactly what it was
rule = next(entry for entry in rules if isinstance(entry, dict) and entry.get("id") == "turn_start_autocasts_skill")
rule["when"] = ["actor == self"]
rule["do"] = [{"op": "CAST_SKILL", "skill": "SKILL", "target": "target"}]
rule["note"] = ("\u2b50 2026-09-30\uff1a\u7528 `CAST_SKILL` \u8ba9\u90a3\u4e2a\u5355\u4f4d**\u7acb\u5373\u65bd\u653e\u4e00\u6b21** `SKILL` \u2713 \u2014\u2014 \u26a0 \u8bfb\u7684\u662f**\u5b83\u81ea\u5df1\u7684\u6570\u636e\u884c** \u2713"
                 "\uff08\u800c\u4e0d\u662f\u624b\u5199\u500d\u7387 \u2717\uff09\uff0c\u26a0 \u800c\u62a4\u680f\u4f1a\u62d2\u7edd**\u4e0d\u9020\u6210\u4f24\u5bb3**\u7684\u6280\u80fd \u2717\u3002"
                 "\u26a0 \u6765\u6e90\uff1a`CAST_SKILL` \u662f `commandSummon` \u653e\u5bbd\u4e09\u5904\u800c\u6765\uff08\u89c1 `aggro \u56de\u6536\u4e4b\u4e03\u767e\u516b\u5341\u4e94`\uff09\uff1b\u26a0 \u5b83\u4e0d\u7ecf\u8fc7\u6218\u6280\u70b9\u6d88\u8017 \u2713\u3002"
                 "\u26a0\u26a0 **2026-10-02 \u5b9e\u6d4b\u4e24\u4ef6\uff08\u672c\u8f6e\u8bd5\u5199\u540e\u56de\u6eda \u2713\uff09**\uff1a"
                 "\u2460 \u53e5\u5b50\u662f\u300c\u3010\u8840\u4ec7\u3011\u72b6\u6001\u671f\u95f4\u2026\u81ea\u8eab\u56de\u5408\u5f00\u59cb\u65f6\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u738b\u6210\u738b\u3011\u300d\u2713\uff0c"
                 "\u800c\u672c\u6761**\u6ca1\u6709 `self has_state \u8840\u4ec7` \u95e8** \u2717\u3001\u4e5f**\u6ca1\u6709\u5148\u628a\u69fd\u4f4d\u6362\u6210\u5f3a\u5316\u6218\u6280** \u2717"
                 "\uff08\u69fd **9** \u2713\uff0c\u89c1 `SkillData.init(1404, 9)` \u7834\u97e7 **60/30** \u2713 \u4e0e\u8bed\u6599\u9010\u5b57\u76f8\u7b26 \u2713\uff09"
                 "\u21d2 **\u58f0\u79f0\u4e0e\u6267\u884c\u4e0d\u4e00\u81f4** \u2717\uff1b\u2461 \u672c\u8f6e\u628a\u5b83\u6539\u6210\u201c\u5148\u6362\u518d\u653e\u201d\u5e76\u52a0\u4e0a\u95e8 \u2713 \u540e\uff0c"
                 "\u5224\u636e\u8bfb\u5230\uff1a\u654c\u4eba**\u6328\u4e86\u6253** \u2713\uff0816498.30 \u2192 15730.71 \u2713\uff09\u4f46\u4ed6**\u81ea\u5df1\u7684\u8840\u6ca1\u6389** \u2717\u3002"
                 "\u26a0 \u4e24\u79cd\u53ef\u80fd\u5c1a\u672a\u5206\u5f00 \u2717\uff1a\uff08a\uff09\u6362\u88c5\u6ca1\u751f\u6548 \u2717\uff08\u4ecd\u5728\u653e\u69fd 2 \u7684\u666e\u901a\u6218\u6280 \u2713\uff0c\u5b83\u4e5f\u4f1a\u9020\u4f24 \u2713\uff09\uff1b"
                 "\uff08b\uff09\u6362\u88c5\u751f\u6548\u4e86 \u2713 \u4f46\u6570\u636e\u884c\u91cc\u300c\u6d88\u8017\u7b49\u540c\u4e8e\u4e07\u654c**\u5f53\u524d\u751f\u547d\u503c 35%** \u7684\u751f\u547d\u503c\u300d\u8fd9\u4e00\u5217**\u6ca1\u6709\u88ab\u6267\u884c** \u2717\u3002"
                 "\u2b50 \u5206\u8fa8\u65b9\u6cd5\uff08\u4e0b\u4e00\u6b65 \u2713\uff09\uff1a\u76f4\u63a5\u91cf `him.getSkills().get(SKILL).getSkillSlot()` \u2014\u2014 "
                 "\u539f\u59cb\u884c\u662f **2** \u2713\u3001\u6362\u5165\u540e\u662f **9** \u2713\uff08`DefaultSkill.getSkillSlot()` \u8fd4\u56de\u7684\u5c31\u662f\u5b83\u88ab\u6784\u9020\u65f6\u7684\u69fd \u2713\uff09\u3002")
rule["source"] = None
rule.pop("source", None)

# ---- 2. the 150-charge rule comes out
kept = [entry for entry in rules
        if not (isinstance(entry, dict) and entry.get("id") == "bloodfeud_godslayer_at_a_hundred_and_fifty")]
if len(kept) != len(rules) - 1:
    sys.exit("REFUSING: the 150-charge rule was not found")
doc["rules"] = kept
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   1404 is back to what it was (%d rules)" % len(kept))

if os.path.exists(JUDGE):
    os.remove(JUDGE)
    print("ok   the unverified judge is gone")

# ---- 3. the §3 row now carries the two failures and the discriminator
lines = io.open(EXPRESSION, encoding="utf-8").read().split("\n")
PREFIX = "| **\u300c\u5145\u80fd\u8fbe\u5230 150 \u70b9\u65f6"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d §3 rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| **\u300c\u5145\u80fd\u8fbe\u5230 150 \u70b9\u65f6\uff0c\u7acb\u5373\u83b7\u5f97 1 \u4e2a\u989d\u5916\u56de\u5408\u5e76\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**\uff08\u4e07\u654c\u7684**\u5f3a\u5316\u6218\u6280** \u2713\uff09 "
    "| \u2b50 \u5b9e\u6d4b\u5df2\u628a\u5b83\u7f29\u5230**\u4e24\u4ef6** \u2713\uff1a\u2460 **\u5f3a\u5316\u6218\u6280\u7684\u81ea\u8eab\u4ee3\u4ef7\u6ca1\u6709\u88ab\u6267\u884c** \u2717"
    "\uff08\u5224\u636e\uff1a\u654c\u4eba\u6328\u6253 16498.30\u219215730.71 \u2713 \u800c\u4ed6\u81ea\u5df1\u7684\u8840\u4e0d\u52a8 \u2717 \u2014\u2014 \u26a0 \u5c1a\u672a\u5206\u5f00\u201c\u6362\u88c5\u6ca1\u751f\u6548\u201d\u4e0e\u201c\u8be5\u5217\u6ca1\u6267\u884c\u201d \u2717\uff09\uff1b"
    "\u2461 \u300c`resource_changed:<\u540d>`\u300d**\u9700\u8981\u7531 op \u62ac\u8d77\u4e8b\u4ef6** \u2717\uff08\u5224\u636e\uff1a\u76f4\u63a5 `fireTriggers(RESOURCE_CHANGED, …)` \u65f6 \u5145\u80fd 150\u2192150\u3001\u654c\u4eba\u6beb\u53d1\u65e0\u635f \u2717\uff1b"
    "\u2620 `Cone20024Test` \u7684\u6ce8\u8bb0\u8bf4\u7684\u5c31\u662f\u8fd9\u4ef6\uff1a\u201c**the op's own change** fired RESOURCE_CHANGED \u2014 that is the wiring\u201d \u2713\uff09 "
    "| `1404`\uff08 1 \u4f4d\uff09\u3001**`1415` \u7684\u5fc6\u7075\u6280\u80fd 8** \u2713 **\u5171 2 \u4f4d** \u2713 "
    "| \u2b50 **\u69fd\u4f4d\u5df2\u6307\u540d** \u2713\uff1a\u3010\u5f11\u738b\u6210\u738b\u3011=\u69fd **9** \u2713\u3001\u3010\u5f11\u795e\u767b\u795e\u3011=\u69fd **11** \u2713"
    "\uff08\u7531 `SkillData.init(1404, \u69fd).stanceFor(\u2026)` \u7684\u7834\u97e7 **60/30** \u4e0e **90/60** \u5bf9\u4e0a\u8bed\u6599 \u2713\uff0c\u6807\u5b9a\u7528 `1301` \u7684\u69fd 8 \u2713\uff09\uff1b"
    "\u4e0b\u4e00\u6b65\uff1a\u5148\u7528 `getSkillSlot()` \u628a\u201c\u6362\u88c5\u662f\u5426\u751f\u6548\u201d\u5206\u5f00 \u2713 |")
io.open(EXPRESSION, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the §3 row carries both failures and the named slots")
