package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
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
 * Cones 21034 and 23018: a derived magnitude off the wearer's MAX ENERGY with a constant ceiling (`cap_amount`).
 *
 * <p>Expected value is the smaller of (max energy x per-point share) and the stated ceiling, so the judge states that formula rather than a number.
 */
public class Cones21034and23018Test {
    private static final int WEARER = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    @Test
    public void cone21034BoostsDamageByMaxEnergyUpToTheCeiling() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21034, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        double before = unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        double energy = unit.getMaxEnergy();
        battle.startBattle();
        double boost = unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get() - before;
        double expected = Math.min(energy * 0.004, 0.64);
        System.out.println("[21034] maxEnergy=" + energy + " boost=" + boost + " expected=" + expected);
        // ⚠ 1e-6, not 1e-9: the engine stores magnitudes through a float, so a derived value lands ~3.6e-8 off.
        Assertions.assertEquals(expected, boost, 1e-6, "min(max energy x share, the stated ceiling)");
    }

    @Test
    public void cone23018RaisesCritDamageAndAnUltScopedBoost() {
        Character unit = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23018, LEVEL, false, 5));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(unit), List.of(enemy), new Random(0));
        double critBefore = unit.getAttribute(AttributeType.CRIT_ATTACK).get();
        double energy = unit.getMaxEnergy();
        battle.startBattle();
        double crit = unit.getAttribute(AttributeType.CRIT_ATTACK).get() - critBefore;
        double boostBefore = unit.getAttribute(AttributeType.ULTIMATE_DAMAGE_BOOST).get();
        battle.fireTriggers(TriggerEvent.ULT_CAST, unit, enemy, 0, 0);
        double ultBoost = unit.getAttribute(AttributeType.ULTIMATE_DAMAGE_BOOST).get() - boostBefore;
        double expected = Math.min(energy * 0.006, 1.08);
        System.out.println("[23018] crit=" + crit + " maxEnergy=" + energy + " ultBoost=" + ultBoost
                + " expected=" + expected);
        Assertions.assertEquals(0.6, crit, 1e-6, "rank 5 states 60% crit damage");
        Assertions.assertEquals(expected, ultBoost, 1e-6, "min(max energy x share, the stated ceiling)");
    }
}
