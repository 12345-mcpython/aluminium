"""Correct the 1408 row, and the stale half of 1404's register (round 3 of the goal).

Measured this round:
  * 【弑神登神】 is 万敌's ENHANCED skill (「强化战技：弑神登神 / Godslayer Be God」), not 1408's -- her page never mentions it, and the
    data tree only carries it on 1404's and 1415's pages;
  * 「自动施放【X】」 ALREADY has a shipped spelling: `CAST_SKILL` (1404, 1412, 1414, 1504, 1513), so the §3 row's "slot 11" premise
    is wrong twice over;
  * the real remaining piece is 1404's 「充能达到 150 点时立即获得 1 个额外回合并自动施放【弑神登神】」. Its prerequisites: `EXTRA_TURN`
    EXISTS, but `SkillType` has no 强化战技 slot, and his TWO enhanced skills would both be addressed as `SKILL` -- so writing it that
    way would silently cast the wrong one. That is a blocker, not a shorthand, so it gets registered precisely instead;
  * and 1404's own register lists 「自身回合开始时自动施放【弑王成王】」 as missing while the rule `turn_start_autocasts_skill` ships.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1404.json"
EXPRESSION = "EXPRESSION.md"

# ---- 1. the stale half of the register
with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next(entry for entry in doc["rules"]
            if isinstance(entry, dict) and entry.get("id") == "level_convention")
note = rule["note"]
OLD = "\u81ea\u8eab\u56de\u5408\u5f00\u59cb\u65f6**\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u738b\u6210\u738b\u3011**\uff1b"
NEW = ("\u81ea\u8eab\u56de\u5408\u5f00\u59cb\u65f6**\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u738b\u6210\u738b\u3011**\uff08\u2705 **\u5df2\u51fa\u8d27** \u2713\uff1a"
       "`turn_start_autocasts_skill` \u21d2 `CAST_SKILL{skill: SKILL}` \u2713 \u2014\u2014 \u26a0 \u672c\u884c\u539f\u672c\u628a\u5b83\u5217\u6210\u7f3a\u5931 \u2717\uff0c\u672c\u8f6e\u8ba2\u6b63 \u2713\uff09\uff1b")
if note.count(OLD) != 1:
    io.open("tools/_probe_register.txt", "w", encoding="utf-8", newline="\n").write(note)
    sys.exit("REFUSING: the register fragment appears %d times (the note is dumped for inspection)" % note.count(OLD))
rule["note"] = note.replace(OLD, NEW, 1)
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   1404's register no longer calls the shipped half missing")

# ---- 2. the §3 row, rewritten from the measurements
lines = io.open(EXPRESSION, encoding="utf-8").read().split("\n")
PREFIX = "| \u300c\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u795e\u767b\u795e\u3011\u300d"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d §3 rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| **\u300c\u5145\u80fd\u8fbe\u5230 150 \u70b9\u65f6\uff0c\u7acb\u5373\u83b7\u5f97 1 \u4e2a\u989d\u5916\u56de\u5408\u5e76\u81ea\u52a8\u65bd\u653e\u3010\u5f11\u795e\u767b\u795e\u3011\u300d**\uff08\u4e07\u654c\u7684**\u5f3a\u5316\u6218\u6280** \u2713\uff09 "
    "| \u4e00\u4e2a**\u5f3a\u5316\u6218\u6280**\u7684\u5bfb\u5740\u65b9\u5f0f \u2717 \u2014\u2014 \u26a0 \u5b9e\u6d4b\uff1a`EXTRA_TURN` **\u5df2\u6709** \u2713\uff0c"
    "\u4f46 `SkillType` \u6ca1\u6709\u5f3a\u5316\u6218\u6280\u69fd \u2717\uff0c\u800c\u4ed6**\u4e24\u4e2a**\u5f3a\u5316\u6218\u6280\uff08\u5f11\u738b\u6210\u738b \u2713\u3001\u5f11\u795e\u767b\u795e \u2713\uff09"
    "\u4f1a**\u540c\u65f6**\u6620\u5c04\u5230 `SKILL` \u2717 \u21d2 \u90a3\u6837\u5199\u4f1a**\u60c4\u60c4\u65bd\u653e\u9519\u7684\u90a3\u4e00\u4e2a** \u2717\uff08\u4e0d\u662f\u7b80\u5199 \u2717\uff09 "
    "| `1404`\uff081 \u4f4d\uff09\u3001**`1415` \u7684\u5fc6\u7075\u6280\u80fd 8**\uff08\u300c\u4f7f\u5176\u81ea\u52a8\u65bd\u653e1\u6b21\u4e0d\u6d88\u8017\u5145\u80fd\u7684\u3010\u5f11\u795e\u767b\u795e\u3011\u300d \u2713\uff09 **\u5171 2 \u4f4d** \u2713 "
    "| `SkillType` \u52a0\u4e00\u4e2a\u5f3a\u5316\u6218\u6280\u69fd\uff0c**\u6216** \u8ba9 `CAST_SKILL` \u6309**\u6570\u636e\u884c**\u5bfb\u5740 \u2713\uff08\u26a0 \u53c2\u8003 `1404` \u5df2\u51fa\u8d27\u7684 "
    "`turn_start_autocasts_skill` \u2713 \u2014\u2014 \u5b83\u8bfb\u7684\u662f**\u5b83\u81ea\u5df1\u7684\u6570\u636e\u884c** \u2713\uff09 |")
io.open(EXPRESSION, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the §3 row is corrected (1408 -> 1404's enhanced skill, two readers named)")
