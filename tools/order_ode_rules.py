import io, json
P = "src/main/resources/characters/1404.json"
d = json.load(io.open(P, encoding="utf-8"))
rules = d if isinstance(d, list) else d.get("rules", [])
crit = [x for x in rules if x.get("id") == "memosprite_ode_raises_his_crit_damage_for_this_cast"]
if len(crit) != 1:
    raise SystemExit("REFUSING: the crit rule is not unique (%d)" % len(crit))
crit = crit[0]
rules.remove(crit)
idx = min(i for i, r in enumerate(rules) if str(r.get("id", "")).startswith("memosprite_ode_"))
rules.insert(idx, crit)
crit["note"] = ("★ 「本次攻击中」是一个**寿命**：`MODIFY_ATTR` 接受 `until`，已出货的 **`cast_end`** 正是这个窗口"
               "（光锥 20001 同形）。⚠ 本条**不**受【行仇】限制（原句如此）。\n"
               "⭐⭐ **位置是必要的**：同一事件上的规则按**文件顺序派发**（`byEvent...add(rule)`），"
               "而 `CAST_SKILL` 是**同步**执行的 —— 若本条排在命令那条**之后**，那一击已经结算完了，"
               "增益才贴上去；实测差别是 **4391.48 → 9080.73**。")
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(d if isinstance(d, dict) else rules, ensure_ascii=False, indent=2) + "\n")
print("order: %s" % [r.get("id") for r in rules if str(r.get("id", "")).startswith("memosprite_ode_")])
