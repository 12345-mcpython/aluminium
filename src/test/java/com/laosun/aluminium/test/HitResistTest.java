package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.buffs.StunBuff;
import com.laosun.aluminium.utils.AttributeBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P6-1 acceptance: effect hit rate and resistance.
 *
 * <pre>
 * chance to land = base chance × (1 + caster's hit rate) × (1 - target's resistance) × (1 - specific debuff resistance), clamp [0,1]
 * </pre>
 *
 * <p>All three factors are **multiplied** (it is not "hit rate minus resistance"). Anchors (ROADMAP):
 * base 1.0 + hit 0 + resist 0.3 → 0.7; with hit 0.5 → 1.0 (clamp);
 * base 0.8 + hit 0.25 + resist 0.2 → 0.8.
 */
public class HitResistTest {
    private static final double EPS = 1e-9;

    @Test
    public void chanceIsBaseTimesHitTimesResist() {
        Character caster = caster(0, 0);                 // hit 0
        Enemy target = enemyWithResist(0.3, null);       // resist 0.3

        Assertions.assertEquals(0.7, newBattle(caster, target).hitChance(caster, target, 1.0, null), EPS,
                "1.0 × (1+0) × (1-0.3) = 0.7");
    }

    @Test
    public void chanceIsClampedToOne() {
        Character caster = caster(0.5, 0);
        Enemy target = enemyWithResist(0.3, null);

        Assertions.assertEquals(1.0, newBattle(caster, target).hitChance(caster, target, 1.0, null), EPS,
                "1.0 × 1.5 × 0.7 = 1.05 → clamped to 1.0");
    }

    @Test
    public void baseBelowOneMultipliesBothFactors() {
        Character caster = caster(0.25, 0);
        Enemy target = enemyWithResist(0.2, null);

        Assertions.assertEquals(0.8, newBattle(caster, target).hitChance(caster, target, 0.8, null), EPS,
                "0.8 × 1.25 × 0.8 = 0.8");
    }

    /**
     * Specific debuff resistance: 冰锋's {@code debuff_resistance = {"STAT_CTRL_Frozen": 1}}
     * → fully immune to freeze (chance 0), other effects are unaffected.
     */
    @Test
    public void specificResistanceCanNullifyTheChance() {
        Character caster = caster(0, 0);
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);

        Assertions.assertEquals(1.0, iceEdge.getDebuffResist().get("STAT_CTRL_Frozen"), EPS,
                "in Ice Edge's data it is 100% resistance to freeze");
        Assertions.assertEquals(0.0,
                newBattle(caster, iceEdge).hitChance(caster, iceEdge, 1.0, "STAT_CTRL_Frozen"), EPS,
                "specific resistance 1.0 → the key factor (1-1) = 0 → fully immune");

        // note that 冰锋 itself also has 30% effect resistance (template 0.2 + level group 0.1), so "unaffected by
        // the specific resistance" ≠ a chance of 1.0, it is 1.0 × (1 - 0.3) = 0.7
        Assertions.assertEquals(0.7,
                newBattle(caster, iceEdge).hitChance(caster, iceEdge, 1.0, "STAT_DOT_Burn"), EPS,
                "a key that was not configured counts as 0 → only the generic 30% effect resistance is left");
        Assertions.assertEquals(0.7,
                newBattle(caster, iceEdge).hitChance(caster, iceEdge, 1.0, null), EPS,
                "without a key the specific resistance is not looked up → same as above");
    }

    /**
     * A character's effect resistance comes from the stat sheet ({@code EFFECT_RESISTANCE}), and there is **no**
     * specific resistance table.
     */
    @Test
    public void characterUsesPanelResistanceOnly() {
        Character caster = caster(0, 0);
        Character victim = Character.fromAttributes("victim", 10_000, 100, 100, 100);
        victim.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0.4));

        Assertions.assertEquals(0.6,
                newBattle(caster, victim).hitChance(caster, victim, 1.0, "STAT_CTRL_Frozen"), EPS,
                "characters have no specific resistance table, only the stat sheet resistance 0.4");
    }

    /**
     * An enemy's effect hit rate **must make it onto the stat sheet**: {@code EnemyScaler} computed 0.32
     * (group 1·Lv90), but early on {@code EnemyFactory} forgot to write it to the sheet, so the enemy's hit rate was
     * always 0 (review report M-5).
     */
    @Test
    public void enemyEffectHitRateReachesThePanel() {
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);

        Assertions.assertEquals(0.32, iceEdge.getAttribute(AttributeType.EFFECT_HIT_RATE).get(), 1e-9,
                "group 1·Lv90 effect hit rate = 0.32");
        Assertions.assertEquals(0.3, iceEdge.getAttribute(AttributeType.EFFECT_RESISTANCE).get(), 1e-9,
                "effect resistance is additive: template 0.2 + level group 0.1 (P2-3)");
        // when the enemy is the caster, the 0.32 hit rate really does raise the chance
        Character victim = Character.fromAttributes("victim", 10_000, 100, 100, 100);
        Assertions.assertEquals(1.0, newBattle(iceEdge, victim).hitChance(iceEdge, victim, 1.0, null), EPS);
        Assertions.assertEquals(0.66, newBattle(iceEdge, victim).hitChance(iceEdge, victim, 0.5, null), 1e-9,
                "0.5 × 1.32 = 0.66");
    }

    /**
     * {@code tryApplyDebuff}: the debuff is only attached when the roll lands; it is not attached when it is fully
     * immune.
     */
    @Test
    public void tryApplyDebuffGatesOnTheRoll() {
        // guaranteed to land: hit 0, resist 0, base 1.0 → chance 1.0 (rng.nextDouble() < 1 is always true)
        Character caster = caster(0, 0);
        Enemy target = enemyWithResist(0, null);
        Battle battle = newBattle(caster, target);

        Assertions.assertTrue(battle.tryApplyDebuff(caster, target, new StunBuff(2), 1.0, null),
                "chance 1.0 → always attached");
        Assertions.assertTrue(target.getBuffManager().hasBuff(StunBuff.class));

        // guaranteed to fail: 冰锋 is immune to freeze → chance 0.0
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);
        Battle battle2 = newBattle(caster, iceEdge);
        Assertions.assertFalse(battle2.tryApplyDebuff(caster, iceEdge, new StunBuff(2), 1.0, "STAT_CTRL_Frozen"),
                "chance 0.0 → cannot be attached");
        Assertions.assertFalse(iceEdge.getBuffManager().hasBuff(StunBuff.class));
    }

    @Test
    public void rollIsReproducibleWithASeed() {
        Character caster = caster(0, 0);
        Enemy target = enemyWithResist(0.5, null);

        // the same seed → the same sequence of results
        StringBuilder a = new StringBuilder();
        StringBuilder b = new StringBuilder();
        Battle first = new Battle(List.of(caster), List.of(target), new Random(7));
        Battle second = new Battle(List.of(caster), List.of(target), new Random(7));
        for (int i = 0; i < 32; i++) {
            a.append(first.rollDebuff(caster, target, 0.5, null) ? '1' : '0');
            b.append(second.rollDebuff(caster, target, 0.5, null) ? '1' : '0');
        }
        Assertions.assertEquals(a.toString(), b.toString(), "injecting a Random with the same seed → reproducible");
        Assertions.assertTrue(a.toString().contains("1") && a.toString().contains("0"),
                "at a 0.5 chance both outcomes should appear within 32 rolls");
    }

    // ==================================================================

    private static Character caster(double hitRate, double resist) {
        Character c = Character.fromAttributes("caster", 10_000, 100, 100, 100);
        c.setAttribute(AttributeType.EFFECT_HIT_RATE, new DoubleValue(hitRate));
        c.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(resist));
        return c;
    }

    /** A hand-made enemy: only effect resistance and (optionally) specific resistance are set. */
    private static Enemy enemyWithResist(double resist, String specificKey) {
        AttributeBuilder builder = new AttributeBuilder();
        builder.setBase(AttributeType.HEALTH, 10_000)
                .setBase(AttributeType.DEFENCE, 100)
                .setBase(AttributeType.ATTACK, 100)
                .setBase(AttributeType.SPEED, 100)
                .setBase(AttributeType.EFFECT_RESISTANCE, resist);
        Enemy enemy = new Enemy("dummy", builder.build());
        if (specificKey != null) {
            enemy.setDebuffResist(java.util.Map.of(specificKey, 1.0));
        }
        return enemy;
    }

    private static Battle newBattle(CanHit... combatants) {
        List<Character> characters = new java.util.ArrayList<>();
        List<Enemy> enemies = new java.util.ArrayList<>();
        for (CanHit c : combatants) {
            if (c.getCamp() == Camp.PLAYER) {
                characters.add((Character) c);
            } else {
                enemies.add((Enemy) c);
            }
        }
        if (characters.isEmpty()) {
            characters.add(Character.fromAttributes("filler", 1000, 100, 100, 100));
        }
        if (enemies.isEmpty()) {
            enemies.add(enemyWithResist(0, null));
        }
        return new Battle(characters, enemies, new Random(20260919));
    }
}
