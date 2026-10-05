"""1415's memosprite skill 22 「献予「海洋」之诗」 -- the mark and the energy it pays (2026-10-02).

Verbatim (1141522, params [0.6, 0.3, 0.4, 60]): 「单次生效，对海瑟音施放时，使海瑟音获得【暖流】。<b>海瑟音施放攻击后消耗【暖流】为自身恢复 #4 点能量。</b>本场战斗中，海瑟音造成的伤害提高 #1%，
施放普攻/战技攻击敌方目标后，使受到攻击的敌方目标当前承受的所有持续伤害立即产生相当于原伤害 #2%/#3% 的伤害。」

The two sentences written here are the pair 1414's ode of romance already ships for 阿格莱雅, shape for shape:
  * `APPLY_BUFF` a named mark when the ode is cast at the character -- gated on `target == self`, `actor is_summon`, `from_skill_id == 22`;
  * `ALLY_ATTACK` + `self has_state` the mark -> `GAIN_ENERGY{60}` + `REMOVE_STATE`.

Measured:
  * #4 runs 60 at EVERY level of 1141522 (all ten rows), so a literal 60 is what the data says;
  * 海瑟音 is cid 1410 in our own character data ("Hysilens"), and `characters/1410.json` did not exist -- the file is created here.

⛔ Registered rather than written from the same sentence: the damage boost (「造成的伤害提高 #1%」) and the DOT-immediate clause (「使…所有持续伤害立即产生相当于原伤害 #2%/#3% 的伤害」).
"""
import io
import json
import os
import sys

TB = "E:/turnbasedgamedata"
HYSILENS = "src/main/resources/characters/1410.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 22
MARK = "暖流"          # 暖流 -- the game's own word for the state

rows = json.load(io.open(TB + "/ExcelOutput/AvatarServantSkillConfig.json", encoding="utf-8"))
levels = [r for r in rows if r["SkillID"] == 1141522]
if not levels:
    sys.exit("REFUSING: no 1141522 rows")
energies = {p.get("Value") for r in levels for i, p in enumerate(r.get("ParamList") or []) if i == 3}
if energies != {60}:
    sys.exit("REFUSING: #4 is %s, not 60 at every level -- a literal would be an approximation" % energies)
print("ok   #4 is 60 at all %d levels" % len(levels))

# mirror the shape of an existing character file rather than guessing it
sample = json.load(io.open("src/main/resources/characters/1402.json", encoding="utf-8"))
container = "rules" if isinstance(sample, dict) else None
print("ok   an existing character file is %s" % ("a dict with a 'rules' key" if container else "a list"))

if os.path.exists(HYSILENS):
    sys.exit("REFUSING: %s already exists -- read it before writing" % HYSILENS)

rules = [
    {
        "id": "memosprite_ode_of_ocean_marks_hysilens",
        "on": "CAST_SETUP",
        "when": ["target == self", "actor is_summon", "from_skill_id == " + str(SLOT)],
        "do": [{"op": "APPLY_BUFF", "buff": MARK, "permanent": True, "target": "self"}],
        "source": ("1415 昔涟 忆灵技能 16 「献予「海洋」之诗」（数据槽位 22，SkillID 1141522）："
                   "「单次生效，对海瑟音施放时，使海瑟音获得【" + MARK + "】。」"),
        "note": "⭐ 形状照 1402 的 `memosprite_ode_of_romance_marks_aglaea`（同一句话的两个实例）。",
    },
    {
        "id": "memosprite_ode_of_ocean_spends_itself_for_energy",
        "on": "ALLY_ATTACK",
        "when": ["self has_state " + MARK],
        "do": [
            {"op": "GAIN_ENERGY", "amount": 60, "target": "self"},
            {"op": "REMOVE_STATE", "buff": MARK},
        ],
        "source": ("1415 昔涟 忆灵技能 16 「献予「海洋」之诗」（数据槽位 22）："
                   "「**海瑟音施放攻击后消耗【" + MARK + "】为自身恢复 #4 点能量**。」"),
        "note": ("⭐ 形状照 1402 的 `memosprite_ode_of_romance_spends_itself_for_energy`。"
                 "⭐ `#4` 在 1141522 的**十行里全是 60**（**实测**），所以写 60 是数据说的话。"),
    },
]
io.open(HYSILENS, "w", encoding="utf-8", newline="\n").write(
    json.dumps({"rules": rules} if container else rules, ensure_ascii=False, indent=2) + "\n")
print("ok   created %s with %d rules" % (HYSILENS, len(rules)))

effects = json.load(io.open(SE, encoding="utf-8"))
if str(SLOT) in effects.get("11415", {}):
    print("ok   skill_effects.json already has 11415/%d" % SLOT)
else:
    effects.setdefault("11415", {})[str(SLOT)] = {
        "effect": "Rules",
        "source": ("1415 昔涟 忆灵技能 16 「献予「海洋」之诗」（数据槽位 22）："
                   "工作在**规则侧**（印记与能量在 `characters/1410.json`），所以是 `Rules` 形状。"),
        "note": "⭐ 没有条目就不可交付。",
    }
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)
