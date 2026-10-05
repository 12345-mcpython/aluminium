"""1415's memosprite skill 12 「献予「浪漫」之诗」 -- the two effects that last until a state ends (2026-10-02).

The sentence: 「阿格莱雅与衣匠造成的伤害提高 #2[i]% 并无视目标 #3[i]% 的防御，<b>持续至阿格莱雅退出【至高之姿】状态</b>。」

Four rules, and every part is a shipped spelling:
  * `#2` / `#3` run with the skill level, so they are read through `percent_from_cast_param`;
  * `ALL_DAMAGE_TYPE_BOOST` and `DEFENCE_IGNORE` are both shipped attributes;
  * the lifetime is a companion rule on `STATE_ENDED` reading `self state_ended <至高之姿>`, which 1402.json already has as a state;
  * and the removal carries `"kind": "own"`, because removing by NAME alone took a pre-existing 0.2 of `ALL_DAMAGE_TYPE_BOOST` off her as well
    (measured last round: both units went to 0.0 although both held 0.2 before the ode). The source filter is the convention `EXTEND_BUFF` has
    always used (`extendBuffsFrom(ctx.owner(), …)`).
"""
import io
import json
import sys

CHARS = "src/main/resources/characters/1402.json"
SKILLS = "src/main/resources/data/skills.json"
SLOT = 14
STANCE = "\u81f3\u9ad8\u4e4b\u59ff"
HANDLE = "MServant_CyreneServant_00_AmazingBuff_Aglaea"
BOOST, PIERCE = "ALL_DAMAGE_TYPE_BOOST", "DEFENCE_IGNORE"
ENOUGH = "self_summon_count >= 1"

table = json.load(io.open(SKILLS, encoding="utf-8"))
rows = table["11415"][str(SLOT)].get("param_list") or []
if len(rows) < 3 or rows[0][1] == rows[-1][1] or rows[0][2] == rows[-1][2]:
    sys.exit("REFUSING: #2/#3 do not run with level")
print("ok   #2 %s -> %s ; #3 %s -> %s over %d rows" % (rows[0][1], rows[-1][1], rows[0][2], rows[-1][2], len(rows)))

doc = json.load(io.open(CHARS, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
NEW_IDS = ("memosprite_ode_of_romance_raises_damage_and_pierces_defence",
           "memosprite_ode_of_romance_also_reaches_the_garmentmaker",
           "memosprite_ode_of_romance_ends_with_her_stance",
           "memosprite_ode_of_romance_stance_end_also_clears_the_garmentmaker")
for new_id in NEW_IDS:
    if any(r.get("id") == new_id for r in rules):
        sys.exit("REFUSING: %s is already there" % new_id)

SOURCE = ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 12 \u300c\u732e\u4e88\u300c\u6d6a\u6f2b\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 14\uff09\uff1a"
          "\u300c\u963f\u683c\u83b1\u96c5\u4e0e\u8863\u5320\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad8 #2% \u5e76\u65e0\u89c6\u76ee\u6807 #3% \u7684\u9632\u5fa1\uff0c"
          "\u6301\u7eed\u81f3\u963f\u683c\u83b1\u96c5\u9000\u51fa\u3010" + STANCE + "\u3011\u72b6\u6001\u3002\u300d")
NOTE = ("\u2b50 \u4e24\u4e2a\u4fee\u9970\u5e26\u540d\u5b57\uff08`buff`\uff0c\u7528\u6e38\u620f\u81ea\u5df1\u7684\u4fee\u9970\u540d `" + HANDLE + "`\uff09\uff0c"
        "\u2b50 \u6570\u503c\u8d70 `percent_from_cast_param`\uff08#2 \u21d2 \u7d22\u5f15 1\uff0c#3 \u21d2 \u7d22\u5f15 2\uff09\uff0c\u56e0\u4e3a\u5b83\u4eec\u968f\u7b49\u7ea7\u53d8\u3002")

def named(target):
    return [{"op": "MODIFY_ATTR", "attribute": BOOST, "percent_from_cast_param": 1,
             "permanent": True, "buff": HANDLE, "target": target},
            {"op": "MODIFY_ATTR", "attribute": PIERCE, "percent_from_cast_param": 2,
             "permanent": True, "buff": HANDLE, "target": target}]

rules.append({
    "id": NEW_IDS[0], "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": named("self"), "source": SOURCE,
    "note": NOTE + " \u26a0 \u8fd9\u4e00\u6761\u53ea\u7ba1\u5979\u81ea\u5df1\uff1a\u300c\u5bf9\u8863\u5320\u4e5f\u751f\u6548\u300d\u9700\u8981\u5fc6\u7075\u5728\u573a\uff0c\u800c\u90a3\u4e2a\u6761\u4ef6\u4e0d\u8be5\u538b\u5728\u5979\u8eab\u4e0a\u3002",
})
rules.append({
    "id": NEW_IDS[1], "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT), ENOUGH],
    "do": named("summon"), "source": SOURCE,
    "note": "\u300c\u963f\u683c\u83b1\u96c5**\u4e0e\u8863\u5320**\u300d\u7684\u8863\u5320\u90a3\u4e00\u534a\u3002" + NOTE,
})
rules.append({
    "id": NEW_IDS[2], "on": "STATE_ENDED",
    "when": ["self state_ended " + STANCE],
    "do": [{"op": "REMOVE_STATE", "buff": HANDLE, "kind": "own", "target": "self"}],
    "source": SOURCE,
    "note": ("\u2b50 \u65f6\u957f\uff1a\u770b\u7740 `STATE_ENDED` \u7684\u4f34\u968f\u89c4\u5219\u3002\u2b50 `\"kind\": \"own\"` \u662f\u5173\u952e\uff1a"
             "\u4e0a\u4e00\u8f6e\u53ea\u6309**\u540d\u5b57**\u6458\uff0c\u628a\u5979**\u672c\u6765\u5c31\u6709\u7684 0.2** \u4e5f\u6458\u4e86\u4e0b\u6765\uff08\u5b9e\u6d4b\uff1a"
             "\u4e24\u8fb9\u90fd\u53d8 0.0\uff0c\u800c\u5b83\u4eec\u5728\u8fd9\u53e5\u8bd7\u4e4b\u524d\u90fd\u662f 0.2\uff09\u3002\u8fd9\u4e2a\u6765\u6e90\u7b5b\u5b50\u5c31\u662f "
             "`EXTEND_BUFF` \u4e00\u76f4\u5728\u7528\u7684\u90a3\u4e2a\uff08`extendBuffsFrom(ctx.owner(), \u2026)`\uff09\u3002"),
})
rules.append({
    "id": NEW_IDS[3], "on": "STATE_ENDED",
    "when": ["self state_ended " + STANCE, ENOUGH],
    "do": [{"op": "REMOVE_STATE", "buff": HANDLE, "kind": "own", "target": "summon"}],
    "source": SOURCE,
    "note": "\u540c\u4e0a\uff0c\u4f46\u6458\u7684\u662f\u8863\u5320\u8eab\u4e0a\u90a3\u4efd\u3002",
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1402 now carries all four rules (%d rules)" % len(rules))
