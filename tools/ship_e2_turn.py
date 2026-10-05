import io, json, sys
P = "src/main/resources/characters/1217.json"
d = json.load(io.open(P, encoding="utf-8"))
rules = d if isinstance(d, list) else d.get("rules", [])
r = [x for x in rules if x.get("id") == "e2_saves_an_ally_with_the_talisman"][0]
if "EXTEND_BUFF" in [e.get("op") for e in r["do"]]:
    sys.exit("already there")
r["do"].append({"op": "EXTEND_BUFF", "buff": "穹命", "turns": -1, "target": "self"})
r["note"] = (r.get("note", "") +
    " ⭐ 2026-10-02: 补上第三句「使【穹命】的持续回合数**减 1**」⇒ "
    "`EXTEND_BUFF{buff: 穹命, turns: -1, target: self}`。⚠ 形状取自**游戏自己的配置**："
    "`Avatar_Huohuo_00_Rank02_Insert` 对具名 modifier 做 `SetModifierValue{ModifyFunction: \"Add\", ValueType: \"LifeTime\"}`"
    "（另一条同族任务是 `SetDynamicValueByAddValue{AddValue: -1, Min: 0}`）—— 即“对具名状态寿命做**带符号的 Add**”，"
    "所以是**负的 `turns`**，而不新增 op。")
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(d, ensure_ascii=False, indent=2) + "\n")
print("ok   the E2 carries the third sentence (when untouched)")
