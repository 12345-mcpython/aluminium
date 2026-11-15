package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.DoubleValue.Modifier.ModifierSource;
import com.laosun.aluminium.buff.AbstractBuff;
import com.laosun.aluminium.buff.ReductionBuff;
import com.laosun.aluminium.buff.VulnerabilityBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.event.DamageEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.DoubleValue;

/**
 * -acceptance: buffs inject zones through {@code DamageEvent} during settlement.
 *
 * <p>Every defender here has DEFENCE = 0 and the attacker has no boost / crit attributes,
 * so a base of 1000 isolates the hooked zone.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the scenarios below, one {@code assertAll} per
 * fixture/clause group so a failing clause still names itself. Every case stays a positive settlement
 * check; there is no rejection path in this class, so nothing here needs its own exception test.
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
        return new Battle(List.of(attacker), List.of(defender), BattleFixture.seeded())
                .applyDamage(defender, damage);
    }

    // ==================================================================
    // 1. Vulnerability injects its zone, and its duration is a lifetime, not a turn counter
    // ==================================================================

    /**
     * One fixture ({@code VulnerabilityBuff(2, 0.5)} on the defender) and one observable (the settled
     * damage): the zone raises the hit, a buff added after the move boundary is not ticked by
     * {@code beforeMove}, and once its duration runs out the modifier is removed again.
     */
    @Test
    public void vulnerabilityLifecycleScalesIncomingDamage() {
        Enemy raisedEnemy = enemy();
        raisedEnemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));
        double raised = settle(attacker(), raisedEnemy);

        Enemy lateEnemy = enemy();
        lateEnemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));
        lateEnemy.getBuffManager().beforeMove();     // a late buff: beforeMove does not touch it
        double afterBeforeMove = settle(attacker(), lateEnemy);

        Enemy expiringEnemy = enemy();
        expiringEnemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));
        double whileActive = settle(attacker(), expiringEnemy);
        expiringEnemy.getBuffManager().afterMove();
        expiringEnemy.getBuffManager().afterMove();      // duration 2 to 0; on expiry the modifier is removed
        double afterExpiry = settle(attacker(), expiringEnemy);

        Assertions.assertAll(
                () -> Assertions.assertEquals(1500, raised, EPS, "vulnerability raises incoming damage"),
                () -> Assertions.assertEquals(1500, afterBeforeMove, EPS,
                        "a late buff is not ticked by beforeMove"),
                () -> Assertions.assertEquals(1500, whileActive, EPS,
                        "vulnerability is active while its duration lasts"),
                () -> Assertions.assertEquals(1000, afterExpiry, EPS,
                        "vulnerability expires after its duration"));
    }

    @Test
    public void sameKindBuffReplacesInsteadOfStacking() {
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.9));

        Assertions.assertEquals(1900, settle(attacker(), enemy), EPS);
    }

    // ==================================================================
    // C-1: a buff that injects a damage zone must look only at which side it stands on
    //   Battle.assemble broadcasts DamageEvent to both the attacking and defending sides, so "which
    //   side it is attached to" must be judged by the buff itself; otherwise vulnerability attached
    //   to an enemy would also raise its own output, and reduction attached to a character would
    //   weaken its own output.
    // ==================================================================

    @Test
    public void vulnerabilityZoneRespectsTheOwningSide() {
        // attached to the attacker it must not boost that same character's own output
        Character attackerOwned = attacker();
        attackerOwned.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));
        // the defending side has no vulnerability, so this hit is a clean 1000 (vulnerability only counts for "the hit I take")
        double onAttacker = settle(attackerOwned, enemy());

        // paired with the previous case: attached to the right side it must still take effect
        // (guards against a fake fix that just disables everything)
        Character attacker = attacker();
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));
        double onDefender = settle(attacker, enemy);

        Assertions.assertAll(
                () -> Assertions.assertEquals(1000, onAttacker, EPS,
                        "vulnerability on the attacker does not boost its own attacks"),
                () -> Assertions.assertEquals(1500, onDefender, EPS,
                        "vulnerability still applies when its owner is the defender"));
    }

    @Test
    public void reductionZoneOnlyAppliesToTheHitItsOwnerTakes() {
        // attached to the defender it lowers the incoming hit
        Enemy loweredEnemy = enemy();
        loweredEnemy.getBuffManager().addBuff(new ReductionBuff(2, 0.3));
        double onDefender = settle(attacker(), loweredEnemy);

        // attached to the attacker it must not weaken its own output
        Character attackerOwned = attacker();
        attackerOwned.getBuffManager().addBuff(new ReductionBuff(2, 0.3));
        double onAttacker = settle(attackerOwned, enemy());

        // paired with the previous case: the defending side is still the side it belongs to
        Character attacker = attacker();
        Enemy defenderOwnedEnemy = enemy();
        defenderOwnedEnemy.getBuffManager().addBuff(new ReductionBuff(2, 0.3));
        double onOwnerDefender = settle(attacker, defenderOwnedEnemy);

        Assertions.assertAll(
                () -> Assertions.assertEquals(700, onDefender, EPS, "reduction lowers incoming damage"),
                () -> Assertions.assertEquals(1000, onAttacker, EPS,
                        "reduction only blocks 「the hit I take」"),
                () -> Assertions.assertEquals(700, onOwnerDefender, EPS,
                        "reduction still applies when its owner is the defender"));
    }

    @Test
    public void hooksAlsoApplyToBreakDamage() {
        Enemy enemy = enemy();
        enemy.getBuffManager().addBuff(new VulnerabilityBuff(2, 0.5));

        // break gets no DMG boost / crit stats (blocked by BoostArea / CritArea's applies()), but it does get vulnerability - the toughness-reduction step reuses this
        Assertions.assertEquals(1500, settle(attacker(), enemy, DamageType.BREAK), EPS);
    }

    @Test
    public void attackerSideWeaknessFeedsTheWeaknessZone() {
        // weakness is an "attacker-side debuff" (HSR.md §2.2) to the hook must iterate the attacker side too
        Character attacker = attacker();
        attacker.getBuffManager().addBuff(new WeaknessBuff(2, 0.4));

        Assertions.assertEquals(600, settle(attacker, enemy()), EPS);
    }

    /**
     * An attacker-side debuff buff embedded in the test: proving that {@code DamageEvent} is also
     * triggered on the attacker side.
     * (The production WeaknessBuff is left to do the same thing uniformly.)
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
