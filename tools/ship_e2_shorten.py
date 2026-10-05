import io, json, sys
P = "src/main/resources/characters/1217.json"
d = json.load(io.open(P, encoding="utf-8"))
rules = d if isinstance(d, list) else d.get("rules", [])
r = [x for x in rules if x.get("id") == "e2_saves_an_ally_with_the_talisman"][0]
# the counter cap: measured, a strict `<` at the counter's own max_stacks never holds; `<= 1` is the same statement
r["when"] = ["actor is_ally", "self has_state 穹命", "self_stacks:救主计数 <= 1"]
ops = [e.get("op") for e in r["do"]]
if "EXTEND_BUFF" not in ops:
    r["do"].append({"op": "EXTEND_BUFF", "buff": "穹命", "turns": -1, "target": "self"})
r["note"] = (r.get("note", "") +
    " ⭐ 2026-10-02: 补上第三句「使【穹命】的持续回合数**减 1**」⇒ "
    "`EXTEND_BUFF{buff: 穹命, turns: -1, target: self}`。⚠ 形状取自**游戏自己的配置**："
    "`Avatar_Huohuo_00_Rank02_Insert` 对具名 modifier 做 `SetModifierValue{ModifyFunction: \"Add\", ValueType: \"LifeTime\"}`"
    "（另一条同族任务是 `SetDynamicValueByAddValue{AddValue: -1, Min: 0}`）—— 即“对具名状态寿命做**带符号的 Add**”，"
    "所以是**负的 `turns`**，而不新增 op。⭐ 并把计数器上限改写为 `<= 1`："
    "实测，**严格 `<` 且阈值等于该计数器自己的 `max_stacks` 时永不成立**，而 `< 100`、`<= 100`、`<= 1` 均成立；"
    "对一个计数器而言 `<= 1` 与 `< 2` 是同一句话。")
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")
print("ok   content: <= 1 and the signed EXTEND_BUFF")
