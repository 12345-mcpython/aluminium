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
STANCE = "至高之姿"
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

SOURCE = ("1415 昔涟 忆灵技能 12 「献予「浪漫」之诗」（数据槽位 14）："
          "「阿格莱雅与衣匠造成的伤害提高 #2% 并无视目标 #3% 的防御，"
          "持续至阿格莱雅退出【" + STANCE + "】状态。」")
NOTE = ("⭐ 两个修饰带名字（`buff`，用游戏自己的修饰名 `" + HANDLE + "`），"
        "⭐ 数值走 `percent_from_cast_param`（#2 ⇒ 索引 1，#3 ⇒ 索引 2），因为它们随等级变。")

def named(target):
    return [{"op": "MODIFY_ATTR", "attribute": BOOST, "percent_from_cast_param": 1,
             "permanent": True, "buff": HANDLE, "target": target},
            {"op": "MODIFY_ATTR", "attribute": PIERCE, "percent_from_cast_param": 2,
             "permanent": True, "buff": HANDLE, "target": target}]

rules.append({
    "id": NEW_IDS[0], "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
    "do": named("self"), "source": SOURCE,
    "note": NOTE + " ⚠ 这一条只管她自己：「对衣匠也生效」需要忆灵在场，而那个条件不该压在她身上。",
})
rules.append({
    "id": NEW_IDS[1], "on": "CAST_SETUP",
    "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT), ENOUGH],
    "do": named("summon"), "source": SOURCE,
    "note": "「阿格莱雅**与衣匠**」的衣匠那一半。" + NOTE,
})
rules.append({
    "id": NEW_IDS[2], "on": "STATE_ENDED",
    "when": ["self state_ended " + STANCE],
    "do": [{"op": "REMOVE_STATE", "buff": HANDLE, "kind": "own", "target": "self"}],
    "source": SOURCE,
    "note": ("⭐ 时长：看着 `STATE_ENDED` 的伴随规则。⭐ `\"kind\": \"own\"` 是关键："
             "上一轮只按**名字**摘，把她**本来就有的 0.2** 也摘了下来（实测："
             "两边都变 0.0，而它们在这句诗之前都是 0.2）。这个来源筛子就是 "
             "`EXTEND_BUFF` 一直在用的那个（`extendBuffsFrom(ctx.owner(), …)`）。"),
})
rules.append({
    "id": NEW_IDS[3], "on": "STATE_ENDED",
    "when": ["self state_ended " + STANCE, ENOUGH],
    "do": [{"op": "REMOVE_STATE", "buff": HANDLE, "kind": "own", "target": "summon"}],
    "source": SOURCE,
    "note": "同上，但摘的是衣匠身上那份。",
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1402 now carries all four rules (%d rules)" % len(rules))
