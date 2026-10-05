package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1212 Jingliu, from her own file (2026-09-29, round 225): the Syzygy stack, its cap, and the threshold that fires her Action Advance.
 *
 * <p>The threshold is a `self_stacks:` condition — a form the engine already had — and the Advance is observed as round 216's recipe prescribes: the remaining wait must reach 0.
 */
public class JingliuTest {
    private static final int JINGLIU = 1212;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⚠ One stack per Skill, stopped at the document's three. */
    @Test
    public void theSkillAddsOneStackUpToThree() {
        Character jingliu = CharacterFactory.create(JINGLIU, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(jingliu), List.of(enemy), fixed());
        battle.startBattle();

        battle.fireTriggers(TriggerEvent.SKILL_CAST, jingliu, enemy, 0, 0);
        Assertions.assertEquals(1, jingliu.getBuffManager().stacksOf("朔望"),
                "「并获得1层【朔望】」");

        for (int i = 0; i < 4; i++) {
            battle.fireTriggers(TriggerEvent.SKILL_CAST, jingliu, enemy, 0, 0);
        }
        Assertions.assertEquals(3, jingliu.getBuffManager().stacksOf("朔望"),
                "「【朔望】最多可累计3层」 -- five casts must still read three");
    }

    /** ⚠ The threshold: the SECOND cast reaches two stacks and must Advance; the first must not. */
    @Test
    public void theAdvanceNeedsTwoStacks() {
        double afterOne = waitAfterCasts(1);
        double afterTwo = waitAfterCasts(2);
        double untouched = waitAfterCasts(0);

        Assertions.assertTrue(untouched > 0, "the fixture must have a wait to skip: " + untouched);
        Assertions.assertEquals(1.0, afterOne / untouched, 1e-6,
                "with ONE stack the Advance must not fire: " + untouched + " -> " + afterOne);
        Assertions.assertEquals(0.0, afterTwo, 1e-6,
                "「当拥有2层【朔望】时…使行动提前100%」: " + untouched + " -> " + afterTwo);
    }

    /** Casts the Skill `casts` times (the RULES build the stacks) and returns her remaining wait. */
    private static double waitAfterCasts(int casts) {
        Character jingliu = CharacterFactory.create(JINGLIU, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(jingliu), List.of(enemy), fixed());
        battle.startBattle();
        for (int i = 0; i < casts; i++) {
            battle.fireTriggers(TriggerEvent.SKILL_CAST, jingliu, enemy, 0, 0);
        }
        for (com.laosun.aluminium.models.Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == jingliu) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        throw new IllegalStateException("she is not in the queue");
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        };
    }
}
