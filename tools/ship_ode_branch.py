import io, json, re, sys

# ---- 1) copy the two names out of the file (never typed by hand: that mistake has cost three rounds) ----
raw = io.open("src/main/resources/characters/1404.json", encoding="utf-8").read()
bloodfeud = sorted(set(re.findall(r'"self has_state ([^"]+)"', raw)))
charge = sorted(set(re.findall(r'"resource": "([^"]+)"', raw)) | set(re.findall(r'self_resource:([^ "]+)', raw)))
print("names in 1404.json: bloodfeud=%s charge=%s" % (bloodfeud, charge))
if len(bloodfeud) != 1 or len(charge) != 1:
    sys.exit("REFUSING: ambiguous names")
bf, ch = bloodfeud[0], charge[0]

J = "src/test/java/com/laosun/aluminium/test/OdeToStrifeCommandsTest.java"
j = io.open(J, encoding="utf-8").read()
j = j.replace("@BLOODFEUD@", bf)
j = j.replace('"\\u5929\\u8d4b\\u5145\\u80fd"', '"%s"' % ch)
j = j.replace("mydei.getBuffManager().stacksOf", "battle.partyResourceValue")
io.open(J, "w", encoding="utf-8", newline="\n").write(j)
print("ok   the judge uses the file's own names, and reads the resource through Battle")

# ---- 2) the clause's first branch, on 万敌's table, shaped like his own hundred-and-fifty rule ----
P = "src/main/resources/characters/1404.json"
d = json.load(io.open(P, encoding="utf-8"))
rules = d if isinstance(d, list) else d.get("rules", [])
if any(r.get("id") == "memosprite_ode_commands_the_godslayer_in_bloodfeud" for r in rules):
    sys.exit("already there")
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
               "（形状照拄 1404 自己的 `bloodfeud_godslayer_at_a_hundred_and_fifty`：同一对 `REPLACE_SKILL` 加 `CAST_SKILL`，"
               "差别只在那一条多出的 `SPEND_RESOURCE`——正是「不消耗充能」这四个字。）"),
    "note": ("⚠ 触发点在**万敌自己的表**上，用 `target == self`（已出货的 20 条同形式，如 1408 的天赋）"
             "—— 它是一句“有人把技能对准我”的规则。★ **新增了一个谓词 `is_summon`**："
             "`from_skill_id` 读的是**槽位**（不是数据行 id），单独用会让**任何其他单位的 16 号技能**打中他时也误触发（包括敌人）；"
             "`actor is_summon` 才能把“施放者是忆灵”说出来。"),
})
if isinstance(d, list):
    doc = rules
else:
    d["rules"] = rules
    doc = d
io.open(P, "w", encoding="utf-8", newline="\n").write(json.dumps(doc, ensure_ascii=False, indent=2) + "\n")
print("ok   the clause's bloodfeud branch is on 1404's table")
