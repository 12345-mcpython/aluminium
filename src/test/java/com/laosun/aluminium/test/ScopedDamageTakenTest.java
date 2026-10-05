package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
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
 * A <b>scoped, rolled</b> taken-side modifier (2026-09-28): {@code MODIFY_DAMAGE_TAKEN} with {@code damage_type} and
 * {@code base_chance}.
 *
 * <p>Readers: shipped 1301 Gallagher's talent "受到的击破伤害提高 12%", and 1108 Sampo's ultimate "有 100% 的基础概率使
 * 被攻击的敌方目标受到的持续伤害提高 30%". Note: `Battle.tickDots` settles DOT ticks as {@link DamageType#DOT}, which is what
 * makes the second one expressible - and the trap on the other side of those words is {@code DOT_DAMAGE_BOOST} ("how hard
 * <b>my</b> DOTs hit") versus this ("how hard DOTs hurt <b>me</b>").
 *
 * <p>Note: <b>Every assertion is a ratio</b>: the attacker's own DMG boosts ride along, so absolute numbers would read 1836
 * where 1000 was expected (the first version of this file did exactly that). The zones are multiplicative, so
 * " x 1.5 /  x 1.3 on the scoped kind,  x 1.0 on everything else" is what is pinned.
 */
public class ScopedDamageTakenTest {
    /** Himeko: no shipped rule file, so the table under test is the only one. */
    private static final int OWNER = 1003;
    private static final int SAMPO = 1108;
    private static final int LEVEL = 80;
    private static final double BASE = 1000;
    private static final double EPS = 1e-6;

    /** Note: A break-scoped vulnerability raises break damage and leaves every other kind alone. */
    @Test
    public void aScopedVulnerabilityOnlyTouchesItsOwnType() {
        double plainBreak = settle(DamageType.BREAK);
        double plainNormal = settle(DamageType.NORMAL);

        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5, false, DamageType.BREAK));

        Assertions.assertEquals(plainBreak * 1.5, settle(enemy, DamageType.BREAK), EPS,
                "「使目标受到的**击破伤害**提高 50%」 -- the scoped kind is raised by the ratio");
        Assertions.assertEquals(plainNormal, settle(enemy, DamageType.NORMAL), EPS,
                "⚠ …and an ordinary hit is untouched: that is the whole point of the scope");
    }

    /** Note: 1108's clause is the DOT kind, and DOT ticks are their own {@link DamageType}. */
    @Test
    public void theDotScopedVulnerabilityMatchesSampoClause() {
        double plainDot = settle(DamageType.DOT);
        double plainNormal = settle(DamageType.NORMAL);

        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.3, false, DamageType.DOT));

        Assertions.assertEquals(plainDot * 1.3, settle(enemy, DamageType.DOT), EPS,
                "「使目标**受到的持续伤害**提高 30%」 -- the DOT kind is raised by the ratio");
        Assertions.assertEquals(plainNormal, settle(enemy, DamageType.NORMAL), EPS,
                "⚠ …and ordinary damage is not: the mirror-image mistake is DOT_DAMAGE_BOOST, which is the dealer's side");
    }

    /** Note: No scope stated still means every type, which is what every earlier file relies on. */
    @Test
    public void anUnscopedVulnerabilityStillRaisesEveryType() {
        double plainBreak = settle(DamageType.BREAK);
        double plainNormal = settle(DamageType.NORMAL);

        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));

        Assertions.assertEquals(plainNormal * 1.5, settle(enemy, DamageType.NORMAL), EPS, "unscoped = all kinds");
        Assertions.assertEquals(plainBreak * 1.5, settle(enemy, DamageType.BREAK), EPS, "…including break");
    }

    /** Note: A misspelled type is refused when the rule is compiled rather than silently meaning "all kinds". */
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

    /** Note: Sampo's file carries the clause - and the fact that it LOADED is itself the spelling check. */
    @Test
    public void hisFileCarriesTheRolledDotVulnerability() {
        Assertions.assertEquals(1, TriggerTables.of(SAMPO).ruleCount(TriggerEvent.ULT_CAST),
                "his ultimate's debuff half is one rule; ⚠ the load itself validates every key, so a misspelled "
                        + "`damage_type` or `base_chance` would have thrown before this line ran");
    }

    /** Note: A stated chance is really ROLLED: a tiny one against a 0.5 draw leaves the victim with no zone at all. */
    @Test
    public void aStatedChanceOnAZoneIsRolled() {
        double plain = settle(DamageType.DOT);
        Assertions.assertEquals(plain, withZoneRolled(0.0001, 0.5), EPS, "「有 X% 的基础概率」 is a roll, not a label");
        Assertions.assertEquals(plain * 1.3, withZoneRolled(1.0, 0.0), EPS, "…and an easy draw lands it");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double settle(DamageType type) {
        return settle(enemy(), type);
    }

    /**
     * Settles a DOT-typed hit against a defender whose zone came from a <b>rule</b> carrying {@code base_chance}, with a
     * fixed draw - the only way to observe that the roll happens at all.
     */
    private static double withZoneRolled(double baseChance, double roll) {
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        Enemy defender = enemy();
        EffectSpec zone = new EffectSpec();
        TriggerSpecs.set(zone, "op", "MODIFY_DAMAGE_TAKEN");
        TriggerSpecs.set(zone, "percent", 0.3);
        TriggerSpecs.set(zone, "damageType", "DOT");
        TriggerSpecs.set(zone, "baseChance", baseChance);
        TriggerSpecs.set(zone, "turns", 2);
        TriggerSpecs.set(zone, "target", "target");
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(TriggerSpecs.rule("KILL", List.of("actor == self"), zone))));

        Battle battle = new Battle(List.of(owner), List.of(defender), new Random() {
            @Override
            public double nextDouble() {
                return roll;
            }
        });
        battle.fireTriggers(TriggerEvent.KILL, owner, defender, 0, 0);
        Damage damage = new Damage(owner, defender, DamageElement.FIRE, DamageType.DOT, BASE);
        return battle.applyDamage(defender, damage);
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
