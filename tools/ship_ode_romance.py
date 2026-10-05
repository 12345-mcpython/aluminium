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

names = sorted(set(re.findall(r"\u3010([^\u3011]+)\u3011", desc)))
print("states the description names: %s" % names)
state = names[0] if names else sys.exit("REFUSING: the description names no state")
if len(names) != 2:
    print("(it also names %s -- the other is a condition, not what this rule applies)" % names[1:])

# ---- the delivery entry, so the skill can be cast at all ----
SE = "src/main/resources/data/skill_effects.json"
table = json.load(io.open(SE, encoding="utf-8"))
entry = {
    "effect": "Rules",
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 12 (\u6570\u636e\u69fd\u4f4d 14, SkillID 1141514, SkillTriggerKey SkillCY02): "
               "\u300c\u5355\u6b21\u751f\u6548\uff0c\u5bf9\u963f\u683c\u83b1\u96c5\u65bd\u653e\u65f6\uff0c\u4f7f\u963f\u683c\u83b1\u96c5\u83b7\u5f97\u3010" + state + "\u3011\u2026\u300d"
               "\u5b83\u7684\u5de5\u4f5c\u5728**\u89c4\u5219\u4fa7**\uff0c\u6240\u4ee5\u662f `Rules` \u5f62\u72b6\u3002"),
    "note": "\u2b50 \u6ca1\u6709\u6761\u76ee\u5c31\u4e0d\u53ef\u4ea4\u4ed8\uff08`SkillExecutor.canDeliver`\uff09\uff0c\u800c\u6574\u6761\u5fc6\u7075\u6280\u80fd\u5c31\u6c38\u8fdc\u65bd\u653e\u4e0d\u4e86\u3002",
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
        "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 12 \u300c\u732e\u4e88\u300c\u6d6a\u6f2b\u300d\u4e4b\u8bd7\u300d\uff1a"
                   "\u300c**\u5bf9\u963f\u683c\u83b1\u96c5\u65bd\u653e\u65f6\uff0c\u4f7f\u963f\u683c\u83b1\u96c5\u83b7\u5f97\u3010" + state + "\u3011**\u300d\u3002"),
        "note": ("\u2605 `from_skill_id == 14` \u662f\u8be5\u6280\u80fd\u7684**\u6570\u636e\u69fd\u4f4d**\uff08\u6570\u636e\u9879 SkillID 1141514 \u21d2 \u69fd\u4f4d 14\uff09\uff0c"
                 "\u4e0e\u5df2\u51fa\u8d27\u7684\u7b2c\u4e94\u53e5\u540c\u5f62\u3002\u26a0 \u539f\u53e5\u7684\u5176\u4f59\u56db\u90e8\u5206\uff08\u8863\u5320\u5929\u8d4b\u53e0\u5c42\u3001\u653b\u51fb\u540e\u6d88\u8017\u56de\u80fd\u3001"
                 "\u4f24\u5bb3\u63d0\u9ad8 #2%\u3001\u65e0\u89c6\u76ee\u6807 #3% \u9632\u5fa1\uff09**\u672a\u5199**\uff0c\u5df2\u9010\u6761\u767b\u8bb0\u3002"),
    })
if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   characters/%d.json carries the rule" % RECIPIENT)
print("state name written: %r" % state)
