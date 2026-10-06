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
 * 1408: "when Phainon (白厄) becomes the skill target of any other target, he gains 1 point of [火种] (Coreflame). If the caster is a teammate of Phainon, it also increases Phainon's
 * crit damage by 30%, lasting 3 turns".
 *
 * <p>SAME SCENE, ONE VARIABLE: 1414 -- whose skill is a shield aimed at a teammate -- casts it either AT 1408 or AT THE ENEMY. The
 * only difference is who was aimed at, which is exactly what the sentence is about.
 */
public class SkillTargetCoreflameTest {
    private static final double EPS = 1e-9;
    private static final int OWNER = 1408;
    private static final int SUPPORT = 1414;
    private static final int MONSTER = 1002011;
    private static final String COREFLAME = "火种";

    /** Aimed at: she gains a point of Coreflame and 30% crit damage. */
    @Test
    public void beingTargetedGrantsCoreflameAndCritDamage() {
        double[] aimedAtHer = scene(true);
        Assertions.assertEquals(1.0, aimedAtHer[0], EPS, "「gains 1 point of [火种] (Kindling)」");
        Assertions.assertEquals(0.30, aimedAtHer[1], EPS, "「crit damage increased by 30%」");
    }

    /** Note: Aimed at an enemy instead: the sentence has not started. */
    @Test
    public void aimingElsewhereChangesNothing() {
        double[] aimedAway = scene(false);
        Assertions.assertEquals(0.0, aimedAway[0], EPS, "「when becoming ... a skill target」 -- she was not the target");
        Assertions.assertEquals(0.0, aimedAway[1], EPS, "…so there is no crit damage either");
    }

    // ==================================================================

    /** returns { Coreflame gained, crit damage gained }. */
    private static double[] scene(boolean aimAtHer) {
        Character owner = CharacterFactory.create(OWNER, 80, false, null, null, 0);
        Character support = CharacterFactory.create(SUPPORT, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner, support),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        double coreBefore = owner.getResources().value(COREFLAME);
        double critBefore = owner.getAttribute(AttributeType.CRIT_ATTACK).get();

        Skill skill = support.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: the teammate has a skill");
        // Note: Two branches, not a ternary expression: one is a `Character` and the other a `CanHit`, and there is no common type to declare.
        if (aimAtHer) {
            SkillExecutor.execute(battle, skill, support, List.of(owner));
        } else {
            SkillExecutor.execute(battle, skill, support, List.of(battle.enemies.get(0)));
        }
        battle.processRequests();

        return new double[]{
                owner.getResources().value(COREFLAME) - coreBefore,
                owner.getAttribute(AttributeType.CRIT_ATTACK).get() - critBefore};
    }
}
