package com.laosun.aluminium.models.battle;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.trigger.CastFacts;
import com.laosun.aluminium.models.trigger.TriggerInterpreter;
import com.laosun.aluminium.models.trigger.TriggerPayload;
import com.laosun.aluminium.models.trigger.TriggerTable;

import java.util.List;

/**
 * The trigger-table bus: who is asked about an event, in which order, and with what firing state.
 *
 * <p><b>Why this is separate.</b> The battle owns the characters and the events; this object owns the
 * <b>delivery</b>: our characters first, then the warehouse listeners, then held rules on a wave, all
 * inside one {@link TriggerFiring} so nesting and the "total damage of this firing" have one owner.
 */
public final class TriggerBus {

    private final Battle battle;
    private final TriggerFacts facts;
    private final WarehouseListeners warehouse = new WarehouseListeners();
    private final DeferredTriggers deferred = new DeferredTriggers();
    private final TriggerFiring firing = new TriggerFiring();

    /**
     * @param battle the battle whose characters are asked
     * @param facts  the last-firing facts read by the condition DSL
     */
    public TriggerBus(Battle battle, TriggerFacts facts) {
        this.battle = battle;
        this.facts = facts;
    }

    /**
     * @param listener  the unit
     * @param warehouse the table to ask
     */
    public void registerWarehouseListener(Character listener, TriggerTable warehouse) {
        this.warehouse.register(listener, warehouse);
    }

    /**
     * @param listener the unit
     */
    public void registerWarehouseListener(Character listener) {
        warehouse.register(listener);
    }

    /**
     * Holds a rule until a wave brings enemies in.
     *
     * @param rule    the rule
     * @param context its firing context
     */
    public void deferUntilWave(TriggerTable.CompiledRule rule, TriggerTable.TriggerContext context) {
        deferred.hold(rule, context);
    }

    /**
     * Fires one event to every table that should hear it.
     *
     * @param event            the event
     * @param actor            who caused it
     * @param target           its subject
     * @param hitCount         how many targets were hit
     * @param amount           its magnitude
     * @param damage           the settled instance, or {@code null}
     * @param fromCast         the cast category, or {@code null}
     * @param skillId          the skill row id, or {@code 0}
     * @param weakHitCount     how many hit targets shared the weakness
     * @param attackHitTargets the targets the attack hit
     * @return how many rules fired
     */
    public int fire(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount,
                    Damage damage, SkillCategory fromCast, int skillId, int weakHitCount,
                    List<CanHit> attackHitTargets) {
        firing.begin(event);
        try {
            int fired = 0;
            CastFacts castFacts = new CastFacts(skillId, weakHitCount, attackHitTargets);
            for (Character ally : battle.characters()) {
                if (ally == null) {
                    continue;
                }
                if (ally.isDeath()) {
                    // DEATH is addressed to the unit that just fell; every other event skips the dead.
                    if (event != TriggerEvent.DEATH || ally != target) {
                        continue;
                    }
                }
                TriggerTable table = ally.getTriggerTable();
                if (table == null || table.isEmpty()) {
                    continue;
                }
                fired += TriggerInterpreter.fire(battle, table, event,
                        new TriggerTable.TriggerContext(ally, actor, target, hitCount, amount, damage, battle,
                                fromCast).withCastFacts(castFacts));
            }
            // Warehouse listeners are asked separately: they are owned but not deployed, so they must not be
            // in `characters` (that list feeds the action queue).
            for (java.util.Map.Entry<Character, TriggerTable> entry : warehouse.entries()) {
                Character listener = entry.getKey();
                if (listener == null || listener.isDeath()) {
                    continue;
                }
                TriggerTable table = entry.getValue();
                if (table == null || table.isEmpty()) {
                    continue;
                }
                fired += TriggerInterpreter.fire(battle, table, event,
                        new TriggerTable.TriggerContext(listener, actor, target, hitCount, amount, damage, battle,
                                fromCast).withCastFacts(castFacts));
            }
            if (event == TriggerEvent.WAVE_START && !deferred.isEmpty()) {
                for (DeferredTriggers.Held trigger : deferred.release()) {
                    TriggerInterpreter.apply(battle, trigger.rule(), trigger.context());
                    fired++;
                }
            }
            return fired;
        } finally {
            firing.end();
        }
    }

    /**
     * @param payload the cast firing data
     * @return how many rules fired
     */
    public int fire(TriggerPayload.Cast payload) {
        return fire(payload.event(), payload.actor(), payload.target(), payload.hitCount(), payload.amount(),
                null, payload.fromCast(), payload.skillId(), payload.weakHitCount(), payload.attackHitTargets());
    }

    /**
     * @param payload the damage firing data
     * @return how many rules fired
     */
    public int fire(TriggerPayload.DamagePayload payload) {
        return fire(payload.event(), payload.actor(), payload.target(), 0, payload.amount(),
                payload.damage(), payload.fromCast(), payload.skillId(), 0, payload.attackHitTargets());
    }

    /**
     * @param payload the subject firing data
     * @return how many rules fired
     */
    public int fire(TriggerPayload.Subject payload) {
        return fire(payload.event(), payload.actor(), payload.target(), 0, payload.amount(),
                null, payload.fromCast(), 0, 0, List.of());
    }

    /**
     * @param payload the ally-subject firing data
     * @return how many rules fired, or {@code 0} for a non-ally subject
     */
    public int fireForAlly(TriggerPayload.AllySubject payload) {
        if (payload.target() == null || payload.target().getCamp() != Camp.PLAYER) {
            return 0;
        }
        return fire(payload.event(), payload.actor(), payload.target(), 0, payload.amount(),
                null, null, 0, 0, List.of());
    }

    /**
     * @param payload the plain firing data
     * @return how many rules fired
     */
    public int fire(TriggerPayload.Simple payload) {
        return fire(payload.event(), payload.actor(), payload.target(), payload.hitCount(), payload.amount(),
                null, null, 0, 0, List.of());
    }

    /**
     * @param payload the state-ended firing data
     * @return how many rules fired
     */
    public int fire(TriggerPayload.StateEnded payload) {
        String previous = facts.stateEndedName();
        facts.stateEndedName(payload.stateName());
        try {
            return fire(payload.event(), payload.actor(), payload.target(), 0, payload.amount(),
                    null, null, 0, 0, List.of());
        } finally {
            facts.stateEndedName(previous);
        }
    }

    /**
     * @param payload the resource-changed firing data
     * @return how many rules fired
     */
    public int fire(TriggerPayload.ResourceChanged payload) {
        String previous = facts.changedResource();
        facts.changedResource(payload.resource());
        try {
            return fire(payload.event(), payload.actor(), payload.target(), 0, payload.amount(),
                    null, null, 0, 0, List.of());
        } finally {
            facts.changedResource(previous);
        }
    }

    /**
     * @param amount the damage settled by this instance
     */
    public void addSettledDamage(double amount) {
        firing.addSettledDamage(amount);
    }

    /**
     * @return the damage settled by the outermost firing
     */
    public double settledDamage() {
        return firing.settledDamage();
    }
}
