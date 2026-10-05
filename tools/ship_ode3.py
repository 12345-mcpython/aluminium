import io, json, sys

# the state name comes out of the TWIN RULE, read element by element (joining first is how it went wrong before)
P = "src/main/resources/characters/1404.json"
d = json.load(io.open(P, encoding="utf-8"))
rules = d if isinstance(d, list) else d.get("rules", [])
twin = [r for r in rules if r.get("id") == "bloodfeud_godslayer_at_a_hundred_and_fifty"]
if len(twin) != 1:
    sys.exit("REFUSING: the twin rule is not unique")
names = [w[len("self has_state "):] for w in twin[0].get("when", []) if isinstance(w, str) and w.startswith("self has_state ")]
print("state names in the twin rule: %s" % names)
if len(names) != 1:
    sys.exit("REFUSING: ambiguous")
bf = names[0]

J = "src/test/java/com/laosun/aluminium/test/OdeToStrifeBloodfeudTest.java"
j = io.open(J, encoding="utf-8").read().replace("@BLOODFEUD@", bf)
io.open(J, "w", encoding="utf-8", newline="\n").write(j)
print("ok   the judge uses the twin rule's own state name")

if not any(r.get("id") == "memosprite_ode_commands_the_godslayer_in_bloodfeud" for r in rules):
    rules.append({
        "id": "memosprite_ode_commands_the_godslayer_in_bloodfeud",
        "on": "SKILL_CAST",
        "when": ["target == self", "actor is_summon", "from_skill_id == 16", "self has_state " + bf],
        "do": [
            {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": 11, "turns": 1, "target": "self"},
            {"op": "CAST_SKILL", "skill": "SKILL", "target": "self"},
        ],
        "source": ("1415 昔涟 忆灵技能 8 「献予「纷争」之诗」（数据槽位 16，SkillID 1141516）："
                   "「若万敌处于【血仇】状态，则使其自动施放 1 次**不消耗充能**的【弑神登神】」。"
                   "（形状照拄同文件的 `bloodfeud_godslayer_at_a_hundred_and_fifty`：同一对 `REPLACE_SKILL` 加 `CAST_SKILL`，"
                   "差别只在那条多出的 `SPEND_RESOURCE`——正是「不消耗充能」这四个字。）"),
        "note": ("⚠ 触发点在**万敌自己的表**上，用 `target == self`（已出货的 20 条同形式）。"
                 "★ **新增谓词 `is_summon`**：`from_skill_id` 读的是**槽位**（不是数据行 id），"
                 "单独用会让**任何其他单位的 16 号技能**打中他时也误触发；`actor is_summon` 才能把“施放者是忆灵”说出来。"),
    })
if isinstance(d, list):
    doc = rules
else:
    d["rules"] = rules
    doc = d
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
print("ok   the bloodfeud branch is on 1404's table")
