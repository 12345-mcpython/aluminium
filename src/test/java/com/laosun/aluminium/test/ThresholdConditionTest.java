package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * The numeric comparison conditions, which EXIST (TriggerTable's javadoc documents {@code hp_percent} as a fraction and shows the {@code <= 50%} shape).
 *
 * <p>Round 214 registered them as a missing capability; reading the source overturned that. These cases are the evidence, and they pin the two things a note cannot: that {@code <=}
 * is inclusive, and that {@code hp_percent} is a FRACTION.
 */
public class ThresholdConditionTest {
    private static final int OWNER = 1224;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** ⚠ `<=` is INCLUSIVE: the boundary value fires. */
    @Test
    public void lessOrEqualFiresOnTheBoundary() {
        Assertions.assertEquals(1, fires("hit_count <= 0", 0), "at the boundary, `<=` must fire");
        Assertions.assertEquals(0, fires("hit_count <= 0", 1), "one above the boundary, it must not");
    }

    /** ⚠ `>=` is inclusive too, and `>` is not. */
    @Test
    public void greaterComparisonsDifferAtTheBoundary() {
        Assertions.assertEquals(0, fires("hit_count >= 2", 1), "one below");
        Assertions.assertEquals(1, fires("hit_count >= 2", 2), "at the boundary, `>=` must fire");
        Assertions.assertEquals(0, fires("hit_count > 2", 2), "at the boundary, `>` must not");
        Assertions.assertEquals(1, fires("hit_count > 2", 3), "one above");
    }

    /** ⚠ `hp_percent` is a FRACTION, probed with a threshold STRICTER than the fraction can reach. */
    @Test
    public void hpPercentIsAFractionNotAPercentage() {
        Assertions.assertEquals(0, firesAfterDamage("hp_percent <= 0.5", false),
                "at full HP the half-HP gate must not fire");
        Assertions.assertEquals(1, firesAfterDamage("hp_percent <= 0.5", true),
                "below half HP it must fire");
        // ⚠ A LOOSER threshold proves nothing (0.1 is <= 50 as well as <= 0.5), so the units are pinned from the strict side:
        // at full HP the fraction 1.0 is >= 0.5 but never >= 50.
        Assertions.assertEquals(1, firesAtFullHp("hp_percent >= 0.5"),
                "the fraction reading: 1.0 >= 0.5 at full HP");
        Assertions.assertEquals(0, firesAtFullHp("hp_percent >= 50"),
                "⚠ the percentage reading would make this >= 50 with 1.0, so it must NEVER fire");
    }

    /** Fires once at FULL HP, so "at or above half" and "at or above 50" can be told apart. */
    private static int firesAtFullHp(String condition) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule(
                TriggerEvent.ALLY_ATTACK.name(), List.of(condition), energy()))));
        Battle battle = new Battle(List.of(owner), List.of(enemy), fixed());
        battle.startBattle();
        return battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, enemy, 0, 0);
    }

    /** Fires an event with a chosen hit count and returns how many rules burned. */
    private static int fires(String condition, int hitCount) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule(
                TriggerEvent.ALLY_ATTACK.name(), List.of(condition), energy()))));
        Battle battle = new Battle(List.of(owner), List.of(enemy), fixed());
        battle.startBattle();
        return battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, enemy, hitCount, 0);
    }

    /** Fires once at full HP and once after heavy damage, returning the count for the damaged case. */
    private static int firesAfterDamage(String condition, boolean damageFirst) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule(
                TriggerEvent.ALLY_ATTACK.name(), List.of(condition), energy()))));
        Battle battle = new Battle(List.of(owner), List.of(enemy), fixed());
        battle.startBattle();
        if (damageFirst) {
            battle.applyDamage(owner, new Damage(enemy, owner, com.laosun.aluminium.enums.DamageElement.PHYSICAL,
                    com.laosun.aluminium.enums.DamageType.NORMAL, owner.getMaxHp() * 0.9));
        }
        return battle.fireTriggers(TriggerEvent.ALLY_ATTACK, owner, enemy, 0, 0);
    }

    private static EffectSpec energy() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_ENERGY");
        TriggerSpecs.set(effect, "amount", 1.0);
        return effect;
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
