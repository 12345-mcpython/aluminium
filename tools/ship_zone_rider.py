"""The zone's additional damage -- 1403 缇宝's own clause (2026-10-02).

Verbatim (140303): 「开启结界…结界持续期间，敌方目标受到的伤害提高 #2%。<b>受到我方目标攻击后，每有1名目标受到攻击，会对被攻击目标中当前生命值最高的目标造成 1 次等同于缇宝 #3%
生命上限的量子属性附加伤害。</b>」

How each half is spelled, and why:
  * `on: DAMAGE_SETTLED` + `when: [damage_is_attack]` -- ⭐ 「每有 1 名目标受到攻击，会…造成 1 次」 IS the per-hit event: a Blast that connects with three enemies settles three
    instances, so the rule fires three times and nothing needs counting. ⚠ And `damage_is_attack` is the guard the engine's OWN comment prescribes for exactly this shape
    (「a rule that reacts to 'my attack hit a burning target' would react to its own additional damage, forever」) -- the instance this rule deals is additional damage, so
    without the guard it would re-trigger itself.
  * `target: highest_hp_attack_hit` -- 「被攻击目标中当前生命值最高的目标」, the selector shipped for this sentence.
  * `scale: self_attr:HEALTH` + `percent_from_skill_param: "ULTRA:2"` -- 「等同于缇宝 #3% 生命上限」: #3 is a parameter of HIS OWN ultimate and runs with level.
  * the gate `self has_state 结界` -- 「结界持续期间」, the state shipped for his ultimate.
"""
import io
import json
import sys

TRIBBIE = "src/main/resources/characters/1403.json"
SKILLS = "src/main/resources/data/skills.json"
ZONE = "\u7ed3\u754c"

table = json.load(io.open(SKILLS, encoding="utf-8"))
rows = table["1403"]["3"].get("param_list") or []
if not rows or rows[0][2] == rows[-1][2]:
    sys.exit("REFUSING: #3 does not run with level")
print("ok   #3 runs %s -> %s over %d levels (key \"3\" == SkillType.ULTRA)" % (rows[0][2], rows[-1][2], len(rows)))

doc = json.load(io.open(TRIBBIE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "ult_zone_additional_damage"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "DAMAGE_SETTLED",
    "when": ["damage_is_attack", "self has_state " + ZONE],
    "do": [{
        "op": "DAMAGE",
        "scale": "self_attr:HEALTH",
        "percent_from_skill_param": "ULTRA:2",
        "element": "Quantum",
        "target": "highest_hp_attack_hit",
    }],
    "source": ("1403 \u7f07\u5b9d \u7ec8\u7ed3\u6280 (140303)\uff1a\u300c\u53d7\u5230\u6211\u65b9\u76ee\u6807\u653b\u51fb\u540e\uff0c**\u6bcf\u6709 1 \u540d\u76ee\u6807\u53d7\u5230\u653b\u51fb**\uff0c"
               "\u4f1a\u5bf9**\u88ab\u653b\u51fb\u76ee\u6807\u4e2d\u5f53\u524d\u751f\u547d\u503c\u6700\u9ad8\u7684\u76ee\u6807**\u9020\u6210 1 \u6b21**\u7b49\u540c\u4e8e\u7f07\u5b9d #3% \u751f\u547d\u4e0a\u9650**\u7684"
               "\u91cf\u5b50\u5c5e\u6027\u9644\u52a0\u4f24\u5bb3\u3002\u300d"),
    "note": ("\u2b50 **\u4e3a\u4ec0\u4e48\u6302\u5728 `DAMAGE_SETTLED`**\uff1a\u201c\u6bcf\u6709 1 \u540d\u76ee\u6807\u53d7\u5230\u653b\u51fb\uff0c\u4f1a\u2026\u9020\u6210 1 \u6b21\u201d"
             "\u672c\u8eab\u5c31\u662f**\u6bcf\u6b21\u547d\u4e2d**\uff08\u4e00\u6b21 Blast \u547d\u4e2d\u4e09\u4e2a\u76ee\u6807 = \u4e09\u6761\u5b9e\u4f8b = \u4e09\u6b21\u89e6\u53d1\uff09\uff0c\u6240\u4ee5**\u4e0d\u9700\u8981 `times`**\u3002"
             "\u2b50 **\u800c `damage_is_attack` \u662f\u5f15\u64ce\u81ea\u5df1\u7684\u6ce8\u91ca\u5f00\u7684\u836f**\uff1a\u5b83\u4e0a\u9762\u5199\u7740 "
             "\u201ca rule that reacts to 'my attack hit a burning target' would react to its own additional damage, **forever**\u201d \u2014\u2014 "
             "\u672c\u6761\u9020\u7684\u6b63\u662f\u9644\u52a0\u4f24\u5bb3\uff0c\u6ca1\u6709\u8fd9\u9053\u95e8\u5c31\u4f1a**\u81ea\u5df1\u89e6\u53d1\u81ea\u5df1**\u3002"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(TRIBBIE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1403 now carries %s (%d rules)" % (RULE_ID, len(rules)))
