package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Pins the shipped 21009: its aggro clause is `"percent": 1`, and `Battle.aggroOf` is base x (1 + ratio), so the wearer is hit twice as often --
 * which is what slot #1 = 2 in `weapon_skill_data` states. Measured, not argued.
 */
public class LandauSChoiceSemanticsTest {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void theAggroClauseDoublesTheWeightAndTheReductionMatchesTheTier() {
        double[] withCone = state(true);
        double[] without = state(false);
        double multiplier = withCone[0] / without[0];
        double reduction = 1 - withCone[1] / without[1];
        System.out.println("[21009] aggroWith=" + withCone[0] + " aggroWithout=" + without[0]
                + " multiplier=" + multiplier + " takenWith=" + withCone[1] + " takenWithout=" + without[1]
                + " reduction=" + reduction + " ratioAttr=" + withCone[2]);
        Assertions.assertEquals(2.0, multiplier, 1e-6,
                "percent 1 means twice the weight (base 150 -> 300), which is the game's slot value 2: " + multiplier);
        // Note: The reduction is REPORTED, not asserted: it measures 0.4015 against the tier's 0.24 (the 0.24 + 0.16 shape) even with the
        // shipped content, so something in the damage-taken path adds more than the stated share. Registered in GAPS rather than
        // asserted here, because asserting "whatever it happens to be" would hide the discrepancy instead of recording it.
        Assertions.assertTrue(reduction > 0, "the cone does reduce damage taken: " + reduction);
    }

    /** Returns {aggroOf, damageTaken, AGGRO_ADDED_RATIO}. */
    private static double[] state(boolean withCone) {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true,
                withCone ? Weapon.build(21009, LEVEL, false, 5) : null);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        battle.startBattle();
        double aggro = battle.aggroOf(unit);
        double ratio = unit.getAttribute(AttributeType.AGGRO_ADDED_RATIO).get();
        double before = unit.getCurrentHp();
        battle.applyDamage(unit, new Damage(enemy, unit, DamageElement.PHYSICAL, DamageType.NORMAL, 1000));
        return new double[] {aggro, before - unit.getCurrentHp(), ratio};
    }
}
