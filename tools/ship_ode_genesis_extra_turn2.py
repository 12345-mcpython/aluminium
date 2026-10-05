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
    "when": ["self has_state " + ode, "actor == self", "from_skill_id " + str(REINFORCED), "self_summon_count >= 1"],
    "do": [
        {"op": "INSERT_ACTION", "target": "summon"},
        {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": MINUET, "turns": 1, "target": "summon"},
        {"op": "CAST_SKILL", "skill": "SKILL", "skill_id": MINUET, "target": "summon"},
    ],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 10 \u300c" + ode + "\u300d\uff08\u6570\u636e\u69fd\u4f4d 13\uff09\uff1a"
               "\u300c\u672c\u573a\u6218\u6597\u4e2d\uff0c**\u5f00\u62d3\u8005\u2022\u8bb0\u5fc6\u65bd\u653e\u5f3a\u5316\u666e\u653b\u540e\uff0c\u5fb7\u8c2c\u6b4c\u7acb\u5373\u83b7\u5f97 1 \u4e2a\u989d\u5916\u56de\u5408"
               "\u5e76\u81ea\u52a8\u65bd\u653e\u3010" + minuet + "\u3011\u300d\u3002"),
    "note": ("\u2b50 \u4e09\u4e2a\u6548\u679c\u5404\u81ea\u5bf9\u5e94\u539f\u53e5\u7684\u4e00\u90e8\u5206\uff1a\u300c\u7acb\u5373\u83b7\u5f97 1 \u4e2a\u989d\u5916\u56de\u5408\u300d\u21d2 `INSERT_ACTION`\uff08"
             "**\u4e0d\u662f** `EXTRA_TURN`\uff1a\u6e38\u620f\u7528 `SpeedOverride = 0` \u628a\u5fc6\u7075\u7684\u901f\u5ea6\u9489\u5728 0\uff0c\u5b83\u5728\u884c\u52a8\u987a\u5e8f\u91cc**\u6ca1\u6709\u4f4d\u7f6e**\uff09\uff1b"
             "\u300c\u81ea\u52a8\u65bd\u653e\u3010" + minuet + "\u3011\u300d\u21d2 `skill_id = " + str(MINUET) + "`\uff08\u5fc6\u7075\u81ea\u5df1\u7684**\u6570\u636e\u69fd\u4f4d**\uff09\u3002"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   8007 now carries the extra-turn rule (%d rules total)" % len(rules))
