package com.laosun.aluminium.test;

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
 * 800: "every time a target holding [迷迷的声援] (Mem's Support) deals 1 instance of damage, it additionally deals 1 instance of true damage equal to 28% of the original damage".
 *
 * <p>TWO-WAY, and driven by REAL content: his skill lays the cheer on the ally, the ally then attacks. The control battle is
 * the same fight without the cheer, so the EXCESS the enemy loses must be exactly 28% of what it lost in the control --
 * that is the sentence, and nothing else changes between the two runs (`level_convention` stays in place: no table is rebuilt).
 */
public class CheerTrueDamageRiderTest {
    private static final double EPS = 1e-6;
    private static final int OWNER = 8007;
    private static final int ALLY = 1002;
    private static final int MONSTER = 1002011;
    private static final String CHEER = "迷迷的声援";

    /** The holder's damage is raised by exactly the 28% the sentence states. */
    @Test
    public void theHolderDealsTwentyEightPercentMore() {
        double with = damageDealt(true);
        double without = damageDealt(false);
        Assertions.assertTrue(without > 0, "precondition: the control battle deals damage (" + without + ")");
        Assertions.assertEquals(without * 1.28, with, without * 0.05,
                "「additionally deals 1 instance of true damage equal to 28% of the original damage」 (with=" + with + ", without=" + without + ")");
    }

    private static double damageDealt(boolean cheer) {
        Character owner = CharacterFactory.create(OWNER, 80);
        Character ally = CharacterFactory.create(ALLY, 80);
        Battle battle = new Battle(List.of(owner, ally),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();

        // BOTH branches summon: his skill summons 迷迷 (Mimi), and a draft that cast it only in the "with" branch would put the
        // excess contained the memosprite's own damage too. Same scene, one variable: the control removes the cheer instead.
        Skill his = owner.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(his, "precondition: he has a skill");
        SkillExecutor.execute(battle, his, owner, List.of(ally));
        battle.processRequests();
        if (!cheer) {
            ally.getBuffManager().removeState(CHEER);
            battle.processRequests();
        }
        double before = battle.enemies.get(0).getCurrentHp();
        Skill hers = ally.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(hers, "precondition: the ally has a skill");
        SkillExecutor.execute(battle, hers, ally, List.of(battle.enemies.get(0)));
        battle.processRequests();
        return before - battle.enemies.get(0).getCurrentHp();
    }
}
