package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.BreakDamageCalculator;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P4-3 acceptance: the break damage formula.
 *
 * <p>Anchor (ROADMAP P4-3): attacker Lv80, break effect 300%, toughness reduction 112.5 points,
 * enemy DEF 1150, no resistance and no reduction →
 * 376.75535 * 4.0 * 112.5 * 1000/2150 ≈ 78855.8.
 */
public class BreakDamageTest {
    private static final double DEFENCE = 1150;
    private static final double STANCE_DAMAGE = 112.5;

    @Test
    public void breakingRateIsLoadedFromData() {
        Assertions.assertEquals(3767.5535, Constant.BREAKING_RATE.get(80), 1e-4, "the data file holds the 10× value");
        Assertions.assertEquals(376.75535, Constant.BREAKING_RATE.get(80) / 10.0, 1e-6);
    }

    @Test
    public void breakDamageIsBaseTimesBreakingEffectTimesStanceThroughDefence() {
        Character breaker = breaker(3.0);
        Enemy dummy = dummy();
        Battle battle = new Battle(List.of(breaker), List.of(dummy), new Random(0));

        Damage breakDamage = BreakDamageCalculator.build(breaker, dummy, DamageElement.FIRE, STANCE_DAMAGE);
        double settled = battle.applyDamage(dummy, breakDamage);

        double expectedBase = 376.75535 * (1 + 3.0) * STANCE_DAMAGE;          // 169539.9
        double expected = expectedBase * (1000.0 / (DEFENCE + 1000.0));        // defence zone (Lv80 → 1000)
        Assertions.assertEquals(expected, settled, 1.0);
        Assertions.assertEquals(78_855.8, settled, 1.0);
        Assertions.assertEquals(dummy.getMaxHp() - settled, dummy.getCurrentHp(), 1.0);
    }

    @Test
    public void breakDamageIgnoresBoostAndNeverCrits() {
        Character breaker = breaker(3.0);
        breaker.setAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST, new DoubleValue(5.0));   // DMG boost 500%
        Enemy dummy = dummy();
        Battle battle = new Battle(List.of(breaker), List.of(dummy), new Random(0));

        double settled = battle.applyDamage(dummy,
                BreakDamageCalculator.build(breaker, dummy, DamageElement.FIRE, STANCE_DAMAGE));

        Assertions.assertEquals(78_855.8, settled, 1.0, "break damage does not take DMG boost");
        Assertions.assertFalse(DamageType.BREAK.isCrittable());
        Assertions.assertFalse(DamageType.BREAK.isBoostable());
    }

    @Test
    public void breakingEffectZeroGivesPlainBase() {
        Character breaker = breaker(0.0);
        Enemy dummy = dummy();
        Battle battle = new Battle(List.of(breaker), List.of(dummy), new Random(0));

        double settled = battle.applyDamage(dummy,
                BreakDamageCalculator.build(breaker, dummy, DamageElement.FIRE, 30));

        double expected = 376.75535 * 30 * (1000.0 / (DEFENCE + 1000.0));
        Assertions.assertEquals(expected, settled, 1.0);
    }

    @Test
    public void unknownLevelFailsFast() {
        Character breaker = breaker(0.0);
        breaker.setLevel(999);

        Assertions.assertThrows(IllegalArgumentException.class,
                () -> BreakDamageCalculator.build(breaker, dummy(), DamageElement.FIRE, 30));
    }

    private static Character breaker(double breakingEffect) {
        Character c = Character.fromAttributes("breaker", 10_000, 100, 100, 100);   // Lv80
        c.setAttribute(AttributeType.BREAKING_EFFECT, new DoubleValue(breakingEffect));
        return c;
    }

    private static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 1_000_000, DEFENCE, 100, 100);
    }
}
