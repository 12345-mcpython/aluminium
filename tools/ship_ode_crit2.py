import io, json
P = "src/main/resources/characters/1404.json"
d = json.load(io.open(P, encoding="utf-8"))
rules = d if isinstance(d, list) else d.get("rules", [])
if not any(r.get("id") == "memosprite_ode_raises_his_crit_damage_for_this_cast" for r in rules):
    import re
    bf = [w[len("self has_state "):] for r in rules if r.get("id") == "bloodfeud_godslayer_at_a_hundred_and_fifty"
          for w in r.get("when", []) if isinstance(w, str) and w.startswith("self has_state ")][0]
    J = "src/test/java/com/laosun/aluminium/test/OdeToStrifeCritTest.java"
    j = io.open(J, encoding="utf-8").read().replace("@BLOODFEUD@", bf)
    io.open(J, "w", encoding="utf-8", newline="\n").write(j)
    print("ok   the judge uses the file's own state name")
    rules.append({
        "id": "memosprite_ode_raises_his_crit_damage_for_this_cast",
        "on": "CAST_SETUP",
        "when": ["target == self", "actor is_summon", "from_skill_id == 16"],
        "do": [{"op": "MODIFY_ATTR", "attribute": "CRIT_ATTACK", "percent": 2.0, "until": "cast_end", "target": "self"}],
        "source": ("1415 昔涟 忆灵技能 8 「献予「纷争」之诗」（数据槽位 16，SkillID 1141516）："
                   "「**本次攻击中**万敌的暴击伤害提高 `#1[i]%`」。"
                   "（满级 `#1` = 2，即 **+200%**；属性名 `CRIT_ATTACK` —— 游戏的 `CriticalDamageBase` 就映射到它。）"),
        "note": ("★ 「本次攻击中」是一个**寿命**，而引擎已经有这个拼法：`MODIFY_ATTR` 接受 `until`，"
                 "已出货的取值 **`cast_end`** 正是这个窗口（光锥 20001 用它表达同一形状）。"
                 "⚠ 本条**不**受【血仇】限制（原句如此），所以它自成一条。"),
    })
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(d if isinstance(d, dict) else rules, ensure_ascii=False, indent=2) + "\n")
print("ok   the crit clause is on 1404's table")
