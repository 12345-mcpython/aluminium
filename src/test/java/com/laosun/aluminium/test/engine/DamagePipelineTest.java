package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.enemy.Enemy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * acceptance: {@code Battle.applyDamage} assembles the zones onto a {@link Damage},
 * settles it, applies it once, and is the only way in.
 *
 * <p>Attackers are plain {@code Character.fromAttributes} combatants (Lv80, no light cone
 * or relic), so every boost / crit attribute starts at 0 and the defender's DEFENCE
 * isolates the zone under test.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the scenarios below, one
 * {@code assertAll} per fixture so a failing clause still names itself. The reflection
 * entry-point check keeps its own test: an {@code assertThrows} is not a positive clause
 * and must not hide behind one.
 */
public class DamagePipelineTest {
    private static final double EPS = 1e-6;
    private static final double BASE = 1000.0;
    /**
     * The level term of the defence zone when the attacker is level 80: 200 + 10  x  80.
     */
    private static final double LEVEL_TERM = 1000.0;

    private static Character attacker() {
        return Character.fromAttributes("attacker", 1000, 100, 100, 100);
    }

    private static Enemy defender(double defence) {
        return Enemy.fromAttributes("defender", 100000, defence, 100, 100);
    }

    private static Battle battle(Character attacker, Enemy defender, long seed) {
        return new Battle(List.of(attacker), List.of(defender), new Random(seed));
    }

    private static double settle(Character attacker, Enemy defender, DamageType type, double base) {
        Damage damage = new Damage(attacker, defender, DamageElement.FIRE, type, base);
        return battle(attacker, defender, 0).applyDamage(defender, damage);
    }

    private static double settle(Character attacker, Enemy defender) {
        return settle(attacker, defender, DamageType.NORMAL, BASE);
    }

    private static double critSettled(double critRate, double critDamage) {
        Character attacker = attacker();
        attacker.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(critRate));
        attacker.setAttribute(AttributeType.CRIT_ATTACK, new DoubleValue(critDamage));
        return settle(attacker, defender(0));
    }

    @Test
    public void boostZoneSettlesOnceAndAppliesHpOnce() {
        Character attacker = attacker();
        attacker.setAttribute(AttributeType.FIRE_DAMAGE_BOOST, new DoubleValue(0.3));

        // same fixture as the "applied exactly once" clause: a fire-boosted attacker vs an undefended dummy
        double fireBoostOnly = settle(attacker, defender(0));

        Enemy victim = defender(0);
        double hpBefore = victim.getCurrentHp();
        Damage damage = new Damage(attacker, victim, DamageElement.FIRE, DamageType.NORMAL, BASE);
        double settled = battle(attacker, victim, 0).applyDamage(victim, damage);

        // attack-type DMG boost and elemental DMG boost add up into the same zone: 1 + 0.3 + 0.2 = 1.5
        attacker.setAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST, new DoubleValue(0.2));
        double withAllTypeBoost = settle(attacker, defender(0));

        Assertions.assertAll("the boost zone settles and the damage lands once",
                () -> Assertions.assertEquals(1300, fireBoostOnly, EPS,
                        "boost zone: FIRE_DAMAGE_BOOST 0.3"),
                () -> Assertions.assertEquals(1300, settled, EPS,
                        "boost zone: the settlement value seen when the hit is applied"),
                () -> Assertions.assertEquals(hpBefore - settled, victim.getCurrentHp(), EPS,
                        "applied once: the defender HP drops by exactly the settled damage"),
                () -> Assertions.assertEquals(1500, withAllTypeBoost, EPS,
                        "boost zone: FIRE_DAMAGE_BOOST 0.3 + ALL_DAMAGE_TYPE_BOOST 0.2"));
    }

    @Test
    public void critFollowsTheInjectedRandom() {
        // the first value of Random(0) ~= 0.310 - it is used to pin down both the "crit / no crit" branches
        double firstRoll = new Random(0).nextDouble();
        Assertions.assertAll("the crit zone reads the injected random",
                () -> Assertions.assertTrue(firstRoll > 0.5 && firstRoll < 0.9,
                        "assertion premise: the first value of Random(0) should lie in 0.5 ~ 0.9, actual " + firstRoll),
                () -> Assertions.assertEquals(1000, critSettled(0.5, 1.0), EPS,
                        "crit zone: roll > 0.5 to no crit"),
                () -> Assertions.assertEquals(2000, critSettled(0.9, 1.0), EPS,
                        "crit zone: roll < 0.9 to crit"));
    }

    @Test
    public void nonCrittableTypeSkipsCritZone() {
        Character attacker = attacker();
        attacker.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(1.0));
        attacker.setAttribute(AttributeType.CRIT_ATTACK, new DoubleValue(1.0));

        // BREAK: does not take crit stats (not even a roll), while the defence zone applies as usual
        Assertions.assertEquals(BASE * LEVEL_TERM / (1150 + LEVEL_TERM),
                settle(attacker, defender(1150), DamageType.BREAK, BASE), EPS);
    }

    @Test
    public void defenceZoneScenarios() {
        Assertions.assertAll("the defence zone follows the attacker level and the defender defence",
                () -> Assertions.assertEquals(BASE * LEVEL_TERM / (1150 + LEVEL_TERM),
                        settle(attacker(), defender(1150)), EPS,
                        "defence zone: level term 200 + 10 x 80 against 1150 DEF"),
                () -> {
                    Character ignoring = attacker();
                    ignoring.setAttribute(AttributeType.DEFENCE_IGNORE, new DoubleValue(0.5));
                    Assertions.assertEquals(BASE * LEVEL_TERM / (575 + LEVEL_TERM),
                            settle(ignoring, defender(1150)), EPS,
                            "defence zone: DEFENCE_IGNORE 0.5 halves the effective defence");
                });
    }

    @Test
    public void deadTargetTakesNothing() {
        Character attacker = attacker();
        Enemy defender = defender(0);
        defender.takeDamage(defender.getCurrentHp());   // kill it outright

        Damage damage = new Damage(attacker, defender, DamageElement.FIRE, DamageType.NORMAL, BASE);

        Assertions.assertAll("a dead target no longer takes damage",
                () -> Assertions.assertTrue(defender.isDeath(),
                        "dead target: the fixture is actually dead"),
                () -> Assertions.assertEquals(0, battle(attacker, defender, 0).applyDamage(defender, damage), EPS,
                        "dead target: applyDamage settles to zero"),
                () -> Assertions.assertEquals(0, defender.getCurrentHp(), EPS,
                        "dead target: HP stays at zero"));
    }

    @Test
    public void settlementHasExactlyOnePublicEntryPoint() throws Exception {
        // the old entry points MUST be gone (E2 complete)
        Assertions.assertThrows(NoSuchMethodException.class, () -> Battle.class.getDeclaredMethod(
                "calculateDamage", CanHit.class, CanHit.class, double.class, List.class));
        Assertions.assertThrows(NoSuchMethodException.class, () -> Battle.class.getDeclaredMethod(
                "applyDamage", CanHit.class, double.class));

        // the assembly entry point MUST be private: from outside you can only get in through applyDamage
        Assertions.assertTrue(Modifier.isPrivate(Battle.class
                .getDeclaredMethod("assemble", Damage.class).getModifiers()));

        // among the public API, there can be only one method that takes a Damage
        long publicDamageEntries = Arrays.stream(Battle.class.getMethods())
                .filter(method -> Arrays.asList(method.getParameterTypes()).contains(Damage.class))
                .count();
        Assertions.assertEquals(1, publicDamageEntries, "the settlement entry point MUST be unique");
    }
}
