"""The zone's additional damage, and the ode's extra instance (2026-10-02).

Both sentences, verbatim out of tbgd:

  1403 (缇宝) ultimate 140303: 「开启结界…**结界持续期间**，敌方目标受到的伤害提高 #2%。受到我方目标攻击后，**每有1名目标受到攻击**，会对**被攻击目标中当前生命值最高的目标**
                              造成 1 次**等同于缇宝 #3% 生命上限**的量子属性附加伤害…」
  1415 (昔涟) memosprite skill 15: 「…**缇宝施放追加攻击触发缇宝的结界的附加伤害时，会额外造成 #1 次附加伤害。**」

The first is 缇宝's OWN clause and our content did not have it (his zone only carried the vulnerability). The second is the ode's, and it is the reason the extra
instance is worth counting: it adds ONE more instance of that same additional damage (#1 = 1 at every level of 11415/15).
"""
import io
import json
import re
import sys

INT = "src/main/java/com/laosun/aluminium/models/TriggerInterpreter.java"
TRIBBIE = "src/main/resources/characters/1403.json"
SKILLS = "src/main/resources/data/skills.json"
ZONE = "结界"

# ---- 0) the "exactly one share" check must count the new spelling too ----
interp = io.open(INT, encoding="utf-8").read()
CHECK_OLD = "                } else if ((effect.getPercent() == null && effect.getPercentFromCastParam() == null)"
CHECK_NEW = ("                } else if ((effect.getPercent() == null && effect.getPercentFromCastParam() == null\n"
             "                        && effect.getPercentFromSkillParam() == null)")
if interp.count(CHECK_NEW) == 1:
    print("ok   the exactly-one-share check already counts percent_from_skill_param")
elif interp.count(CHECK_OLD) == 1:
    io.open(INT, "w", encoding="utf-8", newline="\n").write(interp.replace(CHECK_OLD, CHECK_NEW))
    print("ok   the exactly-one-share check now counts percent_from_skill_param")
else:
    sys.exit("REFUSING: the share check is neither the old nor the new shape")

# ---- 1) #3 is a share that runs with level, so it must come from the row ----
table = json.load(io.open(SKILLS, encoding="utf-8"))
# Note: The keys of this table are SKILL SLOTS ("3" == SkillType.ULTRA), not full skill ids -- measured.
rows = table["1403"]["3"].get("param_list") or []
if not rows or rows[0][2] == rows[-1][2]:
    sys.exit("REFUSING: #3 does not run with level")
print("ok   #3 runs %s -> %s over %d levels" % (rows[0][2], rows[-1][2], len(rows)))

doc = json.load(io.open(TRIBBIE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
for rid in ("ult_zone_additional_damage", "ode_of_passage_extra_instance"):
    if any(r.get("id") == rid for r in rules):
        sys.exit("REFUSING: %s is already there" % rid)

rules.append({
    "id": "ult_zone_additional_damage",
    "on": "ALLY_ATTACK",
    "when": ["self has_state " + ZONE],
    "do": [{
        "op": "DAMAGE",
        "times_from": "hit_count",                                   # "每有 1 名目标受到攻击"= how many the attack connected with
        "scale": "self_attr:HEALTH",                                 # 缇宝 生命上限
        "percent_from_skill_param": "ULTRA:2",                       #  x  #3, out of HIS OWN ultimate, at its own level
        "element": "Quantum",
        "target": "highest_hp_attack_hit",                           # "被攻击目标中当前生命值最高的目标"
    }],
    "source": ("1403 缇宝 终结技 (140303)：「受到我方目标攻击后，**每有 1 名目标受到攻击**，"
               "会对**被攻击目标中当前生命值最高的目标**造成 1 次**等同于缇宝 #3% 生命上限**的"
               "量子属性附加伤害。」"),
    "note": ("⭐ **三件都是本轮或近几轮出货的**：`times_from: \"hit_count\"`（“每有 1 名目标受到攻击”"
             "就是命中数）、`percent_from_skill_param: \"ULTRA:2\"`（#3 在**他自己的**终结技里，且随等级变）、"
             "`highest_hp_attack_hit`（被击目标里血最多的那个）。⭐ 门是上一轮出货的【" + ZONE + "】状态。"),
})

rules.append({
    "id": "ode_of_passage_extra_instance",
    "on": "DAMAGE_SETTLED",
    "when": ["actor == self", "damage_is_additional"],               # HIS additional damage -- which, in this kit, is the zone's
    "do": [{
        "op": "DAMAGE",
        "times": 1,                                                  # "额外造成 #1 次", and #1 is 1 at EVERY level of 11415/15
        "scale": "original_damage",                                  # one more instance OF THE SAME additional damage
        "percent": 1.0,
        "element": "Quantum",
        "target": "target",
    }],
    "source": ("1415 昔涟 忆灵技能 14 「献予「门径」之诗」（数据槽位 15，SkillID 1141515）："
               "「**缇宝施放追加攻击触发缇宝的" + ZONE + "的附加伤害时，会额外造成 #1 次附加伤害**。」"),
    "note": ("⭐ 为什么不写“来源”：原句的条件是“触发**结界**的附加伤害”，而 `damage_is_additional` 对**任何**附加伤害成立。"
             "本文件里他的附加伤害**只有结界那一条**（`ult_zone_additional_damage`），所以这两个写法在**本表内**等价；"
             "⚠ 但跨表就不一定了（反击等也是附加伤害）—— 这一点写在这里，供下次需要“来源”时查。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(TRIBBIE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1403 now carries both clauses (%d rules)" % len(rules))
