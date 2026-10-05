"""1415's memosprite skill 12 「献予「浪漫」之诗」 -- the two effects that last until a state ends (2026-10-02).

The sentence: 「阿格莱雅与衣匠造成的伤害提高 #2[i]% 并无视目标 #3[i]% 的防御，<b>持续至阿格莱雅退出【至高之姿】状态</b>。」

Why this is data and not a new lifetime:
  * `#2` and `#3` run with the skill level (0.36 -> 1.008 and 0.18 -> 0.504 over the ten rows of `11415/14`), so they are read through
    `percent_from_cast_param` -- the field shipped for exactly this;
  * the two attributes are already shipped spellings (`ALL_DAMAGE_TYPE_BOOST`, and `DEFENCE_IGNORE` which 1302 / 1303 already use);
  * the lifetime is the sentence's own: a companion rule on `STATE_ENDED` reading `<subject> state_ended <state>`, which removes the modifiers
    again. That works because `AbstractBuff.buffName` exists and `BuffManager.removeState(String)` takes named buffs off -- and the name is the
    game's own modifier name for this buff, taken from `GlobalModifiers` (`MServant_CyreneServant_00_AmazingBuff_Aglaea`), not invented.
  ⚠ 「至高之姿」 is a state our content already has (1402.json, applied by her ultimate).
"""
import io
import json
import re
import sys

CHARS = "src/main/resources/characters/1402.json"
SKILLS = "src/main/resources/data/skills.json"
TB = "E:/turnbasedgamedata"
SLOT = 14
STANCE = "\u81f3\u9ad8\u4e4b\u59ff"                  # 至高之姿, already a state in 1402.json
HANDLE = "MServant_CyreneServant_00_AmazingBuff_Aglaea"   # the game's own modifier name, from GlobalModifiers

# the two attributes the damage formula reads, both already shipped spellings
BOOST, PIERCE = "ALL_DAMAGE_TYPE_BOOST", "DEFENCE_IGNORE"

table = json.load(io.open(SKILLS, encoding="utf-8"))
rows = table["11415"][str(SLOT)].get("param_list") or []
if len(rows) < 3 or rows[0][1] == rows[-1][1]:
    sys.exit("REFUSING: #2 does not vary with level, so the reading below would be wrong")
print("ok   #2 runs %s -> %s and #3 runs %s -> %s over %d rows"
      % (rows[0][1], rows[-1][1], rows[0][2], rows[-1][2], len(rows)))

doc = json.load(io.open(CHARS, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
if any(r.get("id") == "memosprite_ode_of_romance_raises_damage_and_pierces_defence" for r in rules):
    sys.exit("REFUSING: already there")

def named(attribute, index, target):
    return {"op": "MODIFY_ATTR", "attribute": attribute, "percent_from_cast_param": index,
            "permanent": True, "buff": HANDLE, "target": target}

rules.append({
    "id": "memosprite_ode_of_romance_raises_damage_and_pierces_defence",
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": [named(BOOST, 1, "self"), named(PIERCE, 2, "self"),
           named(BOOST, 1, "summon"), named(PIERCE, 2, "summon")],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 12 \u300c\u732e\u4e88\u300c\u6d6a\u6f2b\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 14\uff09\uff1a"
               "\u300c**\u963f\u683c\u83b1\u96c5\u4e0e\u8863\u5320\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad8 #2% \u5e76\u65e0\u89c6\u76ee\u6807 #3% \u7684\u9632\u5fa1**\uff0c"
               "\u6301\u7eed\u81f3\u963f\u683c\u83b1\u96c5\u9000\u51fa\u3010" + STANCE + "\u3011\u72b6\u6001\u3002\u300d"),
    "note": ("\u2b50 `buff` \u662f\u8fd9\u4e24\u4e2a\u4fee\u9970\u7684**\u540d\u5b57**\uff0c\u7528\u7684\u662f\u6e38\u620f\u81ea\u5df1\u7684\u4fee\u9970\u540d "
             "`" + HANDLE + "`\uff08\u53d6\u81ea `GlobalModifiers`\uff09\u2014\u2014 \u56e0\u4e3a\u4e0b\u4e00\u6761\u89c4\u5219\u8981**\u70b9\u540d\u628a\u5b83\u4eec\u6458\u6389**\u3002"
             "\u2b50 \u4e24\u4e2a\u6570\u503c\u90fd\u8d70 `percent_from_cast_param`\uff08#2 \u21d2 \u7d22\u5f15 1\uff0c#3 \u21d2 \u7d22\u5f15 2\uff09\uff0c"
             "\u56e0\u4e3a\u5b83\u4eec**\u968f\u7b49\u7ea7\u53d8**\u3002"),
})

rules.append({
    "id": "memosprite_ode_of_romance_ends_with_her_stance",
    "on": "STATE_ENDED",
    "when": ["self state_ended " + STANCE],
    "do": [{"op": "REMOVE_STATE", "buff": HANDLE, "target": "self"},
           {"op": "REMOVE_STATE", "buff": HANDLE, "target": "summon"}],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 12\uff1a\u300c\u6301\u7eed\u81f3\u963f\u683c\u83b1\u96c5\u9000\u51fa\u3010" + STANCE + "\u3011\u72b6\u6001\u300d"
               "\u2014\u2014 \u2b50 \u8fd9\u5c31\u662f\u90a3\u53e5\u8bdd\u7684\u65f6\u957f\uff1a\u4e00\u6761\u770b\u7740 `STATE_ENDED` \u7684\u4f34\u968f\u89c4\u5219\u3002"),
    "note": ("\u2b50 \u5f15\u64ce\u7684\u65f6\u957f\u53ea\u6709 `turns`\uff0f`permanent`\uff0f\u4ee5\u53ca\u4e00\u4e2a**\u95ed\u96c6** `until`\uff08cast_end / next_attack / "
             "next_skill / turn_end\uff09\uff0c\u90fd\u8bf4\u4e0d\u4e86\u300c**\u6301\u7eed\u5230\u53e6\u4e00\u4e2a\u72b6\u6001\u7ed3\u675f**\u300d\u2014\u2014"
             "\u800c `STATE_ENDED` + `state_ended <\u540d\u5b57>` \u6b63\u662f\u4e3a\u6b64\u5b58\u5728\u7684\u3002"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1402 now carries both rules (%d rules)" % len(rules))
