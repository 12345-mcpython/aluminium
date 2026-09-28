package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A <b>stated</b> crit on a damage instance: 「该伤害暴击率固定为100%，暴击伤害固定为150%」 (知更鸟's 【协奏】 addendum).
 *
 * <p><b>The gap it closes.</b> {@code Damage.fixedCrit(boolean, double)} has existed for a while — the crit zone skips
 * the roll for an instance that already knows its outcome — but <b>no op could state it</b>, so the sentence could not
 * be written at all. This is the content spelling: {@code crit_rate: 1} + {@code crit_damage: 1.5} on a {@code DAMAGE}.
 *
 * <p>⚠ The cases pin the two halves that a careless spelling would get wrong: the instance really is forced to crit
 * (with a fixed RNG that would never crit on its own), and the stated crit damage is used <b>instead of</b> the
 * attacker's own crit damage stat.
 */
public class FixedCritTest {
    private static final double EPS = 1e-6;

    private static final int MARCH = 1001;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** With {@code crit_rate: 1} the instance crits even though the RNG never would. */
    @Test
    public void theInstanceCritsAlthoughTheRollWouldNot() {
        double normal = damage(null, null);
        double fixed = damage(1.0, 1.5);

        Assertions.assertTrue(fixed > normal,
                "a stated crit must beat an instance that (with this RNG) does not crit: " + normal + " vs " + fixed);
        // ⚠ The engine states crit damage the way the panel does — as the STAT (1.5 = 150%), and the crit zone
        // multiplies by (1 + stat). So a stated 150% shows up as 2.5×, exactly like a panel crit damage of 0.5 shows
        // up as 1.5×. Reading the field as "the multiplier" would have been a silent 1.5 vs 2.5 disagreement.
        Assertions.assertEquals(1 + 1.5, fixed / normal, 1e-3,
                "…and by exactly the stated crit damage (150% means ×2.5 in this engine's convention)");
    }

    /**
     * The stated crit damage is what applies, and the panel's own {@code CRIT_ATTACK} is <b>not</b> it.
     *
     * <p>⚠ The precondition is the evidence: her base crit damage is 0.5 (so the case cannot pass by accident), while
     * the rule states 1.5 — and the measured ratio must be 1.5.
     */
    @Test
    public void theStatedCritDamageWinsOverTheStat() {
        Character hero = CharacterFactory.create(MARCH, LEVEL);
        double stat = hero.getAttribute(AttributeType.CRIT_ATTACK).get();
        Assertions.assertNotEquals(1.5, stat, 1e-9,
                "precondition: the panel's crit damage is NOT the stated number, so the ratio below cannot be a "
                        + "coincidence");

        double normal = damage(null, null);
        double fixed = damage(1.0, 1.5);

        Assertions.assertEquals(1 + 1.5, fixed / normal, 1e-3,
                "the sentence states 150%, so the panel's own crit damage (" + stat
                        + ") is not what it uses (×2.5, not ×1.5)");
    }

    /** The shape is refused at load: half a pair, a rate that is not 1.0, a non-positive crit damage. */
    @Test
    public void theShapeIsRefusedAtLoad() {
        Assertions.assertThrows(IllegalArgumentException.class, () -> table(rule(1.0, null)),
                "a crit damage without a rate says which number to use for a roll nobody described");
        Assertions.assertThrows(IllegalArgumentException.class, () -> table(rule(null, 1.5)), "…and the other half");
        Assertions.assertThrows(IllegalArgumentException.class, () -> table(rule(0.5, 1.5)),
                "a probabilistic rate is the CRIT_CHANCE attribute, not this");
        Assertions.assertThrows(IllegalArgumentException.class, () -> table(rule(1.0, 0.0)), "not a crit damage");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** A rule-driven attack from her ultimate's row, settled, measured as the enemy's HP loss. */
    private static double damage(Double critRate, Double critDamage) {
        Character hero = CharacterFactory.create(MARCH, LEVEL);
        hero.setTriggerTable(new TriggerTable(MARCH, List.of(rule(critRate, critDamage))));
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        enemy.setAttribute(AttributeType.EFFECT_RESISTANCE, new DoubleValue(0));
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.startBattle();

        double before = enemy.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, hero, enemy, 0, 0);
        return before - enemy.getCurrentHp();
    }

    private static TriggerSpec rule(Double critRate, Double critDamage) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "DAMAGE");
        TriggerSpecs.set(effect, "skill", "ULTRA");
        TriggerSpecs.set(effect, "damageParam", 0);
        TriggerSpecs.set(effect, "critRate", critRate);
        TriggerSpecs.set(effect, "critDamage", critDamage);
        TriggerSpecs.set(effect, "target", "target");
        return TriggerSpecs.rule(TriggerEvent.ALLY_ATTACK.value(), null, effect);
    }

    private static TriggerTable table(TriggerSpec rule) {
        return new TriggerTable(MARCH, List.of(rule));
    }

    /** An RNG that never rolls a crit on its own -- so anything that crits did so because it was told to. */
    private static Random fixed() {
        return new Random() {
            @Override
            public double nextDouble() {
                return 0.999;
            }
        };
    }
}
