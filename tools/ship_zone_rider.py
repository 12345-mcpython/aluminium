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
ZONE = "结界"

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
    "source": ("1403 缇宝 终结技 (140303)：「受到我方目标攻击后，**每有 1 名目标受到攻击**，"
               "会对**被攻击目标中当前生命值最高的目标**造成 1 次**等同于缇宝 #3% 生命上限**的"
               "量子属性附加伤害。」"),
    "note": ("⭐ **为什么挂在 `DAMAGE_SETTLED`**：“每有 1 名目标受到攻击，会…造成 1 次”"
             "本身就是**每次命中**（一次 Blast 命中三个目标 = 三条实例 = 三次触发），所以**不需要 `times`**。"
             "⭐ **而 `damage_is_attack` 是引擎自己的注释开的药**：它上面写着 "
             "“a rule that reacts to 'my attack hit a burning target' would react to its own additional damage, **forever**” —— "
             "本条造的正是附加伤害，没有这道门就会**自己触发自己**。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(TRIBBIE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1403 now carries %s (%d rules)" % (RULE_ID, len(rules)))
