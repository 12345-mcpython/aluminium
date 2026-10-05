"""Part 1b: the two 1408 clauses that lacked their own readings (round 1693).

Written in the pattern of the shipped sibling (`TransformationScopedStatsTest`): drive the ultimate through
`SkillExecutor.execute`, then `removeState("变身")` to reach the end moment -- the explicit-removal path, which is the one that
announces STATE_ENDED once with the total.

  * talent 「变身结束时，使我方全体速度提高 15%，持续 1 回合」 -- read on TWO units, because 「我方全体」 is the claim;
  * trace 行向世界终点 「变身结束时，获得 3 点【火种】」 -- measured: 火种 is declared on 1408 herself (max 12), not PARTY.
"""
import io

JUDGE = "src/test/java/com/laosun/aluminium/test/TransformationEndClausesTest.java"

io.open(JUDGE, "w", encoding="utf-8", newline="").write('''package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
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
 * 1408:「变身结束时」那两句的读数 (2026-10-02).
 *
 * <p>Both clauses hang off the same moment the reward does -- STATE_ENDED for 【变身】 -- so this reads them the way the shipped
 * sibling reads the transformation's block: transform, then remove the state and look.
 */
public class TransformationEndClausesTest {
    private static final int OWNER = 1408;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "\\u53d8\\u8eab";
    private static final String SEEDS = "\\u706b\\u79cd";

    /** 「变身结束时，使我方全体速度提高 15%，持续 1 回合」 -- and 「我方全体」 is read on two units, not one. */
    @Test
    public void theEndSpeedsTheWholeParty() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character ally = CharacterFactory.create(ALLY, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "precondition: the transformation is on");

        // ⚠ Read AFTER the transformation: its own numbers are then already in, so the delta below is the end clause alone.
        double ownerBefore = owner.getAttribute(AttributeType.SPEED).get();
        double allyBefore = ally.getAttribute(AttributeType.SPEED).get();
        double ownerBase = owner.getAttribute(AttributeType.SPEED).baseValue();
        double allyBase = ally.getAttribute(AttributeType.SPEED).baseValue();

        owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        double ownerAfter = owner.getAttribute(AttributeType.SPEED).get();
        double allyAfter = ally.getAttribute(AttributeType.SPEED).get();
        System.out.println("[end-clauses] owner " + ownerBefore + " -> " + ownerAfter + " (base " + ownerBase + ")"
                + " ; ally " + allyBefore + " -> " + allyAfter + " (base " + allyBase + ")");

        Assertions.assertEquals(ownerBase * 0.15, ownerAfter - ownerBefore, ownerBase * 1e-6,
                "「变身结束时…速度提高 15%」-- on her");
        Assertions.assertEquals(allyBase * 0.15, allyAfter - allyBefore, allyBase * 1e-6,
                "「我方全体」-- and on the ally, which is what 全体 means");
    }

    /** 「变身结束时，获得 3 点【火种】」. */
    @Test
    public void theEndGrantsThreeSeeds() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        int before = owner.getResources().value(SEEDS);
        Skill ult = owner.getSkills().get(SkillType.ULTRA);
        SkillExecutor.execute(battle, ult, owner, List.of(owner));
        battle.processRequests();
        Assertions.assertTrue(owner.getBuffManager().hasState(STATE), "precondition: the transformation is on");
        int duringTransformation = owner.getResources().value(SEEDS);

        owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        int after = owner.getResources().value(SEEDS);
        System.out.println("[end-clauses] seeds " + before + " -> " + duringTransformation + " -> " + after);

        Assertions.assertEquals(duringTransformation + 3, after,
                "「变身结束时，获得 3 点【火种】」");
    }
}
''')
print("ok   the two readings are written")
