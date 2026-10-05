package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A rule-authored damage instance with a LITERAL ratio (2026-09-29, round 186): Sushang's technique deals 80% of her ATK.
 *
 * <p>The capability's contract is that the stated `percent` is applied to the settled attribute, linearly and exactly once. That is what this test asserts: two
 * literal rules at 0.8 and 0.4 must differ by a factor of two.
 *
 * <p>Asserting the absolute number against a row-based instance turned out impossible to do honestly: measured, a row-based DAMAGE at her Lv10 COMMON row (where
 * the document says 100%) came out 1.4x the plain settled ATK, so that table row's parameter carries a different semantic than "the ATK ratio". That question is
 * registered in GAPS.md instead of being papered over with a magic constant.
 */
public class LiteralDamageTest {
    private static final int SUSHANG = 1206;
    private static final int LEVEL = 80;
    private static final double HEAVY = 0.8;
    private static final double LIGHT = 0.4;

    /** ⚠ The stated percent is applied linearly: half the percent, half the damage. */
    @Test
    public void theStatedPercentIsAppliedLinearly() {
        double heavy = openingDamage(HEAVY);
        double light = openingDamage(LIGHT);

        Assertions.assertTrue(heavy > 0, "the instance must deal damage at all");
        Assertions.assertEquals(2.0, heavy / light, 0.05,
                "percent " + HEAVY + " vs " + LIGHT + " must differ by exactly two: heavy " + heavy + ", light " + light);
    }

    /** ⚠ The control: with no technique declared, the opening rule does not fire at all. */
    @Test
    public void withoutTheTechniqueNoOpeningDamage() {
        Character tb = CharacterFactory.create(SUSHANG, LEVEL);
        Enemy enemy = Enemy.fromAttributes("Dummy", 60000, 500, 100, 90);
        Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
        double before = enemy.getCurrentHp();
        battle.startBattle();

        Assertions.assertEquals(before, enemy.getCurrentHp(), 1e-9,
                "「使用秘技后」 -- undeclared, so no damage");
    }

    /** Fires one literal-ratio DAMAGE at the given percent and returns what it cost the enemy. */
    private static double openingDamage(double percent) {
        Character hero = CharacterFactory.create(SUSHANG, LEVEL);
        Enemy enemy = Enemy.fromAttributes("Dummy", 60000, 500, 100, 90);
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.markTechniqueUsed(hero);
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "element", "Physical");
        TriggerSpecs.set(effect, "target", "all_enemies");
        hero.setTriggerTable(new TriggerTable(SUSHANG, List.of(TriggerSpecs.rule(
                TriggerEvent.BATTLE_START.name(), List.of(), effect))));
        double before = enemy.getCurrentHp();
        battle.startBattle();
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 1.0;
            }
        };
    }
}
