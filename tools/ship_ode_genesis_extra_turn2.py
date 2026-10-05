"""1415's memosprite skill 10 「献予「创世」之诗」 -- the second half, now that the slot lookup exists (2026-10-02).

The sentence: 「本场战斗中，**开拓者•记忆施放强化普攻后，德谬歌立即获得1个额外回合并自动施放【花与箭的舞曲】**，若施放前目标被消灭则对新入场的敌方目标施放。」

Every piece is shipped and each was read:
  * the trigger is `CAST_SETUP` + `from_skill_id == 4`: 8007's slot 4 is 「Almighty Companion」 with `skill_effect = Enhance`, a buff-shaped skill
    that raises none of SKILL_CAST / ULT_CAST / BASIC_ATTACK. In the data the same job is done by `ByCurrentSkillName` on `OnAfterSkillUse`;
  * 「本场战斗中」 is a mark the ode leaves when it reaches him (the sentence spans the battle, so something has to be true about it);
  * 「立即获得 1 个额外回合」 is `INSERT_ACTION`, NOT `EXTRA_TURN`: the game pins a memosprite's speed to 0 with `SpeedOverride`, so it has no place
    in the action order at all;
  * 「自动施放【花与箭的舞曲】」 names the memosprite's OWN SLOT 1 (`11415/1`, `Minuet of Blooms and Plumes`) through `skill_id` -- the lookup
    that had to be taught that a memosprite keys its skills by data slot.
"""
import io
import json
import sys

CHARS = "src/main/resources/characters/8007.json"
SKILLS = "src/main/resources/data/skills.json"
SLOT = 13
REINFORCED = 4
MINUET = 1

table = json.load(io.open(SKILLS, encoding="utf-8"))
ode = table["11415"][str(SLOT)]["name"]["chinese"]
minuet = table["11415"][str(MINUET)]["name"]["chinese"]

doc = json.load(io.open(CHARS, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
by_id = {r.get("id"): r for r in rules}
mark_rule = by_id.get("memosprite_ode_of_genesis_raises_his_attack_and_crit")
if mark_rule is None:
    sys.exit("REFUSING: the first-half rule is not there")
if not any(e.get("op") == "APPLY_BUFF" for e in mark_rule["do"]):
    mark_rule["do"].append({"op": "APPLY_BUFF", "buff": ode, "permanent": True, "target": "self"})

RULE_ID = "memosprite_ode_of_genesis_gives_it_an_extra_turn_after_the_reinforced_basic_attack"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)
rules.append({
    "id": RULE_ID,
    "on": "CAST_SETUP",
    "when": ["self has_state " + ode, "actor == self", "from_skill_id == " + str(REINFORCED), "self_summon_count >= 1"],
    "do": [
        {"op": "INSERT_ACTION", "target": "summon"},
        {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": MINUET, "turns": 1, "target": "summon"},
        {"op": "CAST_SKILL", "skill": "SKILL", "skill_id": MINUET, "target": "summon"},
    ],
    "source": ("1415 昔涟 忆灵技能 10 「" + ode + "」（数据槽位 13）："
               "「本场战斗中，**开拓者•记忆施放强化普攻后，德谬歌立即获得 1 个额外回合"
               "并自动施放【" + minuet + "】」。"),
    "note": ("⭐ 三个效果各自对应原句的一部分：「立即获得 1 个额外回合」⇒ `INSERT_ACTION`（"
             "**不是** `EXTRA_TURN`：游戏用 `SpeedOverride = 0` 把忆灵的速度钉在 0，它在行动顺序里**没有位置**）；"
             "「自动施放【" + minuet + "】」⇒ `skill_id = " + str(MINUET) + "`（忆灵自己的**数据槽位**）。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   8007 now carries the extra-turn rule (%d rules total)" % len(rules))
