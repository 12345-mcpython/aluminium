package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
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
 * Light cone 23062 随心, sentence 4: `per 1 point of energy spent, increases the ultimate damage dealt this time by #3%, up to #6%`.
 *
 * <p>Note: Why this is unit-level: an ultimate can only be cast at full energy (`Battle.castUltra`'s second line calls `isUltraReady`, and measured, 10 points of energy
 * makes it `return false` directly), so the "two kinds of spend" cannot be produced end to end. Here a `Damage` is built by hand and `castEnergySpent` is set on the instance
 * directly, the attribute boosts cancel out in the ratio, and what is left is exactly that clause.
 *
 * <p>Note: Four traps in the controls, all of them found by measuring:
 * <ol>
 *   <li>"equipped / not equipped" does not work - that measures the light cone's ATK +18% (measured 1.088), and removing the engine still leaves everything green;</li>
 *   <li>clearing `when` does not work - nothing changed at the time, but that experiment also had "the write never happened", so two variables failed together and the conclusion is void;</li>
 *   <li>"energy 0" does not work - `isUltraReady` returns `false` directly, so the experiment would run to completion on a false that was never examined;</li>
 *   <li>Note: A hand-built `Damage` must carry the ULTRA category - otherwise `from_skill ULTRA` does not even match, and changing or not changing `castEnergySpent` gives the same number (measured 550.099 ...).</li>
 * </ol>
 */
public class Cone23062SpendTest {
    private static final int LEVEL = 80;
    private static final int RANK = 1;
    private static final int WEARER = 1003;
    private static final int MONSTER = 1002011;
    private static final double PER_POINT = 0.002;
    private static final double CAP = 0.72;
    private static final double CAP_POINTS = 360;

    /** The same light cone, the same thing, changing only "the spend this time"; returns the damage. */
    private static double damageWithSpend(double spend) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true,
                Weapon.build(23062, LEVEL, false, RANK));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(wearer), List.of(enemy), new Random(0));
        battle.startBattle();
        Damage hit = new Damage(enemy, wearer, DamageElement.FIRE, DamageType.NORMAL, 1000,
                SkillCategory.ULTRA);
        hit.withCastEnergySpent(spend);
        return battle.applyDamage(enemy, hit);
    }

    @Test
    public void theUltimateGainsPerPointOfEnergySpent() {
        double at50 = damageWithSpend(50);
        double at120 = damageWithSpend(120);
        Assertions.assertTrue(at50 > 0 && at120 > 0, "both hits must land");
        double ratio = at120 / at50;
        double expected = (1 + 120 * PER_POINT) / (1 + 50 * PER_POINT);
        System.out.println("[23062] at50=" + at50 + " at120=" + at120
                + " ratio=" + ratio + " expected=" + expected);
        Assertions.assertEquals(expected, ratio, 1e-4,
                "per " + PER_POINT + " per point: 120 vs 50 points");
    }

    @Test
    public void theBonusIsCappedAbsolutely() {
        double huge = damageWithSpend(1_000_000);
        double atCap = damageWithSpend(CAP_POINTS);
        System.out.println("[23062] huge=" + huge + " atCap=" + atCap
                + " (cap " + CAP + " needs " + CAP_POINTS + " points)");
        Assertions.assertEquals(atCap, huge, 1e-6,
                "beyond " + CAP_POINTS + " points the boost stops growing at " + CAP);
    }
}
