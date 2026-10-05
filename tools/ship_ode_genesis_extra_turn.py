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
    "source": ("1415 昔涟 忆灵技能 10 「" + ode + "」（数据槽位 13）："
               "「本场战斗中，**开拓者•记忆施放强化普攻后，德谬歌立即获得 1 个额外回合"
               "并自动施放【" + memosprite_skill + "】」。"),
    "note": ("⭐ 触发用 `CAST_SETUP` + `from_skill_id == 4`，而不是 `BASIC_ATTACK` 事件："
             "8007 槽位 4 的 `skill_effect` 是 `Enhance`，属于**buff 形态**的技能，"
             "而这类技能**三个事件都不会发**（本会话早先已量）；"
             "数据里做这件事的是 `ByCurrentSkillName`，而在我们这里就是 `from_skill_id`。"),
})

if isinstance(doc, list):
    out = rules
else:
    doc["rules"] = rules
    out = doc
io.open(CHARS, "w", encoding="utf-8", newline="\n").write(json.dumps(out, ensure_ascii=False, indent=2) + "\n")
print("ok   %s now carries %s (%d rules)" % (CHARS, RULE_ID, len(rules)))
