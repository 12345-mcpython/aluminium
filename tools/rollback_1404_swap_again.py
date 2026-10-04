"""Roll the swap back again, and correct the record: the comparison that "proved" the blocker was invalid.

Found by re-reading my own probe: scene 2 REPLACED his trigger table, so `level_convention` never ran and its slot-9 skill sat at
level 1, while the content scene runs at level 15. Two different levels are not a comparison, so last round's claim ("the swap
does not reach the commanded cast, 767.585 against 383.793") does NOT hold as measured -- what holds is the narrower fact that
`REPLACE_SKILL` installs the row into the map.
"""
import io
import json
import sys

CHAR = "src/main/resources/characters/1404.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/MydeiBloodfeudSkillsTest.java"

with io.open(CHAR, encoding="utf-8") as handle:
    doc = json.load(handle)
rule = next(entry for entry in doc["rules"]
            if isinstance(entry, dict) and entry.get("id") == "turn_start_autocasts_skill")
if not any(effect.get("op") == "REPLACE_SKILL" for effect in rule["do"]):
    sys.exit("REFUSING: the swap is not there")
rule["do"] = [effect for effect in rule["do"] if effect.get("op") != "REPLACE_SKILL"]
rule["note"] = rule["note"].replace(
    "\u2b50 **2026-10-02 \u7b2c\u4e8c\u6b21\u5c1d\u8bd5\uff1a\u6362\u88c5\u7684\u5bff\u547d\u6539\u6210 `turns: 1`** \u2713\uff08\u2757 \u7b2c\u4e00\u6b21\u7528 `until: next_attack` \u2717\uff0c"
    "\u800c\u5b83**\u5728\u653b\u51fb\u5f00\u59cb\u65f6\u5c31\u8fd8\u539f** \u2717 \u21d2 \u4f24\u5bb3\u4ecd\u7528\u65e7\u884c\u7b97 \u2717\uff1b\u8fd9\u6b63\u662f `1301` \u6ce8\u8bb0\u91cc\u5199\u7740\u201c**\u5c1a\u672a\u9489\u4f4f**\u201d\u7684\u90a3\u4ef6\u4e8b \u2713\uff09\u3002",
    "\u26a0\u26a0 **2026-10-02 \u4e24\u6b21\u5c1d\u8bd5\u5747\u56de\u6eda \u2713\uff0c\u800c\u4e14\u7b2c\u4e00\u6b21\u7684\u201c\u963b\u65ad\u201d\u5df2\u88ab\u8ba4\u5b9a\u4e3a\u65e0\u6548\u6d4b\u91cf \u2717**\uff1a"
    "\u2460 \u5bff\u547d\u8bd5\u4e86 `until: next_attack` \u2717 \u4e0e `turns: 1` \u2713\uff0c\u4f24\u5bb3\u90fd\u662f **767.585** \u2713 \u4e0d\u53d8 \u21d2 \u4e0d\u662f\u5bff\u547d\u95ee\u9898 \u2717\uff1b"
    "\u2461 \u800c\u7528\u6765\u5bf9\u6bd4\u7684\u90a3\u4e2a **383.793** \u2713 \u662f\u5728\u201c**\u6362\u6389\u4ed6\u6574\u5f20\u8868**\u201d\u7684\u573a\u666f\u91cc\u91cf\u7684 \u2717 \u2014\u2014 \u90a3\u6837 `level_convention` **\u6ca1\u8dd1** \u2717\uff0c"
    "\u69fd 9 \u505c\u5728 **1 \u7ea7** \u2717\uff0c\u800c\u5185\u5bb9\u573a\u666f\u662f **15 \u7ea7** \u2713 \u21d2 \u4e24\u4e2a\u6570**\u4e0d\u53ef\u6bd4** \u2717\u3002"
    "\u21d2 \u5982\u5b9e\u7684\u7ed3\u8bba\uff1a\u2b50 **\u53ea\u77e5\u9053\u6362\u88c5\u88c5\u8fdb\u4e86 map** \u2713\uff08`getSkillSlot()` \u8bfb\u5230 9 \u2713\uff09\uff1b"
    "\u2757 **\u201c\u88ab\u547d\u4ee4\u7684\u653e\u7528\u54ea\u4e00\u884c\u201d\u4ecd\u672a\u6d4b\u51c6** \u2717\uff08\u4e0b\u4e00\u6b65\uff1a\u5728**\u540c\u4e00\u573a\u666f\u3001\u540c\u4e00\u7b49\u7ea7**\u4e0b\u5bf9\u6bd4 \u2713\uff09\u3002")
with io.open(CHAR, "w", encoding="utf-8", newline="\n") as handle:
    json.dump(doc, handle, ensure_ascii=False, indent=2)
    handle.write("\n")
print("ok   the swap is out and the note records the invalid comparison")

text = io.open(JUDGE, encoding="utf-8").read()
MARKER = "    /**\n     * \u2b50 And the swap must REACH the cast"
index = text.find(MARKER)
if index < 0:
    sys.exit("REFUSING: the invalid reading was not found")
# drop the invalid reading and re-close the class
text = text[:index].rstrip()
if not text.endswith("}"):
    sys.exit("REFUSING: unexpected judge tail")
io.open(JUDGE, "w", encoding="utf-8", newline="").write(text + "\n}\n")
print("ok   the invalid comparison reading is gone")

lines = io.open("EXPRESSION.md", encoding="utf-8").read().split("\n")
PREFIX = "| **\u300c\u81ea\u52a8\u65bd\u653e\u3010\u5f3a\u5316\u6218\u6280\u3011\u300d**"
hits = [index for index, line in enumerate(lines) if line.startswith(PREFIX)]
if len(hits) != 1:
    sys.exit("REFUSING: %d §3 rows start with that prefix" % len(hits))
lines[hits[0]] = (
    "| **\u300c\u81ea\u52a8\u65bd\u653e\u3010\u5f3a\u5316\u6218\u6280\u3011\u300d**\uff08\u4e07\u654c\u7684\u3010\u5f11\u738b\u6210\u738b\u3011\u69fd **9** \u2713\u3001\u3010\u5f11\u795e\u767b\u795e\u3011\u69fd **11** \u2713\uff1b"
    "\u69fd\u53f7\u7531 `SkillData.init(1404, \u69fd).stanceFor(\u2026)` \u7684\u7834\u97e7 60/30 \u4e0e 90/60 \u5bf9\u4e0a\u8bed\u6599 \u2713\uff09 "
    "| \u2b50 \u4e00\u4e2a**\u53ef\u6bd4\u7684\u8bfb\u6570** \u2717 \u2014\u2014 \u2757 \u5df2\u77e5\uff1a`REPLACE_SKILL` **\u88c5\u8fdb\u4e86 map** \u2713\uff08`getSkillSlot()` \u8bfb\u5230 9 \u2713\u3001category `BPSKILL` \u2713\uff09\uff1b"
    "\u2757 **\u672a\u77e5**\uff1a\u88ab\u547d\u4ee4\u7684 `CAST_SKILL` \u5230\u5e95\u7528\u54ea\u4e00\u884c \u2717\uff08\u26a0 \u4e24\u6b21\u5c1d\u8bd5\u540e\u53d1\u73b0\uff1a\u6211\u7528\u6765\u5bf9\u6bd4\u7684\u90a3\u4e2a 383.793 \u662f\u5728\u201c**\u6362\u6389\u4ed6\u6574\u5f20\u8868**\u201d\u7684\u573a\u666f\u91cc\u91cf\u7684 \u2717\uff0c"
    "\u90a3\u6837 `level_convention` \u6ca1\u8dd1 \u2717 \u21d2 \u69fd 9 \u505c\u5728 1 \u7ea7\uff0c\u800c\u5185\u5bb9\u573a\u666f\u662f 15 \u7ea7 \u2713 \u21d2 \u4e0d\u53ef\u6bd4 \u2717\uff09 "
    "| `1404`\uff08\u4e24\u53e5\uff09\u3001**`1415` \u7684\u5fc6\u7075\u6280\u80fd 8** \u2713 **\u5171 3 \u4f4d** \u2713 "
    "| \u2b50 \u4e00\u4e2a**\u540c\u573a\u666f\u3001\u540c\u7b49\u7ea7**\u7684\u5bf9\u6bd4\u8bfb\u6570 \u2717\uff08\u5982\uff1a\u4e24\u573a\u90fd\u8ba9 `level_convention` \u8dd1 \u2713\uff0c\u53ea\u5dee\u201c\u6709\u65e0\u6362\u88c5\u201d \u2713\uff09 |")
io.open("EXPRESSION.md", "w", encoding="utf-8", newline="\n").write("\n".join(lines))
print("ok   the §3 row says exactly what is known and what is not")
