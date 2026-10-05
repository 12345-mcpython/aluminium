package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Light cone 21014: "效果抵抗提高#1%，并使治疗量提高，提高数值等同于效果抵抗的#2%，最多使治疗量提高#3%".
 *
 * <p>Note: EFFECT_RESISTANCE is a RATIO attribute, so the share is ABSOLUTE POINTS: the clause is judged by the GAIN, not by the resulting total (the wearer starts with
 * some resistance of her own). The healing clause derives from the BOOSTED resistance and is capped, so its expectation is the smaller of the product and the ceiling.
 */
public class PerfectTimingTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theHealingClauseDerivesFromTheBoostedResistance() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21014, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        double resistBefore = unit.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        double healingBefore = unit.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get();
        battle.startBattle();
        double resistGain = unit.getAttribute(AttributeType.EFFECT_RESISTANCE).get() - resistBefore;
        double resist = unit.getAttribute(AttributeType.EFFECT_RESISTANCE).get();
        double healing = unit.getAttribute(AttributeType.OUTGOING_HEALING_BOOST).get() - healingBefore;
        double expected = Math.min(resist * 0.45, 0.27);
        System.out.println("[21014] resistBefore=" + resistBefore + " resistGain=" + resistGain
                + " totalResist=" + resist + " healing=" + healing + " expected=" + expected);
        Assertions.assertEquals(0.32, resistGain, 1e-6, "rank 5 states 32 percentage points");
        Assertions.assertEquals(expected, healing, 1e-6, "min(boosted resistance x share, the stated ceiling)");
        Assertions.assertTrue(resist * 0.45 > 0.27, "precondition: the ceiling is what binds");
    }
}
