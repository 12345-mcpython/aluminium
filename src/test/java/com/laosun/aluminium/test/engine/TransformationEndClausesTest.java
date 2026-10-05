package com.laosun.aluminium.test.engine;

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
 * 1408: the reading of the two "when the transformation ends" sentences (2026-10-02).
 *
 * <p>Both clauses hang off the same moment the reward does -- STATE_ENDED for [变身] -- so this reads them the way the shipped
 * sibling reads the transformation's block: transform, then remove the state and look.
 */
public class TransformationEndClausesTest {
    private static final int OWNER = 1408;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String STATE = "变身";
    private static final String SEEDS = "火种";

    /** "变身结束时，使我方全体速度提高 15%，持续 1 回合" -- and "我方全体" is read on two units, not one. */
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

        // Note: Read AFTER the transformation: its own numbers are then already in, so the delta below is the end clause alone.
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

    /** "变身结束时，获得 3 点[火种]". */
    @Test
    public void theEndGrantsThreeSeeds() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        int before = owner.getResources().value(SEEDS);
        // "战斗开始时，获得 1 点[火种]" -- the other half of the same trace line (1408101).
        Assertions.assertEquals(1, before, "「战斗开始时，获得 1 点【火种】」");
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

    /**
     * "进入战斗或变身结束时，攻击力提高 50%。该效果最多叠加 2 层"-- read by firing the end clause more times than the cap allows.
     *
     * <p>Note: The state is applied directly for these ends: the transformation is only granted by the ultimate, and what this reading
     * is about is the CAP. The seed clause beside it has no cap, so its growth proves the later firings really happened --
     * without that, a flat ATK could just mean "nothing fired".
     */
    @Test
    public void theEndClauseCapsAtTwoLayers() {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        owner.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.StateBuff(STATE, 9, true));
        owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        double atkAfterOneEnd = owner.getAttribute(AttributeType.ATTACK).get();
        int seedsAfterOneEnd = owner.getResources().value(SEEDS);

        owner.getBuffManager().addBuff(new com.laosun.aluminium.models.buff.StateBuff(STATE, 9, true));
        owner.getBuffManager().removeState(STATE);
        battle.processRequests();
        double atkAfterTwoEnds = owner.getAttribute(AttributeType.ATTACK).get();
        int seedsAfterTwoEnds = owner.getResources().value(SEEDS);
        System.out.println("[end-clauses] atk " + atkAfterOneEnd + " -> " + atkAfterTwoEnds
                + " ; seeds " + seedsAfterOneEnd + " -> " + seedsAfterTwoEnds);

        Assertions.assertEquals(seedsAfterOneEnd + 3, seedsAfterTwoEnds,
                "the second end really fired -- the seed clause has no cap");
        Assertions.assertEquals(0.0, atkAfterTwoEnds - atkAfterOneEnd, 1e-9,
                "「最多叠加 2 层」: with the battle-start layer that is already two, so the third is dropped");
    }
}
