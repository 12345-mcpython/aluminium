"""1415's memosprite skill 14 「献予「门径」之诗」 -- the half about DEF, plus its delivery entry (2026-10-02).

The sentence, verbatim out of tbgd:
  「整场生效，<b>对缇宝施放时，使缇宝造成的伤害无视敌方目标 #2[i]% 的防御力。</b>缇宝施放追加攻击触发缇宝的结界的<u>附加伤害</u>时，会额外造成 #1[i] 次附加伤害。」

Measured before writing:
  * `#2` runs with the skill level (0.06 -> 0.168 over the ten rows of `11415/15`), so it goes through `percent_from_cast_param` (index 1);
  * `#1` is 1 at EVERY level, and it counts a number of extra instances -- a literal 1 says what the data says;
  * 缇宝 is cid **1403** in our own character data ("Tribbie");
  * 「无视…防御力」 is `DEFENCE_IGNORE`, already shipped by 1302 / 1303.
⚠ Registered rather than written: the second sentence, 「缇宝施放追加攻击触发缇宝的结界的<u>附加伤害</u>时，会额外造成 #1 次附加伤害」, which needs a
follow-up-attack event, the zone's own additional damage, and "N extra instances of it" -- none of which is a spelling the engine has today.
"""
import io
import json
import re
import sys

TB = "E:/turnbasedgamedata"
CHARS = "src/main/resources/characters/1403.json"      # 缇宝 / Tribbie
SKILLS = "src/main/resources/data/skills.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 15
PIERCE = "DEFENCE_IGNORE"
STANCE = None

table = json.load(io.open(SKILLS, encoding="utf-8"))
rows = table["11415"][str(SLOT)].get("param_list") or []
if len(rows) < 2 or rows[0][0] != 1 or any(r[0] != 1 for r in rows):
    sys.exit("REFUSING: #1 is not 1 at every level, so a literal would be an approximation")
if rows[0][1] == rows[-1][1]:
    sys.exit("REFUSING: #2 does not run with level")
print("ok   #1 is 1 at all %d levels ; #2 runs %s -> %s" % (len(rows), rows[0][1], rows[-1][1]))

# the ode's own name, out of the game's description of the first sentence (the [获得] route does not apply here)
chs = json.load(io.open(TB + "/TextMap/TextMapCHS.json", encoding="utf-8"))
desc_rows = json.load(io.open(TB + "/ExcelOutput/AvatarServantSkillConfig.json", encoding="utf-8"))
row = [x for x in desc_rows if x["SkillID"] == 1141515 and x.get("Level") == 1][0]
name = chs.get(str(row["SkillName"]["Hash"]), "")
if not name:
    sys.exit("REFUSING: the ode has no name in the TextMap")
print("the ode is %r" % name)

doc = json.load(io.open(CHARS, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "memosprite_ode_of_passage_makes_his_damage_ignore_defence"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": [
        # 「整场生效」: the ode marks him, so the piercing lasts as long as the battle does
        {"op": "APPLY_BUFF", "buff": name, "permanent": True, "target": "self"},
        {"op": "MODIFY_ATTR", "attribute": PIERCE, "percent_from_cast_param": 1,
         "permanent": True, "target": "self"},
    ],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 14 \u300c" + name + "\u300d\uff08\u6570\u636e\u69fd\u4f4d 15\uff0cSkillID 1141515\uff09\uff1a"
               "\u300c\u6574\u573a\u751f\u6548\uff0c**\u5bf9\u7f07\u5b9d\u65bd\u653e\u65f6\uff0c\u4f7f\u7f07\u5b9d\u9020\u6210\u7684\u4f24\u5bb3\u65e0\u89c6\u654c\u65b9\u76ee\u6807 #2% \u7684\u9632\u5fa1\u529b\u3002**\u300d"),
    "note": ("\u2b50 `DEFENCE_IGNORE` \u662f\u5df2\u51fa\u8d27\u7684\u5c5e\u6027\uff081302\uff0f1303 \u5df2\u5728\u7528\uff1a\u300c\u65e0\u89c6\u654c\u65b9\u76ee\u6807 30% \u7684\u9632\u5fa1\u529b\u300d\uff09\uff1b"
             "\u2b50 `#2` **\u968f\u7b49\u7ea7\u53d8**\uff080.06 \u2192 0.168\uff09\uff0c\u6240\u4ee5\u8d70 `percent_from_cast_param`\uff08\u7d22\u5f15 1\uff09\uff1b"
             "\u2b50 \u539f\u53e5\u8bf4\u300c**\u6574\u573a\u751f\u6548**\u300d\uff0c\u6240\u4ee5\u540c\u65f6\u7559\u4e0b\u4e00\u4e2a\u6301\u4e45\u5370\u8bb0\uff08\u5c31\u662f\u8fd9\u53e5\u8bd7\u7684\u540d\u5b57\uff09\u3002"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1403 now carries %s (%d rules)" % (RULE_ID, len(rules)))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) in effects.get("11415", {}):
    print("ok   skill_effects.json already has 11415/%d" % SLOT)
else:
    effects.setdefault("11415", {})[str(SLOT)] = {
        "effect": "Rules",
        "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 14 \u300c" + name + "\u300d\uff08\u6570\u636e\u69fd\u4f4d 15\uff09\uff1a"
                   "\u5de5\u4f5c\u5728**\u89c4\u5219\u4fa7**\uff0c\u6240\u4ee5\u662f `Rules` \u5f62\u72b6\u3002"),
        "note": "\u2b50 \u6ca1\u6709\u6761\u76ee\u5c31\u4e0d\u53ef\u4ea4\u4ed8\uff0c\u6574\u6761\u5fc6\u7075\u6280\u80fd\u5c31\u6c38\u8fdc\u65bd\u653e\u4e0d\u4e86\u3002",
    }
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)
