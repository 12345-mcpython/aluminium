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
ZONE = "\u7ed3\u754c"

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
# ⚠ The keys of this table are SKILL SLOTS ("3" == SkillType.ULTRA), not full skill ids -- measured.
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
        "times_from": "hit_count",                                   # 「每有 1 名目标受到攻击」= how many the attack connected with
        "scale": "self_attr:HEALTH",                                 # 缇宝 生命上限
        "percent_from_skill_param": "ULTRA:2",                       # × #3, out of HIS OWN ultimate, at its own level
        "element": "Quantum",
        "target": "highest_hp_attack_hit",                           # 「被攻击目标中当前生命值最高的目标」
    }],
    "source": ("1403 \u7f07\u5b9d \u7ec8\u7ed3\u6280 (140303)\uff1a\u300c\u53d7\u5230\u6211\u65b9\u76ee\u6807\u653b\u51fb\u540e\uff0c**\u6bcf\u6709 1 \u540d\u76ee\u6807\u53d7\u5230\u653b\u51fb**\uff0c"
               "\u4f1a\u5bf9**\u88ab\u653b\u51fb\u76ee\u6807\u4e2d\u5f53\u524d\u751f\u547d\u503c\u6700\u9ad8\u7684\u76ee\u6807**\u9020\u6210 1 \u6b21**\u7b49\u540c\u4e8e\u7f07\u5b9d #3% \u751f\u547d\u4e0a\u9650**\u7684"
               "\u91cf\u5b50\u5c5e\u6027\u9644\u52a0\u4f24\u5bb3\u3002\u300d"),
    "note": ("\u2b50 **\u4e09\u4ef6\u90fd\u662f\u672c\u8f6e\u6216\u8fd1\u51e0\u8f6e\u51fa\u8d27\u7684**\uff1a`times_from: \"hit_count\"`\uff08\u201c\u6bcf\u6709 1 \u540d\u76ee\u6807\u53d7\u5230\u653b\u51fb\u201d"
             "\u5c31\u662f\u547d\u4e2d\u6570\uff09\u3001`percent_from_skill_param: \"ULTRA:2\"`\uff08#3 \u5728**\u4ed6\u81ea\u5df1\u7684**\u7ec8\u7ed3\u6280\u91cc\uff0c\u4e14\u968f\u7b49\u7ea7\u53d8\uff09\u3001"
             "`highest_hp_attack_hit`\uff08\u88ab\u51fb\u76ee\u6807\u91cc\u8840\u6700\u591a\u7684\u90a3\u4e2a\uff09\u3002\u2b50 \u95e8\u662f\u4e0a\u4e00\u8f6e\u51fa\u8d27\u7684\u3010" + ZONE + "\u3011\u72b6\u6001\u3002"),
})

rules.append({
    "id": "ode_of_passage_extra_instance",
    "on": "DAMAGE_SETTLED",
    "when": ["actor == self", "damage_is_additional"],               # HIS additional damage -- which, in this kit, is the zone's
    "do": [{
        "op": "DAMAGE",
        "times": 1,                                                  # 「额外造成 #1 次」, and #1 is 1 at EVERY level of 11415/15
        "scale": "original_damage",                                  # one more instance OF THE SAME additional damage
        "percent": 1.0,
        "element": "Quantum",
        "target": "target",
    }],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 14 \u300c\u732e\u4e88\u300c\u95e8\u5f84\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 15\uff0cSkillID 1141515\uff09\uff1a"
               "\u300c**\u7f07\u5b9d\u65bd\u653e\u8ffd\u52a0\u653b\u51fb\u89e6\u53d1\u7f07\u5b9d\u7684" + ZONE + "\u7684\u9644\u52a0\u4f24\u5bb3\u65f6\uff0c\u4f1a\u989d\u5916\u9020\u6210 #1 \u6b21\u9644\u52a0\u4f24\u5bb3**\u3002\u300d"),
    "note": ("\u2b50 \u4e3a\u4ec0\u4e48\u4e0d\u5199\u201c\u6765\u6e90\u201d\uff1a\u539f\u53e5\u7684\u6761\u4ef6\u662f\u201c\u89e6\u53d1**\u7ed3\u754c**\u7684\u9644\u52a0\u4f24\u5bb3\u201d\uff0c\u800c `damage_is_additional` \u5bf9**\u4efb\u4f55**\u9644\u52a0\u4f24\u5bb3\u6210\u7acb\u3002"
             "\u672c\u6587\u4ef6\u91cc\u4ed6\u7684\u9644\u52a0\u4f24\u5bb3**\u53ea\u6709\u7ed3\u754c\u90a3\u4e00\u6761**\uff08`ult_zone_additional_damage`\uff09\uff0c\u6240\u4ee5\u8fd9\u4e24\u4e2a\u5199\u6cd5\u5728**\u672c\u8868\u5185**\u7b49\u4ef7\uff1b"
             "\u26a0 \u4f46\u8de8\u8868\u5c31\u4e0d\u4e00\u5b9a\u4e86\uff08\u53cd\u51fb\u7b49\u4e5f\u662f\u9644\u52a0\u4f24\u5bb3\uff09\u2014\u2014 \u8fd9\u4e00\u70b9\u5199\u5728\u8fd9\u91cc\uff0c\u4f9b\u4e0b\u6b21\u9700\u8981\u201c\u6765\u6e90\u201d\u65f6\u67e5\u3002"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(TRIBBIE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1403 now carries both clauses (%d rules)" % len(rules))
