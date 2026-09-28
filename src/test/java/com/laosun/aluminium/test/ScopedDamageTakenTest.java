package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.VulnerabilityBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A <b>scoped</b> taken-side modifier (2026-09-28): {@code MODIFY_DAMAGE_TAKEN} with {@code damage_type}.
 *
 * <p>Reader: shipped 1301 Gallagher's talent 「【酩酊】使目标受到的<b>击破伤害</b>提高 12.00%」. The trap on the other side of the same
 * word is {@code BREAKING_EFFECT} ("how hard <b>I</b> break"), which is why this is a *taken-side* zone with a type filter rather
 * than an attribute.
 *
 * <p>⚠ <b>Every assertion is a ratio</b>, and that is the second version of this file: the first compared absolute numbers against
 * 1000 and read 1836, because the attacker's own DMG boosts ride along (the same reason {@code TriggerDamageTakenTest} asserts a
 * ratio). The vulnerability zone is multiplicative, so "×1.5 on break, ×1.0 on everything else" is what is being pinned, and the
 * unrelated boosts cancel out of both sides.
 */
public class ScopedDamageTakenTest {
    /** Himeko: no shipped rule file, so the table under test is the only one. */
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final double BASE = 1000;
    private static final double EPS = 1e-6;

    /** ⚠ A break-scoped vulnerability raises break damage by exactly its ratio and leaves other kinds untouched. */
    @Test
    public void aScopedVulnerabilityOnlyTouchesItsOwnType() {
        double plainBreak = settle(DamageType.BREAK);
        double plainNormal = settle(DamageType.NORMAL);

        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5, false, DamageType.BREAK));

        Assertions.assertEquals(plainBreak * 1.5, settle(enemy, DamageType.BREAK), EPS,
                "「使目标受到的**击破伤害**提高 50%」 -- the scoped kind is raised by the ratio");
        Assertions.assertEquals(plainNormal, settle(enemy, DamageType.NORMAL), EPS,
                "⚠ …and an ordinary hit is untouched: that is the whole point of the scope (50% of everything would be a wrong "
                        + "number that still looks plausible)");
    }

    /** ⚠ No scope stated still means every type, which is what every earlier file relies on. */
    @Test
    public void anUnscopedVulnerabilityStillRaisesEveryType() {
        double plainBreak = settle(DamageType.BREAK);
        double plainNormal = settle(DamageType.NORMAL);

        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));

        Assertions.assertEquals(plainNormal * 1.5, settle(enemy, DamageType.NORMAL), EPS, "unscoped = all kinds");
        Assertions.assertEquals(plainBreak * 1.5, settle(enemy, DamageType.BREAK), EPS, "…including break");
    }

    /** ⚠ A misspelled type is refused when the rule is compiled rather than silently meaning "all kinds". */
    @Test
    public void aMisspelledTypeIsRefused() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_DAMAGE_TAKEN");
        TriggerSpecs.set(effect, "percent", 0.12);
        TriggerSpecs.set(effect, "turns", 2);
        TriggerSpecs.set(effect, "target", "target");
        TriggerSpecs.set(effect, "damageType", "BREK");
        TriggerSpec wrong = TriggerSpecs.rule("KILL", List.of("actor == self"), effect);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(wrong)));
        Assertions.assertTrue(refused.getMessage().contains("BREK"), refused.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double settle(DamageType type) {
        return settle(enemy(), type);
    }

    private static double settle(Enemy defender, DamageType type) {
        Character attacker = CharacterFactory.create(OWNER, LEVEL);
        Damage damage = new Damage(attacker, defender, DamageElement.FIRE, type, BASE);
        return new Battle(List.of(attacker), List.of(defender), new Random(0)).applyDamage(defender, damage);
    }

    private static Enemy enemy() {
        return Enemy.fromAttributes("enemy", 1000000, 0, 100, 100);
    }
}
