"""`1141514` 献予「浪漫」之诗, the half that is expressible today (2026-10-02).

The sentence, verbatim from tbgd (`AvatarServantSkillConfig` -> `SkillDesc` -> TextMap):
  「单次生效，对阿格莱雅施放时，使阿格莱雅获得【浪漫】并使衣匠忆灵天赋的速度提高效果层数立即叠加至上限。阿格莱雅或衣匠攻击后，消耗【浪漫】
   为自身恢复 #1[i] 点能量。阿格莱雅与衣匠造成的伤害提高 #2[i]% 并无视目标 #3[i]% 的防御，持续至阿格莱雅退出【至高之姿】状态。」

What this file lands, and why only this:
  * 「使阿格莱雅获得【浪漫】」 is a plain `APPLY_BUFF` -- the state's name is READ OUT of the game's own description (the 【…】 brackets),
    never typed by hand;
  * the skill also needs a `skill_effects.json` entry, or `SkillExecutor.canDeliver` refuses it and the whole ode can never be cast -- the
    `Rules` shape (its work is on the rule side).
⚠ Registered instead of written: 「衣匠忆灵天赋的速度提高层数立即叠加至上限」, 「攻击后消耗【浪漫】恢复 #1 点能量」, 「伤害提高 #2%」,
「无视目标 #3% 的防御」 -- each needs something measured first (a memosprite-talent stack operation; an ally-attack consume; and whether
"ignore X% DEF" is `DAMAGE_PENETRATION` at all).
"""
import io
import json
import re
import sys

TB = "E:/turnbasedgamedata"
SKILL_ID = 1141514
RECIPIENT = 1402          # 阿格莱雅: `Servant_AglaeaServant_00` is her memosprite, so the ode is hers

chs = json.load(io.open(TB + "/TextMap/TextMapCHS.json", encoding="utf-8"))
rows = json.load(io.open(TB + "/ExcelOutput/AvatarServantSkillConfig.json", encoding="utf-8"))
row = [x for x in rows if x["SkillID"] == SKILL_ID and x.get("Level") == 1][0]
desc = chs.get(str(row["SkillDesc"]["Hash"]), "")
print("description length: %d" % len(desc))

names = sorted(set(re.findall(r"【([^】]+)】", desc)))
print("states the description names: %s" % names)
state = names[0] if names else sys.exit("REFUSING: the description names no state")
if len(names) != 2:
    print("(it also names %s -- the other is a condition, not what this rule applies)" % names[1:])

# ---- the delivery entry, so the skill can be cast at all ----
SE = "src/main/resources/data/skill_effects.json"
table = json.load(io.open(SE, encoding="utf-8"))
entry = {
    "effect": "Rules",
    "source": ("1415 昔涟 忆灵技能 12 (数据槽位 14, SkillID 1141514, SkillTriggerKey SkillCY02): "
               "「单次生效，对阿格莱雅施放时，使阿格莱雅获得【" + state + "】…」"
               "它的工作在**规则侧**，所以是 `Rules` 形状。"),
    "note": "⭐ 没有条目就不可交付（`SkillExecutor.canDeliver`），而整条忆灵技能就永远施放不了。",
}
table.setdefault("11415", {})["14"] = entry
io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(table, ensure_ascii=False, indent=2) + "\n")
print("ok   skill_effects.json: 11415/14 = Rules")

# ---- the rule, on the RECIPIENT's table (the odes' convention) ----
CHARS = "src/main/resources/characters/%d.json" % RECIPIENT
doc = json.load(io.open(CHARS, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
if not any(r.get("id") == "memosprite_ode_of_romance_marks_aglaea" for r in rules):
    rules.append({
        "id": "memosprite_ode_of_romance_marks_aglaea",
        "on": "CAST_SETUP",
        "when": ["target == self", "actor is_summon", "from_skill_id == 14"],
        "do": [{"op": "APPLY_BUFF", "buff": state, "permanent": True, "target": "self"}],
        "source": ("1415 昔涟 忆灵技能 12 「献予「浪漫」之诗」："
                   "「**对阿格莱雅施放时，使阿格莱雅获得【" + state + "】**」。"),
        "note": ("★ `from_skill_id == 14` 是该技能的**数据槽位**（数据项 SkillID 1141514 ⇒ 槽位 14），"
                 "与已出货的第五句同形。⚠ 原句的其余四部分（衣匠天赋叠层、攻击后消耗回能、"
                 "伤害提高 #2%、无视目标 #3% 防御）**未写**，已逐条登记。"),
    })
if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   characters/%d.json carries the rule" % RECIPIENT)
print("state name written: %r" % state)
