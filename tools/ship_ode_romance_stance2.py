"""1415's memosprite skill 12 「献予「浪漫」之诗」 -- the two effects that last until a state ends (2026-10-02).

The sentence: 「阿格莱雅与衣匠造成的伤害提高 #2[i]% 并无视目标 #3[i]% 的防御，<b>持续至阿格莱雅退出【至高之姿】状态</b>。」

Four rules, and every part is a shipped spelling:
  * `#2` / `#3` run with the skill level (0.36 -> 1.008 and 0.18 -> 0.504 over the ten rows of `11415/14`), so they are read through
    `percent_from_cast_param`;
  * `ALL_DAMAGE_TYPE_BOOST` (「造成的伤害提高」) and `DEFENCE_IGNORE` (1302 / 1303 already ship 「无视目标 …% 的防御力」);
  * the modifiers carry the GAME'S OWN name for this buff (`GlobalModifiers`: `MServant_CyreneServant_00_AmazingBuff_Aglaea`), because the
    lifetime is spelled by naming them again: `AbstractBuff.buffName` plus `BuffManager.removeState(String)` takes a named buff off;
  * the lifetime itself is a companion rule on `STATE_ENDED` reading `self state_ended <至高之姿>`, which `1402.json` already has as a state.
⚠ Split by subject, as the engine itself demands: a `target: "summon"` effect needs the memosprite on the field (`self_summon_count >= 1`), and
gating the whole rule would have made her own half conditional on 衣匠 being out.
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
    sys.exit("REFUSING: #2/#3 do not run with level, so this reading would be wrong")
print("ok   #2 %s -> %s ; #3 %s -> %s over %d rows" % (rows[0][1], rows[-1][1], rows[0][2], rows[-1][2], len(rows)))

doc = json.load(io.open(CHARS, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
existing = {r.get("id") for r in rules}
for new_id in ("memosprite_ode_of_romance_raises_damage_and_pierces_defence",
               "memosprite_ode_of_romance_also_reaches_the_garmentmaker",
               "memosprite_ode_of_romance_ends_with_her_stance",
               "memosprite_ode_of_romance_stance_end_also_clears_the_garmentmaker"):
    if new_id in existing:
        sys.exit("REFUSING: %s is already there" % new_id)

SOURCE = ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 12 \u300c\u732e\u4e88\u300c\u6d6a\u6f2b\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 14\uff09\uff1a"
          "\u300c\u963f\u683c\u83b1\u96c5\u4e0e\u8863\u5320\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad8 #2% \u5e76\u65e0\u89c6\u76ee\u6807 #3% \u7684\u9632\u5fa1\uff0c"
          "\u6301\u7eed\u81f3\u963f\u683c\u83b1\u96c5\u9000\u51fa\u3010" + STANCE + "\u3011\u72b6\u6001\u3002\u300d")
HANDLE_NOTE = ("\u2b50 \u4e24\u4e2a\u4fee\u9970\u90fd\u5e26**\u540d\u5b57**\uff08`buff`\uff09\uff0c\u7528\u7684\u662f\u6e38\u620f\u81ea\u5df1\u7684\u4fee\u9970\u540d "
               "`" + HANDLE + "`\uff08\u53d6\u81ea `GlobalModifiers`\uff09\u2014\u2014 \u56e0\u4e3a\u65f6\u957f\u662f\u9760**\u70b9\u540d\u518d\u6458\u4e00\u6b21**\u5199\u7684\uff1a"
               "`AbstractBuff.buffName` + `BuffManager.removeState(String)` \u4f1a\u628a\u5e26\u8be5\u540d\u5b57\u7684 buff \u6458\u4e0b\u6765\u3002"
               "\u2b50 \u6570\u503c\u90fd\u8d70 `percent_from_cast_param`\uff08#2 \u21d2 \u7d22\u5f15 1\uff0c#3 \u7d22\u5f15 2\uff09\uff0c\u56e0\u4e3a\u5b83\u4eec**\u968f\u7b49\u7ea7\u53d8**\u3002")

def named(target):
    return [{"op": "MODIFY_ATTR", "attribute": BOOST, "percent_from_cast_param": 1,
             "permanent": True, "buff": HANDLE, "target": target},
            {"op": "MODIFY_ATTR", "attribute": PIERCE, "percent_from_cast_param": 2,
             "permanent": True, "buff": HANDLE, "target": target}]

rules.append({
    "id": "memosprite_ode_of_romance_raises_damage_and_pierces_defence",
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": named("self"),
    "source": SOURCE,
    "note": HANDLE_NOTE + " \u26a0 \u8fd9\u4e00\u6761\u53ea\u7ba1**\u5979\u81ea\u5df1**\uff0c\u56e0\u4e3a\u300c\u5bf9\u8863\u5320\u4e5f\u751f\u6548\u300d\u9700\u8981\u5fc6\u7075\u5728\u573a\uff0c\u800c\u90a3\u4e2a\u6761\u4ef6\u4e0d\u8be5\u538b\u5728\u5979\u8eab\u4e0a\u3002",
})
rules.append({
    "id": "memosprite_ode_of_romance_also_reaches_the_garmentmaker",
    "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT), ENOUGH],
    "do": named("summon"),
    "source": SOURCE,
    "note": "\u300c**\u963f\u683c\u83b1\u96c5\u4e0e\u8863\u5320**\u9020\u6210\u7684\u4f24\u5bb3\u63d0\u9ad8\u2026\u300d\u2014\u2014 \u8863\u5320\u90a3\u4e00\u534a\u3002" + HANDLE_NOTE,
})
rules.append({
    "id": "memosprite_ode_of_romance_ends_with_her_stance",
    "on": "STATE_ENDED",
    "when": ["self state_ended " + STANCE],
    "do": [{"op": "REMOVE_STATE", "buff": HANDLE, "target": "self"}],
    "source": SOURCE,
    "note": ("\u2b50 \u8fd9\u5c31\u662f\u300c**\u6301\u7eed\u81f3\u963f\u683c\u83b1\u96c5\u9000\u51fa\u3010" + STANCE + "\u3011\u72b6\u6001**\u300d\u7684\u65f6\u957f\uff1a"
             "\u4e00\u6761\u770b\u7740 `STATE_ENDED` \u7684\u4f34\u968f\u89c4\u5219\u3002\u5f15\u64ce\u81ea\u5df1\u7684\u65f6\u957f\u53ea\u6709 `turns`\uff0f`permanent`\uff0f"
             "\u4e00\u4e2a\u95ed\u96c6 `until`\uff08cast_end\u3001next_attack\u3001next_skill\u3001turn_end\uff09\uff0c\u90fd\u8bf4\u4e0d\u4e86\u8fd9\u53e5\u3002"),
})
rules.append({
    "id": "memosprite_ode_of_romance_stance_end_also_clears_the_garmentmaker",
    "on": "STATE_ENDED",
    "when": ["self state_ended " + STANCE, ENOUGH],
    "do": [{"op": "REMOVE_STATE", "buff": HANDLE, "target": "summon"}],
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
