"""1415's memosprite skill 10 「献予「创世」之诗」 -- the second half, written from the sentence (2026-10-02).

The sentence, verbatim: 「本场战斗中，<b>开拓者•记忆施放强化普攻后，德谬歌立即获得1个额外回合并自动施放【花与箭的舞曲】</b>，若施放前目标被消灭则对新入场的
敌方目标施放。」

What tbgd says the shape is (`GlobalModifiers`, `MServant_CyreneServant_00_AmazingBuff_Player`):

    "Event": "OnAfterSkillUse"
    PredicateTaskList -> ByCurrentSkillName        <- WHICH skill was just used: that is how 强化普攻 is told apart
    TurnInsertAction{AutoCast}                     <- the memosprite acts, automatically
and `_M_Cyrene_Player_InsertActionCheck` carries `Retarget` + `ByHaveEnemyAlive` + `ByIsTargetValid` -- the retarget half.

Every piece here is a shipped capability, and each was read rather than assumed:
  * the trigger is `CAST_SETUP` with `from_skill_id == 4`, NOT the `BASIC_ATTACK` event: 8007's slot 4 is `skill_effect = Enhance`, a
    buff-shaped skill, and a memosprite's BUFF skill raises none of SKILL_CAST / ULT_CAST / BASIC_ATTACK (measured earlier this session). The
    `from_skill_id` condition is what names the skill, which is the job `ByCurrentSkillName` does in the data;
  * `EXTRA_TURN{target: "summon"}` -- the op exists and reads its target as "who gets the extra turn" (1309 ships `"target": "self"`);
  * 【花与箭的舞曲】 is the memosprite's own slot 1 (`11415/1`, `Minuet of Blooms and Plumes`), reached with the shipped
    `REPLACE_SKILL` + `CAST_SKILL` pair the godslayer clause already uses.
"""
import io
import json
import sys

CHARS = "src/main/resources/characters/8007.json"
SKILLS = "src/main/resources/data/skills.json"
SLOT = 13          # the ode: SkillID 1141513
REINFORCED = 4     # 8007/4 "Almighty Companion", skill_effect Enhance
SERVANT_SKILL_SLOT = 1   # 11415/1 "Minuet of Blooms and Plumes" -- 【花与箭的舞曲】

table = json.load(io.open(SKILLS, encoding="utf-8"))
ode = table["11415"][str(SLOT)]["name"]["chinese"]
memosprite_skill = table["11415"][str(SERVANT_SKILL_SLOT)]["name"]["chinese"]
print("the ode %r ; the skill it commands %r" % (ode, memosprite_skill))

doc = json.load(io.open(CHARS, encoding="utf-8"))
rules = doc if isinstance(doc, list) else doc.get("rules", [])
RULE_ID = "memosprite_ode_of_genesis_gives_it_an_extra_turn_after_the_reinforced_basic_attack"
if any(r.get("id") == RULE_ID for r in rules):
    sys.exit("REFUSING: %s is already there" % RULE_ID)

rules.append({
    "id": RULE_ID,
    "on": "CAST_SETUP",
    # ⚠ `self_summon_count >= 1` is the gate the engine itself names when `target: "summon"` has nothing to aim at
    "when": ["actor == self", "from_skill_id == " + str(REINFORCED), "self_summon_count >= 1"],
    "do": [
        {"op": "EXTRA_TURN", "target": "summon"},
        {"op": "REPLACE_SKILL", "skill": "SKILL", "skill_id": SERVANT_SKILL_SLOT, "turns": 1, "target": "summon"},
        {"op": "CAST_SKILL", "skill": "SKILL", "target": "summon"},
    ],
    "source": ("1415 \u6614\u6d9f \u5fc6\u7075\u6280\u80fd 10 \u300c" + ode + "\u300d\uff08\u6570\u636e\u69fd\u4f4d 13\uff09\uff1a"
               "\u300c\u672c\u573a\u6218\u6597\u4e2d\uff0c**\u5f00\u62d3\u8005\u2022\u8bb0\u5fc6\u65bd\u653e\u5f3a\u5316\u666e\u653b\u540e\uff0c\u5fb7\u8c2c\u6b4c\u7acb\u5373\u83b7\u5f97 1 \u4e2a\u989d\u5916\u56de\u5408"
               "\u5e76\u81ea\u52a8\u65bd\u653e\u3010" + memosprite_skill + "\u3011\u300d\u3002"),
    "note": ("\u2b50 \u89e6\u53d1\u7528 `CAST_SETUP` + `from_skill_id == 4`\uff0c\u800c\u4e0d\u662f `BASIC_ATTACK` \u4e8b\u4ef6\uff1a"
             "8007 \u69fd\u4f4d 4 \u7684 `skill_effect` \u662f `Enhance`\uff0c\u5c5e\u4e8e**buff \u5f62\u6001**\u7684\u6280\u80fd\uff0c"
             "\u800c\u8fd9\u7c7b\u6280\u80fd**\u4e09\u4e2a\u4e8b\u4ef6\u90fd\u4e0d\u4f1a\u53d1**\uff08\u672c\u4f1a\u8bdd\u65e9\u5148\u5df2\u91cf\uff09\uff1b"
             "\u6570\u636e\u91cc\u505a\u8fd9\u4ef6\u4e8b\u7684\u662f `ByCurrentSkillName`\uff0c\u800c\u5728\u6211\u4eec\u8fd9\u91cc\u5c31\u662f `from_skill_id`\u3002"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   %s now carries %s (%d rules)" % (CHARS, RULE_ID, len(rules)))
