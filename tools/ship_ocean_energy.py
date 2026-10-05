"""1415's memosprite skill 22 「献予「海洋」之诗」 -- the mark and the energy it pays (2026-10-02).

Verbatim (1141522, params [0.6, 0.3, 0.4, 60]): 「单次生效，对海瑟音施放时，使海瑟音获得【暖流】。<b>海瑟音施放攻击后消耗【暖流】为自身恢复 #4 点能量。</b>本场战斗中，海瑟音造成的伤害提高 #1%，
施放普攻/战技攻击敌方目标后，使受到攻击的敌方目标当前承受的所有持续伤害立即产生相当于原伤害 #2%/#3% 的伤害。」

The two sentences written here are the pair 1414's ode of romance already ships for 阿格莱雅, shape for shape:
  * `APPLY_BUFF` a named mark when the ode is cast at the character -- gated on `target == self`, `actor is_summon`, `from_skill_id == 22`;
  * `ALLY_ATTACK` + `self has_state` the mark -> `GAIN_ENERGY{60}` + `REMOVE_STATE`.

Measured:
  * #4 runs 60 at EVERY level of 1141522 (all ten rows), so a literal 60 is what the data says;
  * 海瑟音 is cid 1410 in our own character data ("Hysilens"), and `characters/1410.json` did not exist -- the file is created here.

\u26d4 Registered rather than written from the same sentence: the damage boost (「造成的伤害提高 #1%」) and the DOT-immediate clause (「使…所有持续伤害立即产生相当于原伤害 #2%/#3% 的伤害」).
"""
import io
import json
import os
import sys

TB = "E:/turnbasedgamedata"
HYSILENS = "src/main/resources/characters/1410.json"
SE = "src/main/resources/data/skill_effects.json"
SLOT = 22
MARK = "\u6696\u6d41"          # 暖流 -- the game's own word for the state

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
        "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 16 \u300c\u732e\u4e88\u300c\u6d77\u6d0b\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 22\uff0cSkillID 1141522\uff09\uff1a"
                   "\u300c\u5355\u6b21\u751f\u6548\uff0c\u5bf9\u6d77\u745f\u97f3\u65bd\u653e\u65f6\uff0c\u4f7f\u6d77\u745f\u97f3\u83b7\u5f97\u3010" + MARK + "\u3011\u3002\u300d"),
        "note": "\u2b50 \u5f62\u72b6\u7167 1402 \u7684 `memosprite_ode_of_romance_marks_aglaea`\uff08\u540c\u4e00\u53e5\u8bdd\u7684\u4e24\u4e2a\u5b9e\u4f8b\uff09\u3002",
    },
    {
        "id": "memosprite_ode_of_ocean_spends_itself_for_energy",
        "on": "ALLY_ATTACK",
        "when": ["self has_state " + MARK],
        "do": [
            {"op": "GAIN_ENERGY", "amount": 60, "target": "self"},
            {"op": "REMOVE_STATE", "buff": MARK},
        ],
        "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 16 \u300c\u732e\u4e88\u300c\u6d77\u6d0b\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 22\uff09\uff1a"
                   "\u300c**\u6d77\u745f\u97f3\u65bd\u653e\u653b\u51fb\u540e\u6d88\u8017\u3010" + MARK + "\u3011\u4e3a\u81ea\u8eab\u6062\u590d #4 \u70b9\u80fd\u91cf**\u3002\u300d"),
        "note": ("\u2b50 \u5f62\u72b6\u7167 1402 \u7684 `memosprite_ode_of_romance_spends_itself_for_energy`\u3002"
                 "\u2b50 `#4` \u5728 1141522 \u7684**\u5341\u884c\u91cc\u5168\u662f 60**\uff08**\u5b9e\u6d4b**\uff09\uff0c\u6240\u4ee5\u5199 60 \u662f\u6570\u636e\u8bf4\u7684\u8bdd\u3002"),
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
        "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 16 \u300c\u732e\u4e88\u300c\u6d77\u6d0b\u300d\u4e4b\u8bd7\u300d\uff08\u6570\u636e\u69fd\u4f4d 22\uff09\uff1a"
                   "\u5de5\u4f5c\u5728**\u89c4\u5219\u4fa7**\uff08\u5370\u8bb0\u4e0e\u80fd\u91cf\u5728 `characters/1410.json`\uff09\uff0c\u6240\u4ee5\u662f `Rules` \u5f62\u72b6\u3002"),
        "note": "\u2b50 \u6ca1\u6709\u6761\u76ee\u5c31\u4e0d\u53ef\u4ea4\u4ed8\u3002",
    }
    io.open(SE, "w", encoding="utf-8", newline="\n").write(json.dumps(effects, ensure_ascii=False, indent=2) + "\n")
    print("ok   skill_effects.json: 11415/%d = Rules" % SLOT)
