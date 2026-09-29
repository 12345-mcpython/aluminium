package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1314 翡翠's 【当品】: one layer at the enemy's battle entry, fifteen from her technique, and 2.40% critical damage each.
 *
 * <p>Both the counter's name and the values come from the data; the technique half is measured with and without
 * `markTechniqueUsed`, which is what its `self has_state 秘技` gate reads.
 */
public class JadeStackTest {
    private static final int JADE = 1314;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final double EPS = 1e-9;
    private static final String COUNTER = "当品";

    @Test
    public void theTechniqueGrantsFifteenAndTheTraceOne() {
        Assertions.assertEquals(1, layers(false), "行迹: one layer when a battle starts");
        Assertions.assertEquals(16, layers(true), "秘技: fifteen more, so sixteen in total");
    }

    /**
     * 2.40% per layer.
     *
     * <p>⚠ The absolute delta carries the unit's own inherent +0.5 critical damage (measured: 0.524 for one layer, where
     * the clause owns 0.024), so the clause's share is pinned twice: the measured one-layer total, and the difference
     * between sixteen layers and one, which is 15 x 2.4% and does not care what the unit carries.
     */
    @Test
    public void eachLayerAddsTwoPointFourPercentCriticalDamage() {
        Assertions.assertEquals(0.524, criticalDamage(false), EPS, "one layer on top of the inherent 0.5");
        Assertions.assertEquals(0.36, criticalDamage(true) - criticalDamage(false), EPS,
                "fifteen more layers are 15 x 2.40%");
    }

    @Test
    public void eachLayerAddsHalfAPercentAttack() {
        Fixture one = fixture(false);
        Fixture sixteen = fixture(true);
        double base = one.jade.getAttribute(AttributeType.ATTACK).baseValue();
        double delta = sixteen.jade.getAttribute(AttributeType.ATTACK).get()
                - one.jade.getAttribute(AttributeType.ATTACK).get();
        Assertions.assertEquals(0.075 * base, delta, base * 1e-6,
                "fifteen more layers are 15 x 0.50% of the base");
    }

    private static int layers(boolean technique) {
        Fixture f = fixture(technique);
        return f.jade.getBuffManager().stacksOf(COUNTER);
    }

    private static double criticalDamage(boolean technique) {
        Fixture f = fixture(technique);
        return f.jade.getAttribute(AttributeType.CRIT_ATTACK).get()
                - f.jade.getAttribute(AttributeType.CRIT_ATTACK).baseValue();
    }

    private static Fixture fixture(boolean technique) {
        Character jade = CharacterFactory.create(JADE, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(jade), List.of(enemy), new Random(0));
        if (technique) {
            battle.markTechniqueUsed(jade);
        }
        battle.startBattle();
        return new Fixture(jade);
    }

    private record Fixture(Character jade) {
    }
}
