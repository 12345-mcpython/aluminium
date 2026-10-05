import io, json, sys
P = "src/main/resources/characters/1404.json"
d = json.load(io.open(P, encoding="utf-8"))
rules = d if isinstance(d, list) else d.get("rules", [])
if not any(r.get("id") == "memosprite_ode_dispels_his_control_debuffs" for r in rules):
    bf = [w[len("self has_state "):] for r in rules if r.get("id") == "bloodfeud_godslayer_at_a_hundred_and_fifty"
          for w in r.get("when", []) if isinstance(w, str) and w.startswith("self has_state ")][0]
    rules.append({
        "id": "memosprite_ode_dispels_his_control_debuffs",
        "on": "CAST_SETUP",
        "when": ["target == self", "actor is_summon", "from_skill_id == 16"],
        "do": [{"op": "DISPEL", "kind": "control", "target": "self"}],
        "source": ("1415 昔涟 忆灵技能 8 「献予「纷争」之诗」（数据槽位 16，SkillID 1141516）："
                   "「对万敌施放时**解除万敌陷入的所有控制类负面状态**」。"
                   "（本条不带量：原句的「**所有**」不说数量，而 `kind` 单独出现即表示“该类全部”。）"),
        "note": ("★ 本轮给 `DISPEL` 加了**类别过滤**：它之前只能移除最近的 N 个，而类别是状态自己的属性"
                "`AbstractBuff.debuffClass()`。词汇本来就有（条件 `debuff_class:control`，以及 `RESIST_DEBUFF` 的 `kind`）。"),
    })
if isinstance(d, list):
    doc = rules
else:
    d["rules"] = rules
    doc = d
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
print("ok   the dispel clause is on 1404's table")
