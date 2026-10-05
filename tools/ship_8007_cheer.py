"""8007: 【迷迷的声援】-- merged into the skill's own rule, not a second one (2026-10-02).

Document, verbatim (8007_开拓者.html:143):
「使指定我方单体<b>行动提前 100%</b>并附上【<b>迷迷的声援</b>】，持续 <b>3</b> 回合。
持有【迷迷的声援】的目标每造成 1 次伤害，都会再额外造成 1 次等同于原伤害 <b>28%</b> 的真实伤害。」

⚠ WHY THIS EDITS AN EXISTING RULE instead of adding one (measured, not guessed): the first draft appended a second
`SKILL_CAST` rule, the judge went green, and then the FULL suite stayed red on
`RemembranceTrailblazerTest.hisFileCarriesTheClauses()`, whose javadoc reads "Census: the summon (Skill), the summon
(Ultimate) and the level convention" and which counts `SKILL_CAST` / `ULT_CAST` / `BATTLE_START` rules per file. That census
is not a prohibition -- it is a statement of what the file carries, and it points at the right thing: the document's sentence
IS this skill's effect, so the advance and the cheer belong in the rule that already carries the skill. No judge is edited.

⛔ Registered, measured twice, not approximated: the 28% rider. `DAMAGE` states its multiplier in exactly two ways (a skill
row via `skill` + `damage_param`, or a literal ratio -- TriggerInterpreter:432) and neither means "28% of the damage that was
just dealt"; and 「真实伤害」 has no `DamageType` member (NORMAL, ULTRA, BREAK, DOT, TECHNIQUE, ELATION) while the glossary
defines it as effect-proof, element-less, and not counting as an attack (:144).
ASCII only.
"""
import io
import json

DATA = "src/main/resources/characters/8007.json"
JUDGE = "src/test/java/com/laosun/aluminium/test/MimiCheerTest.java"
CHEER = "迷迷的声援"
MARK = "advance_and_cheer"

doc = json.load(io.open(DATA, encoding="utf-8"))
is_dict = isinstance(doc, dict)
rules = doc["rules"] if is_dict else doc

targets = [r for r in rules if isinstance(r, dict) and r.get("on") == "SKILL_CAST"]
if len(targets) != 1:
    raise SystemExit("expected exactly one SKILL_CAST rule, found " + str(len(targets)))

rule = targets[0]
# ⚠ Idempotence by OP, not by an invented key: `EffectKeyDisciplineTest` reddened the first draft with "these effect keys
# are not fields of EffectSpec, so Gson drops them silently: {mark=[8007.json]}" -- an invented key is not a marker, it is a
# silent no-op. Drop any previous copy of these two ops, then append the real ones.
do = [e for e in (rule.get("do") or []) if not (isinstance(e, dict) and (
    e.get("op") == "ADVANCE" or (e.get("op") == "APPLY_BUFF" and e.get("buff") == CHEER)))]
do.append({"op": "ADVANCE", "percent": 1.0, "target": "target"})
do.append({"op": "APPLY_BUFF", "buff": CHEER, "turns": 3, "target": "target"})
rule["do"] = do
rule["source"] = ((rule.get("source") or "") +
                  "\n⭐ 2026-10-02（文档 `:143`）：「使指定我方单体**行动提前 100%**"
                  "并附上【**迷迷的声援**】，持续 **3** 回合」✓")
rule["note"] = ((rule.get("note") or "") +
                "\n⭐ 2026-10-02：把“提前 + 声援”**并进本条** ✓ "
                "—— ❗ 第一版另加了一条 `SKILL_CAST`，判据绿了但**全量红**："
                "`RemembranceTrailblazerTest.hisFileCarriesTheClauses` 是一条**普查**（按事件计数）✓，"
                "而它恰好指向正确做法：**文档那一句就是这个战技的效果** ✓。"
                "⛐ **已登记**：“等同于原伤害 28% 的真实伤害” ✗。")

if is_dict:
    doc["rules"] = rules
    out = doc
else:
    out = rules
json.dump(out, io.open(DATA, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
print("ok   8007.json: the advance and the cheer ride the skill's own rule")

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.skill.SkillExecutor;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 8007：「使指定我方单体行动提前 100% 并附上【迷迷的声援】，持续 3 回合」 (2026-10-02).
 *
 * <p>⭐ FILE-DRIVEN. The cheer lands on the ally the skill was aimed at, which is also the unit the advance moves.
 */
public class MimiCheerTest {
    private static final int OWNER = 8007;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String CHEER = "迷迷的声援";

    /** ⭐ The skill lays the cheer on its target. */
    @Test
    public void theSkillLaysTheCheerOnItsTarget() {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Assertions.assertFalse(ally.getBuffManager().hasState(CHEER), "precondition: no cheer yet");
        Skill skill = owner.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: he has a skill");
        SkillExecutor.execute(battle, skill, owner, List.of(ally));
        battle.processRequests();

        Assertions.assertTrue(ally.getBuffManager().hasState(CHEER),
                "「附上【迷迷的声援】」");
    }
}
''')
print("ok   judge written")
