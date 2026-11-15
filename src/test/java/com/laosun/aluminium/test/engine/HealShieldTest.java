package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * acceptance: the healing damage zone and shields.
 *
 * <pre>
 * healing = base amount  x  (1 + outgoing healing boost)  x  (1 + heal taken ratio) from the two factors come from different people
 * shield: damage drains the shield first, and you do not die before the shield breaks; shields do not stack
 * </pre>
 */
public class HealShieldTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // healing damage zone
    // ==================================================================

    @Test
    public void healingAppliesBothZonesAndRestoresHpUpToTheCap() {
        Character healer = character("healer");
        Character target = character("target");
        healer.setAttribute(AttributeType.OUTGOING_HEALING_BOOST, new DoubleValue(0.3));
        target.setAttribute(AttributeType.HEAL_TAKEN_RATIO, new DoubleValue(0.2));
        Battle battle = newBattle(healer, target);

        double calculated = battle.calculateHeal(healer, target, 1000);

        target.takeDamage(2000);
        double before = target.getCurrentHp();
        double healed = battle.heal(healer, target, 1000);
        double hpAfterFirstHeal = target.getCurrentHp();

        // Heal once more: this time it hits the cap, and what is returned is the actual amount restored
        double healed2 = battle.heal(healer, target, 100_000);
        double hpAfterSecondHeal = target.getCurrentHp();

        Assertions.assertAll("healing = base × (1 + outgoing) × (1 + taken), then applied and capped",
                () -> Assertions.assertEquals(1560, calculated, EPS,
                        "1000 × 1.3 × 1.2 = 1560"),
                () -> Assertions.assertEquals(1560, healed, EPS,
                        "the actual amount restored = the heal amount after the damage zones"),
                () -> Assertions.assertEquals(before + 1560, hpAfterFirstHeal, EPS,
                        "HP rises by exactly the amount healed"),
                () -> Assertions.assertEquals(target.getMaxHp(), hpAfterSecondHeal, EPS,
                        "does not exceed the cap"),
                () -> Assertions.assertEquals(target.getMaxHp() - (before + 1560), healed2, EPS,
                        "the return value is the actual amount restored, not the theoretical heal amount"));
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
    public void deadTargetIsNotHealed() {
        Character healer = character("healer");
        Character target = character("target");
        Battle battle = newBattle(healer, target);

        target.takeDamage(999_999);
        Assertions.assertTrue(target.isDeath());

        double healed = battle.heal(healer, target, 1000);
        double hp = target.getCurrentHp();

        Assertions.assertAll("a dead target is never healed",
                () -> Assertions.assertEquals(0, healed, EPS, "a dead man cannot be healed"),
                () -> Assertions.assertEquals(0, hp, EPS, "the HP stays at 0"));
    }

    // ==================================================================
    // shield
    // ==================================================================

    /**
     * A shield absorbs damage before HP, so it breaks before the target can die; a new shield
     * overwrites the old value instead of adding to it.
     */
    @Test
    public void shieldAbsorbsDamageBeforeHpAndDoesNotStack() {
        Character target = character("target");
        Battle battle = newBattle(character("ally"), target);

        double granted = battle.grantShield(target, 500);
        target.takeDamage(300);
        double shieldAfterFirstHit = target.getShield();
        double hpAfterFirstHit = target.getCurrentHp();

        Assertions.assertAll("a shield absorbs damage before HP",
                () -> Assertions.assertEquals(500, granted, EPS, "granting 500 sets a 500-point shield"),
                () -> Assertions.assertEquals(200, shieldAfterFirstHit, EPS, "shield 500 - 300 = 200"),
                () -> Assertions.assertEquals(target.getMaxHp(), hpAfterFirstHit, EPS, "HP did not drop at all"));

        boolean died = target.takeDamage(300);                   // the shield eats 200, the remaining 100 goes into HP
        double shieldAfterBreak = target.getShield();
        double hpAfterBreak = target.getCurrentHp();
        boolean deadAfterBreak = target.isDeath();

        Assertions.assertAll("overflow past the broken shield goes into HP",
                () -> Assertions.assertFalse(died, "breaking the shield does not kill"),
                () -> Assertions.assertEquals(0, shieldAfterBreak, EPS, "the shield is empty"),
                () -> Assertions.assertEquals(target.getMaxHp() - 100, hpAfterBreak, EPS, "HP -100"),
                () -> Assertions.assertFalse(deadAfterBreak, "the target survives the overflow"));

        battle.grantShield(target, 500);
        battle.grantShield(target, 300);
        double overwritten = target.getShield();
        double cleared = battle.grantShield(target, -50);
        double shieldAfterClear = target.getShield();

        Assertions.assertAll("a new shield overwrites instead of stacking",
                () -> Assertions.assertEquals(300, overwritten, EPS, "overwrite, not 800"),
                () -> Assertions.assertEquals(0, cleared, EPS, "≤ 0 is treated as clearing the shield"),
                () -> Assertions.assertEquals(0, shieldAfterClear, EPS, "the shield is gone"));
    }

    /**
     * Damage that goes into the shield must count towards the damage of this hit (otherwise "hitting a
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

        // The settled value of this hit must first go through the defence zone (character defence 100):
        // 1000  x  1600/(100+1600) ~= 941.18
        double levelTerm = com.laosun.aluminium.Constant.DEFENCE_CONST
                + com.laosun.aluminium.Constant.DEFENCE_PER_LEVEL * attacker.getLevel();
        double settled = 1000 * levelTerm / (100 + levelTerm);
        double hpAfter = target.getCurrentHp();
        double absorbed = target.getLastShieldAbsorbed();

        Assertions.assertAll("damage eaten by a shield still counts as damage dealt",
                () -> Assertions.assertEquals(hpBefore, hpAfter, EPS, "HP did not move (all eaten by the shield)"),
                () -> Assertions.assertEquals(settled, absorbed, EPS, "the shield absorbed the fully settled hit"),
                () -> Assertions.assertEquals(settled, dealt, EPS,
                        "what is returned is \"settled value + shield absorbed\""));
    }

    @Test
    public void invulnerableAndDeadTargetsStillIgnoreShields() {
        Character target = character("target");
        Battle battle = newBattle(character("ally"), target);
        battle.grantShield(target, 500);

        target.setInvulnerable(true);
        double dealt = battle.applyDamage(target, new Damage(
                character("x"), target, DamageElement.ICE, DamageType.NORMAL, 1000));
        double shield = target.getShield();

        Assertions.assertAll("an invulnerable target loses no HP and no shield",
                () -> Assertions.assertEquals(0, dealt, EPS, "the settled damage is 0"),
                () -> Assertions.assertEquals(500, shield, EPS, "while invulnerable not even the shield drops"));
    }

    // ==================================================================

    private static Character character(String name) {
        return Character.fromAttributes(name, 3000, 100, 100, 100);
    }

    private static Battle newBattle(CanHit a, CanHit b) {
        List<Character> characters = new java.util.ArrayList<>();
        List<Enemy> enemies = new java.util.ArrayList<>();
        for (CanHit c : List.of(a, b)) {
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
