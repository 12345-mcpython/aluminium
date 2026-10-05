"""1415's memosprite skill 12 「献予「浪漫」之诗」 -- the clause that has no duration (2026-10-02).

The sentence, verbatim out of tbgd:
  「单次生效，对阿格莱雅施放时，使阿格莱雅获得【浪漫】并使衣匠忆灵天赋的速度提高效果层数立即叠加至上限。<b>阿格莱雅或衣匠攻击后，消耗【浪漫】为自身恢复
   #1[i] 点能量。</b>阿格莱雅与衣匠造成的伤害提高 #2[i]% 并无视目标 #3[i]% 的防御，持续至阿格莱雅退出【至高之姿】状态。」

Why THIS clause and not the next one:
  * `#1` is 70 in EVERY level row of `11415/14` (measured: `[70, 0.36, 0.18]` … `[70, 1.008, 0.504]`), so writing 70 as a literal is not an
    approximation -- it is the number the data states at every level;
  * the clause after it carries a duration -- 「持续至阿格莱雅退出【至高之姿】状态」 -- and the engine's durations are `turns` or `permanent`.
    Writing either one would be a guess, so that half is registered instead (see GAPS).
"""
import io
import json
import re
import sys

CHARS = "src/main/resources/characters/1402.json"     # 阿格莱雅: the one the ode is cast on
SKILLS = "src/main/resources/data/skills.json"
TB = "E:/turnbasedgamedata"
SLOT = 14
ENERGY = 70.0

table = json.load(io.open(SKILLS, encoding="utf-8"))
rows = table["11415"][str(SLOT)].get("param_list") or []
if not rows or any(row[0] != ENERGY for row in rows):
    sys.exit("REFUSING: #1 is not the same at every level, so a literal would be an approximation")
print("ok   #1 is %.0f at all %d levels" % (ENERGY, len(rows)))

# the state name comes out of the game's own description, as it did for the first clause of this ode
chs = json.load(io.open(TB + "/TextMap/TextMapCHS.json", encoding="utf-8"))
desc_rows = json.load(io.open(TB + "/ExcelOutput/AvatarServantSkillConfig.json", encoding="utf-8"))
row = [x for x in desc_rows if x["SkillID"] == 1141514 and x.get("Level") == 1][0]
desc = chs.get(str(row["SkillDesc"]["Hash"]), "")
names = re.findall(r"获得【([^】]+)】", desc)
if len(names) != 1:
    sys.exit("REFUSING: expected exactly one 获得【…】, got %d" % len(names))
state = names[0]
print("the state, read out of the description: %r" % state)

doc = json.load(io.open(CHARS, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "memosprite_ode_of_romance_spends_itself_for_energy"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "ALLY_ATTACK",
    "when": ["self has_state " + state],
    "do": [
        {"op": "GAIN_ENERGY", "amount": ENERGY, "target": "self"},
        {"op": "REMOVE_STATE", "buff": state, "target": "self"},
    ],
    "source": ("1415 昔涟 忆灵技能 12 「献予「浪漫」之诗」（数据槽位 14）："
               "「**阿格莱雅或衣匠攻击后，消耗【" + state + "】为自身恢复 " + str(int(ENERGY)) + " 点能量。**」"),
    "note": ("⭐ 触发用 `ALLY_ATTACK`：原句是「阿格莱雅**或衣匠**攻击后」——"
             "两个主体都算，而能量给的是**规则持有者**（即阿格莱雅），"
             "所以两个效果都是 `target: \"self\"`。⭐ `#1` 在数据里**每一级都是 70**，"
             "所以写成字面量**不是**近似。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1402 now carries %s (%d rules)" % (RULE_ID, len(rules)))
