"""Keep the half that works, register the half that does not (round 5 of the goal).

Measured with a temporary probe, same scene, same enemy:
  * the content's cast deals 767.585; casting SLOT 9 directly deals 383.793 -- so `REPLACE_SKILL` installs the row into the map
    (`getSkillSlot() == 9`) but a `CAST_SKILL` later in the SAME rule still executes the OLD row. That is why the file used to
    claim 【弑王成王】 while running the plain skill.
  * the cost half works exactly: 1831.7376 -> 1190.6294 = 65% of the CURRENT value, through the new `target_current_hp` share.

So: the swap effect comes out, the cost stays, and the note says which half is missing and why.
"""
import io
import json
import os
import sys

CHAR = "src/main/resources/characters/1404.json"
PROBE = "src/test/java/com/laosun/aluminium/test/SwapReachesCastProbeTest.java"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next(entry for entry in doc["rules"]
            if isinstance(entry, dict) and entry.get("id") == "turn_start_autocasts_skill")
effects = rule["do"]
if not any(effect.get("op") == "REPLACE_SKILL" for effect in effects):
    sys.exit("REFUSING: the swap is not there")
rule["do"] = [effect for effect in effects if effect.get("op") != "REPLACE_SKILL"]
rule["note"] = (
    "\u300c\u3010\u8840\u4ec7\u3011\u72b6\u6001\u671f\u95f4\u2026\u81ea\u8eab\u56de\u5408\u5f00\u59cb\u65f6\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u738b\u6210\u738b\u3011\u300d\u21d2 `TURN_START` \uff0b `self has_state \u8840\u4ec7` \u21d2 "
    "`CONSUME_HP{scale: target_current_hp, percent: 0.35}` \u2713 \u21d2 `CAST_SKILL{skill: SKILL}` \u2713\u3002"
    "\u26a0\u26a0 **2026-10-02 \u4e09\u5904\u8ba2\u6b63\uff08\u5747\u5b9e\u6d4b \u2713\uff09**\uff1a\u2460 \u539f\u6765**\u6ca1\u6709 `self has_state \u8840\u4ec7` \u95e8** \u2717\uff08\u53e5\u5b50\u660e\u5199\u5728\u3010\u8840\u4ec7\u3011\u8bed\u5883\u91cc \u2713\uff09\uff1b"
    "\u2461 \u539f\u6765**\u6ca1\u6709\u4ed8\u4ee3\u4ef7** \u2717 \u21d2 \u672c\u8f6e\u5199\u51fa `CONSUME_HP` \u2713\uff08\u65b0\u589e**\u5f53\u524d\u751f\u547d\u503c**\u4efd\u989d "
    "`target_current_hp` \u2713 \u2014\u2014 \u65e7\u8bcd\u6c47\u53ea\u6709\u6700\u5927\u503c/\u5df2\u635f\u5931\u4e24\u79cd \u2717\uff0c\u800c 35% \u7684**\u6700\u5927\u503c**\u4f1a\u662f\u4e00\u4e2a\u770b\u4e0a\u53bb\u5f88\u5bf9\u7684\u9519\u6570 \u2717\uff09\uff1b"
    "\u2462 **\u65e7\u62a5\u7684 `CAST_SKILL{SKILL}` \u653e\u7684\u662f\u666e\u901a\u6218\u6280** \u2717\uff08\u69fd 2 \u2713\uff09\u3002"
    "\u26a0\u26a0 **\u800c\u201c\u6539\u653e\u3010\u5f11\u738b\u6210\u738b\u3011\u201d\u672c\u8f6e\u8bd5\u4e86\u5e76\u649e\u5230\u4e00\u4e2a\u786c\u4e8b\u5b9e \u2717**\uff1a"
    "`REPLACE_SKILL{skill: SKILL, skill_id: **9**}` **\u786e\u5b9e\u88c5\u8fdb\u4e86 map** \u2713\uff08`getSkills().get(SKILL).getSkillSlot()` \u8bfb\u5230 **9** \u2713\u3001category `BPSKILL` \u2713\uff09\uff0c"
    "\u26a0 \u4f46**\u540c\u4e00\u6761\u89c4\u5219\u91cc\u968f\u540e\u7684 `CAST_SKILL` \u4ecd\u6267\u884c\u65e7\u884c** \u2717\uff1a\u540c\u4e00\u573a\u666f\u91cc\uff0c\u5185\u5bb9\u90a3\u6761\u9020\u6210 "
    "**767.585** \u2713\u3001\u800c**\u76f4\u63a5**\u653e\u69fd 9 \u53ea\u6709 **383.793** \u2713\uff08\u4ed6\u653b\u51fb\u529b 426.888\u3001\u4e0a\u9650\u751f\u547d 1831.7376 \u2713\uff09\u3002"
    "\u21d2 \u6240\u4ee5\u6362\u88c5\u90a3\u4e00\u534a**\u672c\u8f6e\u4e0d\u8fdb\u6811** \u2717\uff0c\u767b\u8bb0\u5728 `EXPRESSION.md` \u00a73 \u2713\u3002"
    "\u26a0 \u4ecd\u767b\u8bb0 \u2713\uff1a\u5145\u80fd 150 \u90a3\u53e5\uff08\u2757 \u5b9e\u6d4b\uff1a\u300c\u5145\u80fd \u2265 100\u300d\u90a3\u6761**\u6ca1\u6709\u3010\u8840\u4ec7\u3011\u95e8** \u2717 \u21d2 \u5b83\u4f1a**\u53cd\u590d**\u89e6\u53d1\u5e76\u628a\u5145\u80fd\u62bd\u5e72 \u2717"
    "\uff0c\u5145\u80fd\u56e0\u6b64\u6512\u4e0d\u5230 150 \u2717\uff09\u3002")
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the cost stays, the swap comes out, and the note names the missing half")

if os.path.exists(PROBE):
    os.remove(PROBE)
    print("ok   the probe is gone (it answered)")

# the surviving reading still needs to read the whole rule, so the hand-built swap test keeps its own place in the judge
JUDGE = "src/test/java/com/laosun/aluminium/test/MydeiBloodfeudSkillsTest.java"
text = io.open(JUDGE, encoding="utf-8").read()
OLD = '        Assertions.assertTrue(enemyAfter < enemyBefore, "and the attack lands");'
NEW = ('        Assertions.assertTrue(enemyAfter < enemyBefore, "and the attack lands");\n'
       '        // \u26a0 This reading covers the COST half. The other half -- swapping slot 9 in so the cast runs \u3010\u5f11\u738b\u6210\u738b\u3011 -- does NOT\n'
       '        // work from inside a rule yet, measured: the swap lands in the map while a `CAST_SKILL` later in the same rule still runs the old\n'
       '        // row (767.585 against 383.793 for a direct cast of slot 9). Registered in EXPRESSION \u00a73, so the note does not claim it.')
if text.count(OLD) != 1:
    sys.exit("REFUSING: the judge anchor appears %d times" % text.count(OLD))
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text.replace(OLD, NEW, 1))
print("ok   the judge says which half it covers")
