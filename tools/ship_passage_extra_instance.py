"""1415's ode of passage, second sentence -- and the follow-up mark that makes it decidable (2026-10-02).

Verbatim (1141515): 「…**缇宝施放追加攻击触发缇宝的结界的附加伤害时，会额外造成 #1 次附加伤害。**」 #1 is 1 at every level.

The two ends that meet here:
  * his own follow-up (`talent_followup_on_other_ult`) now STATES `cast_category: "FOLLOW_UP"`, which the DAMAGE op stamps onto the instance;
  * the ode's clause ASKS it with `damage_is_follow_up`, plus `self has_state <the ode>` -- the mark the ode's first clause already applies.

⭐ And the mark is also the door that stops the recursion: the clause's own extra instance states no category, so `damage_is_follow_up` is FALSE for it and it cannot
re-trigger itself. (Without a word for this, a clause hanging on additional damage and dealing additional damage loops forever -- the engine's `Trigger recursion
exceeded 8 levels`, which it did raise while this was being written.)
"""
import io
import json
import sys

TRIBBIE = "src/main/resources/characters/1403.json"
SKILLS = "src/main/resources/data/skills.json"

doc = json.load(io.open(TRIBBIE, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])

follow = [r for r in rules if r.get("id") == "talent_followup_on_other_ult"]
if len(follow) != 1:
    sys.exit("REFUSING: the follow-up rule is not unique (%d)" % len(follow))
follow = follow[0]
if any(e.get("cast_category") for e in follow.get("do", [])):
    sys.exit("REFUSING: the follow-up already states a category")

# ⭐ the ode's own name, taken from the mark its first clause applies -- no second spelling
odes = [r for r in rules if r.get("id") == "memosprite_ode_of_passage_makes_his_damage_ignore_defence"]
if len(odes) != 1:
    sys.exit("REFUSING: the first clause is not unique (%d)" % len(odes))
marks = [e.get("buff") for e in odes[0].get("do", []) if e.get("op") == "APPLY_BUFF" and e.get("buff")]
if len(marks) != 1:
    sys.exit("REFUSING: the first clause applies no single mark (%s)" % marks)
ODE = marks[0]
print("the ode's mark is %r" % ODE)

table = json.load(io.open(SKILLS, encoding="utf-8"))
rows = table["11415"]["15"].get("param_list") or []
if not rows or any(r[0] != 1 for r in rows):
    sys.exit("REFUSING: #1 is not 1 at every level")
print("ok   #1 is 1 at all %d levels of 11415/15" % len(rows))

# 1) the follow-up declares itself
for e in follow["do"]:
    if e.get("op") == "DAMAGE":
        e["cast_category"] = "FOLLOW_UP"
follow["note"] = (follow.get("note", "") +
                  " ⭐ 2026-10-02：它声明了 `cast_category: \"FOLLOW_UP\"` —— "
                  "因为 1415 的「门径」之诗要问“这是否是**追加攻击**”，而这个标记同时也是**防止递归**的门。")

RULE_ID = "ode_of_passage_extra_instance"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "FOLLOW_UP",
    "when": ["actor == self", "damage_is_follow_up", "self has_state " + ODE],
    "do": [{
        "op": "DAMAGE",
        "times": 1,                                              # 「额外造成 #1 次」, and #1 is 1 at EVERY level
        "scale": "owner_max_hp",
        "percent_from_skill_param": "ULTRA:2",                    # 同一笔附加伤害：#3 × 生命上限
        "element": "Quantum",
        "target": "target",
    }],
    "source": ("1415 昔涟 忆灵技能 14 「献予「门径」之诗」（数据槽位 15，SkillID 1141515）："
               "「**缇宝施放追加攻击触发缇宝的结界的附加伤害时，会额外造成 #1 次附加伤害**。」"),
    "note": ("⭐ 三件都是本轮／近几轮出货的：`SkillCategory.FOLLOW_UP`（**新词**）、`cast_category`（DAMAGE op 把它印在实例上）、"
             "`damage_is_follow_up`（条件）。⭐ **而这个词同时就是防递归的门**：本条造的那笔实例"
             "**不声明类别**，所以 `damage_is_follow_up` 对它为**假**，它触发不了自己。"
             "⚠（没有这个词时，挂在附加伤害上、又产出附加伤害的规则会**永远循环**，"
             "引擎当时确实报了 `Trigger recursion exceeded 8 levels`。）"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(TRIBBIE, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   1403 now carries %s (%d rules) and its follow-up declares itself" % (RULE_ID, len(rules)))
