import io, json, sys
P = "src/main/resources/characters/1404.json"
d = json.load(io.open(P, encoding="utf-8"))
rules = d if isinstance(d, list) else d.get("rules", [])
bf = [w[len("self has_state "):] for r in rules if r.get("id") == "bloodfeud_godslayer_at_a_hundred_and_fifty"
      for w in r.get("when", []) if isinstance(w, str) and w.startswith("self has_state ")]
if len(bf) != 1:
    sys.exit("REFUSING: ambiguous state name")
bf = bf[0]

J = "src/test/java/com/laosun/aluminium/test/OdeToStrifeAdvanceTest.java"
j = io.open(J, encoding="utf-8").read().replace("@BLOODFEUD@", bf)
io.open(J, "w", encoding="utf-8", newline="\n").write(j)
print("ok   the judge uses the file's own state name")

if not any(r.get("id") == "memosprite_ode_advances_him_outside_bloodfeud" for r in rules):
    rules.append({
        "id": "memosprite_ode_advances_him_outside_bloodfeud",
        "on": "CAST_SETUP",
        "when": ["target == self", "actor is_summon", "from_skill_id == 16", "!self has_state " + bf],
        "do": [{"op": "ADVANCE", "percent": 1.0, "target": "self"}],
        "source": ("1415 昔涟 忆灵技能 8 「献予「纷争」之诗」（数据槽位 16，SkillID 1141516）："
                   "「若万敌**不**处于【血仇】状态，则使万敌行动提前 `#2[i]%`」。"
                   "（`#2` 各级恒为 1，即 **100%**；形状照 1101／1212 的 `ADVANCE{percent: 1.0}`。）"),
        "note": ("★ 否定用的是**现成的** `!`：`HasState` 本就实现了 `PartyCondition`，所以 `!self has_state <state>` "
                 "是一个普通的否定，不需要新词汇。（上一轮我据注释推断说写不出来，而类声明才是定论。）"),
    })
if isinstance(d, list):
    doc = rules
else:
    d["rules"] = rules
    doc = d
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
print("ok   the advance branch is on 1404's table")
