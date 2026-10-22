package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Summon;
import com.laosun.aluminium.models.trigger.TriggerPayload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The death sweep: orphaned summons, disappeared memosprites, corpses off the action bar, buffs
 * anchored to the dead, and the result check.
 *
 * <p><b>Why this is separate.</b> These steps always run together, in this order, and the order is the
 * mechanic: a master's death first takes its summon, the departure is announced while the owner can
 * still hear it, then the action bar is cleared. Keeping that in the turn file made each step look
 * independently callable.
 */
public final class DeathService {

    private final Battle battle;
    private final TriggerBus bus;
    private final Set<CanHit> summonsAnnouncedGone =
            Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * @param battle the battle whose camps, queue and result are used
     * @param bus    the trigger bus for the departure event
     */
    public DeathService(Battle battle, TriggerBus bus) {
        this.battle = battle;
        this.bus = bus;
    }

    /**
     * Runs the full sweep.
     */
    public void removeDeadCombatants() {
        perishOrphanedSummons();
        announceSummonsThatLeft();
        for (var signal : battle.queue().snapshot()) {
            if (signal.getCanHit().isDeath()) {
                battle.queue().removeCombatant(signal.getCanHit());
            }
        }
        releaseBuffsAnchoredToTheDead();
        battle.checkResult();
    }

    /**
     * Fires {@code SUMMON_LEFT} once per disappeared memosprite.
     */
    private void announceSummonsThatLeft() {
        List<CanHit> everyone = new ArrayList<>(battle.allies());
        everyone.addAll(battle.enemies());
        for (CanHit unit : everyone) {
            if (unit instanceof Summon && unit.isDeath() && summonsAnnouncedGone.add(unit)) {
                bus.fire(TriggerPayload.Simple.of(TriggerEvent.SUMMON_LEFT, unit, unit, 0, 0.0));
            }
        }
    }

    /**
     * Removes buffs whose clock was another unit's turns from carriers whose anchor died.
     */
    private void releaseBuffsAnchoredToTheDead() {
        List<CanHit> everyone = new ArrayList<>(battle.allies());
        everyone.addAll(battle.enemies());
        for (CanHit carrier : everyone) {
            if (carrier == null || carrier.isDeath()) {
                continue;
            }
            for (CanHit other : everyone) {
                if (other != null && other != carrier && other.isDeath()) {
                    carrier.getBuffManager().removeBuffsAnchoredTo(other);
                }
            }
        }
    }

    /**
     * Defeats every summon whose master is down.
     */
    private void perishOrphanedSummons() {
        for (CanHit unit : battle.allies()) {
            perishIfOrphaned(unit);
        }
        for (CanHit unit : battle.enemies()) {
            perishIfOrphaned(unit);
        }
    }

    private static void perishIfOrphaned(CanHit unit) {
        if (unit instanceof Summon summon && !summon.isDeath()
                && summon.getMaster() != null && summon.getMaster().isDeath()) {
            summon.perish();
        }
    }
}
