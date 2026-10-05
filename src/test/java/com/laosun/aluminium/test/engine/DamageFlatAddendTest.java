package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * "提高数值等同于&lt;某属性&gt;的 Y%" rides the <b>base layer</b>, not the boost zone (ROADMAP M-55).
 *
 * <p>The primitive: {@code Damage.addFlat(value)}. The decision it encodes - an absolute addend is added to the skill
 * multiplier <b>before</b> the zones, so it crits and is boosted exactly like the multiplier - is what these cases pin.
 * A percentage in the boost zone would be a different number whenever the instance's base differs from the attribute,
 * which is why "伤害值提高" cannot reuse {@code addBoost}.
 */
public class DamageFlatAddendTest {
    private static final double EPS = 1e-6;

    /** The addend survives the zone chain by being part of the base: 100  x  1.5 = 150, not 100  x  1.5 + 50. */
    @Test
    public void theAddendIsMultipliedByTheZonesLikeTheBase() {
        Damage damage = damage(100);
        damage.addBoost(0.5);

        Assertions.assertEquals(150, damage.toValue(), EPS, "the stated base times the boost");

        damage.addFlat(50);

        Assertions.assertEquals(225, damage.toValue(), EPS,
                "(100 + 50) × 1.5 = 225 -- the addend is part of the BASE, so a boost multiplies it too. A boost-zone "
                        + "spelling would have given 100 × 2.0 = 200");
    }

    /** A boost is a percentage of the base; the addend is a value - so they are not interchangeable, and this is the pair. */
    @Test
    public void anAddendIsNotAPercentage() {
        Damage asAddend = damage(100);
        asAddend.addFlat(30);
        Damage asBoost = damage(100);
        asBoost.addBoost(0.30);

        Assertions.assertEquals(130, asAddend.toValue(), EPS);
        Assertions.assertEquals(130, asBoost.toValue(), EPS, "…equal only because the base is 100 (the trap)");

        // Any other base separates them: that is exactly why M-55 needed its own spelling.
        Damage otherBase = damage(1000);
        otherBase.addFlat(30);
        Assertions.assertEquals(1030, otherBase.toValue(), EPS, "+30 is +30 whatever the base is");
    }

    /** The addend rides into the assembled value through the real pipeline, so the zone order is the real one. */
    @Test
    public void theEngineAppliesItThroughAssemble() {
        Character attacker = CharacterFactory.create(1003, 80);
        Enemy defender = EnemyFactory.create(1002011, 90, 1);
        defender.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
        Battle battle = new Battle(List.of(attacker), List.of(defender), fixed());
        Damage plain = new Damage(attacker, defender, DamageElement.FIRE, DamageType.NORMAL, 100, null);
        Damage withAddend = new Damage(attacker, defender, DamageElement.FIRE, DamageType.NORMAL, 100, null);
        withAddend.addFlat(100);

        double without = battle.applyDamage(defender, plain);
        defender.heal(defender.getMaxHp());
        double with = battle.applyDamage(defender, withAddend);

        Assertions.assertEquals(2 * without, with, 1.0,
                "adding 100 to a base of 100 doubles the settled damage -- so the addend really is in the base layer "
                        + "of the engine's own assembly, not a number the test computed");
    }

    private static Damage damage(double base) {
        return new Damage(null, null, DamageElement.FIRE, DamageType.NORMAL, base);
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
