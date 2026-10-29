package com.laosun.aluminium.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.damage.BattleDamage;
import com.laosun.aluminium.damage.BreakDamageCalculator;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.trigger.TriggerPayload;

/**
 * Toughness reduction and weakness break.
 *
 * <p><b>Why this is separate.</b> The rule has two halves that must not be reordered: only a weakness
 * reduces toughness, but an already-broken enemy turns the whole nominal reduction into super-break
 * input regardless of element. The break chain - fact, damage, delay, DOT, control, energy - is one
 * settlement, so it lives in one place.
 */
public final class BreakService {

    /**
     * Announces a weakness break to the event listeners.
     */
    @FunctionalInterface
    public interface BreakSink {
        /**
         * @param attacker who broke it
         * @param target   the broken enemy
         * @param element  the breaking element
         */
        void accept(CanHit attacker, CanHit target, DamageElement element);
    }

    private final Battle battle;
    private final DamageService damage;
    private final TriggerBus bus;
    private final BreakSink breakSink;

    /**
     * @param battle    the battle whose support services are used
     * @param damage    the settlement path
     * @param bus       the trigger bus
     * @param breakSink announces the break to listeners
     */
    public BreakService(Battle battle, DamageService damage, TriggerBus bus, BreakSink breakSink) {
        this.battle = battle;
        this.damage = damage;
        this.bus = bus;
        this.breakSink = breakSink;
    }

    /**
     * Reduces toughness and settles a break when the bar empties.
     *
     * @param attacker     the attacker
     * @param enemy        the target
     * @param element      the instance's element
     * @param stanceDamage the nominal toughness reduction
     * @param fromCast     the cast category, or {@code null}
     * @return the result
     */
    public Battle.StanceResult reduce(CanHit attacker, Enemy enemy, DamageElement element, double stanceDamage,
                                      SkillCategory fromCast) {
        if (attacker == null || enemy == null || stanceDamage <= 0) {
            return Battle.StanceResult.NONE;
        }
        if (enemy.isDeath()) {
            return Battle.StanceResult.NONE;
        }
        if (enemy.isBroken() || !enemy.hasToughnessBar()) {
            return new Battle.StanceResult(0, stanceDamage, 0, false);
        }
        if (!enemy.isWeakTo(element)) {
            return Battle.StanceResult.NONE;
        }
        double consumed = enemy.reduceStance(stanceDamage);
        double overkill = stanceDamage - consumed;
        if (enemy.getStance() > 0) {
            return new Battle.StanceResult(consumed, 0, 0, false);
        }
        enemy.breakEnemy(element);
        enemy.setBrokenRemainTurns(Constant.BROKEN_REMAIN_TURNS);
        breakSink.accept(attacker, enemy, element);
        battle.announceBroken(attacker, enemy, element);
        bus.fire(TriggerPayload.Subject.of(TriggerEvent.BREAK, attacker, enemy, 0, fromCast));
        Damage breakHit = BreakDamageCalculator.build(attacker, enemy, element, consumed);
        breakHit.setCausedByCast(fromCast);
        double breakDamage = damage.applyDamage(enemy, breakHit, DamageService.EnergyGrant.KILL_ONLY);
        delayMovePercent(enemy, Constant.BREAK_DELAY_RATIO);
        BattleDamage.attachBreakDot(battle, attacker, enemy, element);
        BattleDamage.attachBreakControl(battle, enemy, element);
        battle.gainBreakEnergy(attacker, enemy);
        return new Battle.StanceResult(consumed, overkill, breakDamage, true);
    }

    /**
     * Delays an action by a percentage of its period ({@code 10000 / speed}).
     *
     * @param target  the unit
     * @param percent the delay ratio
     * @return whether it was in the action bar and was delayed
     */
    public boolean delayMovePercent(CanHit target, double percent) {
        if (target == null || percent <= 0) {
            return false;
        }
        double speed = target.getAttribute(AttributeType.SPEED).get();
        if (speed <= 0) {
            return false;
        }
        return battle.queue().delayAction(target, 10000.0 / speed * percent);
    }

    /**
     * Counts down a broken enemy's remaining turns and recovers at zero.
     *
     * @param enemy the enemy whose turn it is
     * @return whether this turn is skipped
     */
    public boolean handleBrokenTurn(Enemy enemy) {
        if (enemy == null || !enemy.isBroken()) {
            return false;
        }
        enemy.setBrokenRemainTurns(enemy.getBrokenRemainTurns() - 1);
        if (enemy.getBrokenRemainTurns() <= 0) {
            enemy.recoverFromBroken();
            return false;
        }
        return true;
    }
}
