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
CHEER = "\u8ff7\u8ff7\u7684\u58f0\u63f4"
MARK = "advance_and_cheer"

doc = json.load(io.open(DATA, encoding="utf-8"))
is_dict = isinstance(doc, dict)
rules = doc["rules"] if is_dict else doc

targets = [r for r in rules if isinstance(r, dict) and r.get("on") == "SKILL_CAST"]
if len(targets) != 1:
    raise SystemExit("expected exactly one SKILL_CAST rule, found " + str(len(targets)))

rule = targets[0]
do = [e for e in (rule.get("do") or [])
      if not (isinstance(e, dict) and e.get("mark") == MARK)]
do.append({"op": "ADVANCE", "percent": 1.0, "target": "target", "mark": MARK})
do.append({"op": "APPLY_BUFF", "buff": CHEER, "turns": 3, "target": "target", "mark": MARK})
rule["do"] = do
rule["source"] = ((rule.get("source") or "") +
                  "\n\u2b50 2026-10-02\uff08\u6587\u6863 `:143`\uff09\uff1a\u300c\u4f7f\u6307\u5b9a\u6211\u65b9\u5355\u4f53**\u884c\u52a8\u63d0\u524d 100%**"
                  "\u5e76\u9644\u4e0a\u3010**\u8ff7\u8ff7\u7684\u58f0\u63f4**\u3011\uff0c\u6301\u7eed **3** \u56de\u5408\u300d\u2713")
rule["note"] = ((rule.get("note") or "") +
                "\n\u2b50 2026-10-02\uff1a\u628a\u201c\u63d0\u524d + \u58f0\u63f4\u201d**\u5e76\u8fdb\u672c\u6761** \u2713 "
                "\u2014\u2014 \u2757 \u7b2c\u4e00\u7248\u53e6\u52a0\u4e86\u4e00\u6761 `SKILL_CAST`\uff0c\u5224\u636e\u7eff\u4e86\u4f46**\u5168\u91cf\u7ea2**\uff1a"
                "`RemembranceTrailblazerTest.hisFileCarriesTheClauses` \u662f\u4e00\u6761**\u666e\u67e5**\uff08\u6309\u4e8b\u4ef6\u8ba1\u6570\uff09\u2713\uff0c"
                "\u800c\u5b83\u6070\u597d\u6307\u5411\u6b63\u786e\u505a\u6cd5\uff1a**\u6587\u6863\u90a3\u4e00\u53e5\u5c31\u662f\u8fd9\u4e2a\u6218\u6280\u7684\u6548\u679c** \u2713\u3002"
                "\u26d0 **\u5df2\u767b\u8bb0**\uff1a\u201c\u7b49\u540c\u4e8e\u539f\u4f24\u5bb3 28% \u7684\u771f\u5b9e\u4f24\u5bb3\u201d \u2717\u3002")

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
 * 8007\uff1a\u300c\u4f7f\u6307\u5b9a\u6211\u65b9\u5355\u4f53\u884c\u52a8\u63d0\u524d 100% \u5e76\u9644\u4e0a\u3010\u8ff7\u8ff7\u7684\u58f0\u63f4\u3011\uff0c\u6301\u7eed 3 \u56de\u5408\u300d (2026-10-02).
 *
 * <p>\u2b50 FILE-DRIVEN. The cheer lands on the ally the skill was aimed at, which is also the unit the advance moves.
 */
public class MimiCheerTest {
    private static final int OWNER = 8007;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String CHEER = "\u8ff7\u8ff7\u7684\u58f0\u63f4";

    /** \u2b50 The skill lays the cheer on its target. */
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
                "\u300c\u9644\u4e0a\u3010\u8ff7\u8ff7\u7684\u58f0\u63f4\u3011\u300d");
    }
}
''')
print("ok   judge written")
