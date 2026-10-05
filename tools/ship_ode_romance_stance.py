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
STANCE = "至高之姿"                  # 至高之姿, already a state in 1402.json
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
    "source": ("1415 昔涟 忆灵技能 12 「献予「浪漫」之诗」（数据槽位 14）："
               "「**阿格莱雅与衣匠造成的伤害提高 #2% 并无视目标 #3% 的防御**，"
               "持续至阿格莱雅退出【" + STANCE + "】状态。」"),
    "note": ("⭐ `buff` 是这两个修饰的**名字**，用的是游戏自己的修饰名 "
             "`" + HANDLE + "`（取自 `GlobalModifiers`）—— 因为下一条规则要**点名把它们摘掉**。"
             "⭐ 两个数值都走 `percent_from_cast_param`（#2 ⇒ 索引 1，#3 ⇒ 索引 2），"
             "因为它们**随等级变**。"),
})

rules.append({
    "id": "memosprite_ode_of_romance_ends_with_her_stance",
    "on": "STATE_ENDED",
    "when": ["self state_ended " + STANCE],
    "do": [{"op": "REMOVE_STATE", "buff": HANDLE, "target": "self"},
           {"op": "REMOVE_STATE", "buff": HANDLE, "target": "summon"}],
    "source": ("1415 昔涟 忆灵技能 12：「持续至阿格莱雅退出【" + STANCE + "】状态」"
               "—— ⭐ 这就是那句话的时长：一条看着 `STATE_ENDED` 的伴随规则。"),
    "note": ("⭐ 引擎的时长只有 `turns`／`permanent`／以及一个**闭集** `until`（cast_end / next_attack / "
             "next_skill / turn_end），都说不了「**持续到另一个状态结束**」——"
             "而 `STATE_ENDED` + `state_ended <名字>` 正是为此存在的。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1402 now carries both rules (%d rules)" % len(rules))
