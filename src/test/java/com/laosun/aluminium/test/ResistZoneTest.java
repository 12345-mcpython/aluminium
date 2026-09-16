package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * P1-6 acceptance: the resistance zone reads the defender's per-element table
 * (HSR.md §2.5: 抗性区 = 1 - clamp(抗性 - 穿透), 抗性 ∈ [-100%, 90%]).
 *
 * <p>Every defender here has DEFENCE = 0 so the resistance zone is isolated.
 */
public class ResistZoneTest {
    private static final double EPS = 1e-6;
    private static final double BASE = 1000.0;

    private static Character attacker(double penetration) {
        Character attacker = Character.fromAttributes("attacker", 1000, 100, 100, 100);
        attacker.setAttribute(AttributeType.DAMAGE_PENETRATION, new DoubleValue(penetration));
        return attacker;
    }

    private static Enemy enemy() {
        Enemy enemy = Enemy.fromAttributes("enemy", 100000, 0, 100, 100);
        enemy.setDamageResist(Map.of(DamageElement.ICE, 0.2, DamageElement.FIRE, 1.2));
        return enemy;
    }

    private static double settle(Character attacker, Enemy defender, DamageElement element) {
        Damage damage = new Damage(attacker, defender, element, DamageType.NORMAL, BASE);
        return new Battle(List.of(attacker), List.of(defender), new Random(0))
                .applyDamage(defender, damage);
    }

    private static double settle(Character attacker, Character defender, DamageElement element) {
        Damage damage = new Damage(attacker, defender, element, DamageType.NORMAL, BASE);
        return new Battle(List.of(attacker, defender), List.of(), new Random(0))
                .applyDamage(defender, damage);
    }

    @Test
    public void resistanceIsReducedByPenetration() {
        // ICE 抗 0.2 - 穿透 0.4 = -0.2 → 抗性区 1.2
        Assertions.assertEquals(1200, settle(attacker(0.4), enemy(), DamageElement.ICE), EPS);
    }

    @Test
    public void negativeResistanceKeepsFullEffect() {
        // ICE 抗 0.2 - 穿透 0.5 = -0.3 → 抗性区 1.3（负抗全效，HSR.md §2.5）
        Assertions.assertEquals(1300, settle(attacker(0.5), enemy(), DamageElement.ICE), EPS);
    }

    @Test
    public void resistanceIsClampedToNinetyPercent() {
        // FIRE 抗 1.2 → clamp 0.9 → 抗性区 0.1
        Assertions.assertEquals(100, settle(attacker(0.0), enemy(), DamageElement.FIRE), EPS);
    }

    @Test
    public void elementMissingFromTheTableHasNoResistance() {
        Assertions.assertEquals(BASE, settle(attacker(0.0), enemy(), DamageElement.PHYSICAL), EPS);
    }

    @Test
    public void nonEnemyDefenderHasNoResistanceTable() {
        Character defender = Character.fromAttributes("defender", 1000, 0, 100, 100);

        Assertions.assertEquals(BASE, settle(attacker(0.0), defender, DamageElement.ICE), EPS);
    }
}
