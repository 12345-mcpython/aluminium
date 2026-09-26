package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.buff.ReductionBuff;
import com.laosun.aluminium.models.buff.VulnerabilityBuff;
import com.laosun.aluminium.models.event.DamageEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P1-7 acceptance: buffs inject zones through {@code DamageEvent} during settlement.
 *
 * <p>Every defender here has DEFENCE = 0 and the attacker has no boost / crit attributes,
 * so a base of 1000 isolates the hooked zone.
 */
public class DamageHookTest {
    private static final double EPS = 1e-6;
    private static final double BASE = 1000.0;

    private static Character attacker() {
        return Character.fromAttributes("attacker", 1000, 100, 100, 100);
    }

    private static Enemy enemy() {
        return Enemy.fromAttributes("enemy", 100000, 0, 100, 100);
    }

    private static double settle(Character attacker, Enemy defender) {
        return settle(attacker, defender, DamageType.NORMAL);
    }

    private static double settle(Character attacker, Enemy defender, DamageType type) {
        Damage damage = new Damage(attacker, defender, DamageElement.FIRE, type, BASE);
        return new Battle(List.of(attacker), List.of(defender), new Random(0))
                .applyDamage(defender, damage);
    }

    @Test
    public void vulnerabilityRaisesIncomingDamage() {
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));

        Assertions.assertEquals(1500, settle(attacker(), enemy), EPS);
    }

    @Test
    public void lateBuffDoesNotTickOnBeforeMove() {
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));

        enemy.getBuffManager().beforeMove();     // a late buff: beforeMove does not touch it

        Assertions.assertEquals(1500, settle(attacker(), enemy), EPS);
    }

    @Test
    public void vulnerabilityExpiresAfterItsDuration() {
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));
        Assertions.assertEquals(1500, settle(attacker(), enemy), EPS);

        enemy.getBuffManager().afterMove();
        enemy.getBuffManager().afterMove();      // duration 2 → 0; on expiry the modifier is removed

        Assertions.assertEquals(1000, settle(attacker(), enemy), EPS);
    }

    @Test
    public void reductionLowersIncomingDamage() {
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new ReductionBuff(2, 0.3));

        Assertions.assertEquals(700, settle(attacker(), enemy), EPS);
    }

    @Test
    public void sameKindBuffReplacesInsteadOfStacking() {
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.9));

        Assertions.assertEquals(1900, settle(attacker(), enemy), EPS);
    }

    @Test
    public void hooksAlsoApplyToBreakDamage() {
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));

        // break gets no DMG boost / crit stats (blocked by BoostArea / CritArea's applies()), but it does get vulnerability — P4 reuses this
        Assertions.assertEquals(1500, settle(attacker(), enemy, DamageType.BREAK), EPS);
    }

    @Test
    public void attackerSideWeaknessFeedsTheWeaknessZone() {
        // weakness is an "attacker-side debuff" (HSR.md §2.2) → the hook must iterate the attacker side too
        Character attacker = attacker();
        attacker.getBuffManager().addBuff(new WeaknessBuff(2, 0.4));

        Assertions.assertEquals(600, settle(attacker, enemy()), EPS);
    }

    // ==================================================================
    // C-1: a buff that injects a damage zone must look only at which side it stands on
    //   Battle.assemble broadcasts DamageEvent to both the attacking and defending sides, so "which
    //   side it is attached to" must be judged by the buff itself; otherwise vulnerability attached
    //   to an enemy would also raise its own output, and reduction attached to a character would
    //   weaken its own output.
    // ==================================================================

    @Test
    public void vulnerabilityOnTheAttackerDoesNotBoostItsOwnAttacks() {
        Character attacker = attacker();
        attacker.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));

        // the defending side has no vulnerability → this hit is a clean 1000 (vulnerability only counts for "the hit I take")
        Assertions.assertEquals(1000, settle(attacker, enemy()), EPS);
    }

    @Test
    public void vulnerabilityStillAppliesWhenItsOwnerIsTheDefender() {
        // paired with the previous case: attached to the right side it must still take effect (guards against a fake fix that just disables everything)
        Character attacker = attacker();
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));

        Assertions.assertEquals(1500, settle(attacker, enemy), EPS);
    }

    @Test
    public void reductionOnTheAttackerDoesNotWeakenItsOwnAttacks() {
        Character attacker = attacker();
        attacker.getBuffManager().addBuff(new ReductionBuff(2, 0.3));

        Assertions.assertEquals(1000, settle(attacker, enemy()), EPS, "reduction only blocks 「the hit I take」");
    }

    @Test
    public void reductionStillAppliesWhenItsOwnerIsTheDefender() {
        Character attacker = attacker();
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new ReductionBuff(2, 0.3));

        Assertions.assertEquals(700, settle(attacker, enemy), EPS);
    }

    /**
     * An attacker-side debuff buff embedded in the test: proving that {@code DamageEvent} is also
     * triggered on the attacker side.
     * (The production WeaknessBuff is left to P10-3 to do uniformly.)
     */
    private static class WeaknessBuff extends AbstractBuff implements DamageEvent {
        private final double ratio;

        private WeaknessBuff(int duration, double ratio) {
            super(duration, false);
            this.ratio = ratio;
        }

        @Override
        public boolean canAct() {
            return true;
        }

        @Override
        public void applyEffect(CanHit target) {
        }

        @Override
        public void removeBuff(CanHit target) {
        }

        @Override
        public void tickEffect(CanHit target) {
            decreaseDuration();
        }

        @Override
        public void onDamage(Battle battle, Damage damage) {
            damage.addWeakness(ratio, ModifierSource.DEBUFF, id);
        }
    }
}
