package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P6-2 / P6-3 acceptance: the healing damage zone and shields.
 *
 * <pre>
 * healing = base amount × (1 + outgoing healing boost) × (1 + heal taken ratio)   ← the two factors come from different people
 * shield: damage drains the shield first, and you do not die before the shield breaks; shields do not stack
 * </pre>
 */
public class HealShieldTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // P6-2 healing damage zone
    // ==================================================================

    @Test
    public void healIsBaseTimesOutgoingTimesTaken() {
        Character healer = character("healer");
        Character target = character("target");
        healer.setAttribute(AttributeType.OUTGOING_HEALING_BOOST, new DoubleValue(0.3));
        target.setAttribute(AttributeType.HEAL_TAKEN_RATIO, new DoubleValue(0.2));
        Battle battle = newBattle(healer, target);

        Assertions.assertEquals(1560, battle.calculateHeal(healer, target, 1000), EPS,
                "1000 × 1.3 × 1.2 = 1560");
    }

    @Test
    public void negativeHealTakenRatioActsAsHealingReduction() {
        Character healer = character("healer");
        Character target = character("target");
        target.setAttribute(AttributeType.HEAL_TAKEN_RATIO, new DoubleValue(-0.5));
        Battle battle = newBattle(healer, target);

        Assertions.assertEquals(500, battle.calculateHeal(healer, target, 1000), EPS,
                "heal taken -50% → 1000 × 1 × 0.5 = 500 (the game has no separate \"healing reduction\" attribute, so a negative number expresses it)");
    }

    @Test
    public void healActuallyRestoresHpAndIsCappedAtMaxHp() {
        Character healer = character("healer");
        Character target = character("target");
        healer.setAttribute(AttributeType.OUTGOING_HEALING_BOOST, new DoubleValue(0.3));
        target.setAttribute(AttributeType.HEAL_TAKEN_RATIO, new DoubleValue(0.2));
        Battle battle = newBattle(healer, target);

        target.takeDamage(2000);
        double before = target.getCurrentHp();
        double healed = battle.heal(healer, target, 1000);

        Assertions.assertEquals(1560, healed, EPS, "the actual amount restored = the heal amount after the damage zones");
        Assertions.assertEquals(before + 1560, target.getCurrentHp(), EPS);

        // Heal once more: this time it hits the cap, and what is returned is the **actual** amount restored
        double healed2 = battle.heal(healer, target, 100_000);
        Assertions.assertEquals(target.getMaxHp(), target.getCurrentHp(), EPS, "does not exceed the cap");
        Assertions.assertEquals(target.getMaxHp() - (before + 1560), healed2, EPS,
                "the return value is the actual amount restored, not the theoretical heal amount");
    }

    @Test
    public void deadTargetIsNotHealed() {
        Character healer = character("healer");
        Character target = character("target");
        Battle battle = newBattle(healer, target);

        target.takeDamage(999_999);
        Assertions.assertTrue(target.isDeath());

        Assertions.assertEquals(0, battle.heal(healer, target, 1000), EPS, "a dead man cannot be healed");
        Assertions.assertEquals(0, target.getCurrentHp(), EPS);
    }

    // ==================================================================
    // P6-3 shield
    // ==================================================================

    @Test
    public void shieldAbsorbsDamageBeforeHp() {
        Character target = character("target");
        Battle battle = newBattle(character("ally"), target);

        Assertions.assertEquals(500, battle.grantShield(target, 500), EPS);
        target.takeDamage(300);

        Assertions.assertEquals(200, target.getShield(), EPS, "shield 500 - 300 = 200");
        Assertions.assertEquals(target.getMaxHp(), target.getCurrentHp(), EPS, "HP did not drop at all");
    }

    @Test
    public void overflowAfterTheShieldBreaksHitsHp() {
        Character target = character("target");
        Battle battle = newBattle(character("ally"), target);
        battle.grantShield(target, 500);

        target.takeDamage(300);                                  // shield → 200
        boolean died = target.takeDamage(300);                   // the shield eats 200, the remaining 100 goes into HP

        Assertions.assertFalse(died, "breaking the shield does not kill");
        Assertions.assertEquals(0, target.getShield(), EPS, "the shield is empty");
        Assertions.assertEquals(target.getMaxHp() - 100, target.getCurrentHp(), EPS, "HP -100");
        Assertions.assertFalse(target.isDeath());
    }

    /**
     * Shields **do not stack**: a new shield overwrites the old value instead of adding to it.
     */
    @Test
    public void shieldDoesNotStack() {
        Character target = character("target");
        Battle battle = newBattle(character("ally"), target);

        battle.grantShield(target, 500);
        battle.grantShield(target, 300);

        Assertions.assertEquals(300, target.getShield(), EPS, "overwrite, not 800");
        Assertions.assertEquals(0, battle.grantShield(target, -50), EPS, "≤ 0 is treated as clearing the shield");
        Assertions.assertEquals(0, target.getShield(), EPS);
    }

    /**
     * Damage that goes into the shield **must count towards the damage of this hit** (otherwise "hitting a
     * shielded target" would show as 0, and {@code AttackEvent.totalDamage} and kill energy gain would both
     * be distorted).
     */
    @Test
    public void damageAbsorbedByTheShieldStillCountsAsDamageDealt() {
        Enemy attacker = EnemyFactory.create(1002011, 90, 1);
        Character target = character("target");
        target.setMaxEnergy(120);
        Battle battle = newBattle(attacker, target);
        battle.grantShield(target, 10_000);                      // the shield is far larger than this hit

        double hpBefore = target.getCurrentHp();
        double dealt = battle.applyDamage(target, new Damage(attacker, target,
                DamageElement.ICE, DamageType.NORMAL, 1000));

        // The **settled value** of this hit must first go through the defence zone (character defence 100):
        // 1000 × 1600/(100+1600) ≈ 941.18
        double levelTerm = com.laosun.aluminium.Constant.DEFENCE_CONST
                + com.laosun.aluminium.Constant.DEFENCE_PER_LEVEL * attacker.getLevel();
        double settled = 1000 * levelTerm / (100 + levelTerm);

        Assertions.assertEquals(hpBefore, target.getCurrentHp(), EPS, "HP did not move (all eaten by the shield)");
        Assertions.assertEquals(settled, target.getLastShieldAbsorbed(), EPS);
        Assertions.assertEquals(settled, dealt, EPS, "what is returned is \"settled value + shield absorbed\"");
    }

    @Test
    public void invulnerableAndDeadTargetsStillIgnoreShields() {
        Character target = character("target");
        Battle battle = newBattle(character("ally"), target);
        battle.grantShield(target, 500);

        target.setInvulnerable(true);
        Assertions.assertEquals(0, battle.applyDamage(target, new Damage(
                character("x"), target, DamageElement.ICE, DamageType.NORMAL, 1000)), EPS);
        Assertions.assertEquals(500, target.getShield(), EPS, "while invulnerable not even the shield drops");
    }

    // ==================================================================

    private static Character character(String name) {
        return Character.fromAttributes(name, 3000, 100, 100, 100);
    }

    private static Battle newBattle(com.laosun.aluminium.models.CanHit a,
                                   com.laosun.aluminium.models.CanHit b) {
        List<Character> characters = new java.util.ArrayList<>();
        List<Enemy> enemies = new java.util.ArrayList<>();
        for (com.laosun.aluminium.models.CanHit c : List.of(a, b)) {
            if (c instanceof Character character) {
                characters.add(character);
            } else if (c instanceof Enemy enemy) {
                enemies.add(enemy);
            }
        }
        if (characters.isEmpty()) {
            characters.add(character("filler"));
        }
        if (enemies.isEmpty()) {
            enemies.add(EnemyFactory.create(1002011, 90, 1));
        }
        return new Battle(characters, enemies, new Random(20260919));
    }
}
