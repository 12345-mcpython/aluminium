package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code BOOST_TOUGHNESS} (2026-09-28): "使本次攻击的<b>削韧值</b>提高 100%".
 *
 * <p><b>Where it is read.</b> Not inside {@code Battle.reduceToughness} - that receives "the nominal reduction of this
 * instance" and is also called by enemy skills and by the demo script. The one place that turns a nominal reduction into a
 * settled one is {@code SkillExecutor.applyStanceDamage}, which runs <b>after</b> the damage ({@code DEALING_DAMAGE} fires
 * inside {@code applyDamage}) and <b>before</b> the bar moves - so a rule on {@code DEALING_DAMAGE} can grant the boost in
 * time.
 *
 * <p><b>Why two harness details matter here</b> (both learned the hard way): the attack must be a <b>real skill object</b>
 * that carries a toughness value (a hand-built {@code DefaultSkill} carries none, so an earlier version measured 0), and the
 * enemy must be one the engine itself says is <b>weak to the element</b> - an attack reduces nothing against a non-weakness.
 */
public class ToughnessBoostTest {
    private static final int CID = 1207;
    private static final int LEVEL = 80;

    /** The same attack reduces twice as much toughness with "提高 100%" up. */
    @Test
    public void theBoostMultipliesTheReduction() {
        double plain = stanceAfterAttack(-1);
        double boosted = stanceAfterAttack(1.0);

        Assertions.assertTrue(plain > 0, "precondition: the attack reduces toughness at all");
        Assertions.assertEquals(plain * 2, boosted, 1e-6,
                "「削韧值提高100%」: the reduction doubles (the attack's own value is the 100% baseline)");
    }

    /** Half of it, for the "提高 50%" half of the family. */
    @Test
    public void aPartialBoostIsProportional() {
        double plain = stanceAfterAttack(-1);
        Assertions.assertEquals(plain * 1.5, stanceAfterAttack(0.5), 1e-6, "「提高50%」 is half of the doubling");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** Settles one attack and reports how much toughness it took off ({@code boost < 0} = no boost rule). */
    private static double stanceAfterAttack(double boost) {
        Character hero = CharacterFactory.create(CID, LEVEL);
        Enemy enemy = enemyWeakToImaginary();

        List<TriggerSpec> specs = new java.util.ArrayList<>();
        if (boost >= 0) {
            EffectSpec boostEffect = new EffectSpec();
            TriggerSpecs.set(boostEffect, "op", "BOOST_TOUGHNESS");
            TriggerSpecs.set(boostEffect, "percent", boost);
            TriggerSpecs.set(boostEffect, "turns", 1);
            TriggerSpecs.set(boostEffect, "target", "self");
            specs.add(TriggerSpecs.rule("CAST_SETUP", null, boostEffect));
        }
        hero.setTriggerTable(new TriggerTable(CID, specs));

        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.startBattle();
        double before = enemy.getStance();
        battle.castImmediate(hero.getSkills().get(SkillType.COMMON), hero, List.of(enemy));
        return before - enemy.getStance();
    }

    /**
     * Note: The enemy is chosen by <b>asking it</b> what it is weak to, not by trusting a hard-coded id: an attack reduces
     * toughness only against a weakness ({@code Battle.reduceToughness} returns {@code NONE} otherwise).
     * {@code getStanceWeak()} is the same API the mechanics demo prints "Weakness [...]" from.
     */
    private static Enemy enemyWeakToImaginary() {
        for (int id = 1002010; id < 1002060; id++) {
            try {
                Enemy candidate = EnemyFactory.create(id, 90, 1);
                if (String.valueOf(candidate.getStanceWeak()).contains("IMAGINARY")) {
                    return candidate;
                }
            } catch (RuntimeException ignored) {
                // that id is not in the data; keep looking
            }
        }
        throw new AssertionError("no Imaginary-weak monster found in the probed id range");
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
