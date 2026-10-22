package com.laosun.aluminium;

import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.CanHit;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The battle's <b>event-announcement surface</b>: one named entry per event that is announced to units rather than
 * fired at trigger tables.
 *
 * <h2>Why this exists apart from {@code Battle}</h2>
 * {@code Battle} both <b>runs</b> the fight and <b>announces</b> what happened, and the two have nothing to say to
 * each other: the settlement code decides a number, then tells everyone. Keeping both in one 3800-line class made
 * the announcement surface invisible -- eight near-identical one-line methods scattered among the runtime, each
 * carrying a different routing rule that nothing else states. Here they are the whole file, so "who is told about
 * what" is readable in one place.
 *
 * <h2>The routing rule, which is the only thing these methods decide</h2>
 * {@link #dispatch} sends an event to "the directly involved parties + our entire side, de-duplicated":
 * <ul>
 *   <li><b>the involved parties first</b> ({@code target}, {@code attacker}, ...), so a unit that both acted and
 *       was acted upon hears it once, in the same position every time. ⚠ <b>An involved party may be an ENEMY</b>
 *       -- the monster that landed the killing blow is told, because it is part of what happened, and a boss
 *       mechanic or a counter has to be able to react to it;</li>
 *   <li><b>then our whole party</b>, because our members subscribe to things happening to each other --
 *       "after each time one of our targets casts an attack" is subscribed by every support, not only by the one
 *       involved. Note: <b>our side only.</b> An enemy that was not involved has no rules table and no buff that
 *       reacts, so announcing to it would be work nobody reads;</li>
 *   <li><b>de-duplicated by identity</b>, so a unit that was already reached as an involved party is not reached
 *       again as a party member -- otherwise the same buff is called twice and its effect doubles.</li>
 * </ul>
 *
 * <p>⚠ <b>This is not the trigger-table bus.</b> {@code Battle.fireTriggers} hands an event to the <b>compiled
 * rule tables</b>; this class hands a typed hook ({@code onKill}, {@code onHeal}, ...) to <b>units and their
 * buffs</b>. The two are different audiences with different call sites, and this file deliberately owns only the
 * second -- moving the rule-table bus here would drag the whole assembly with it.
 */
final class BattleEvents {

    /**
     * The battle whose units are announced to. The hooks take it as their first argument, so a listener can read
     * state rather than only the delta it was handed.
     */
    private final Battle battle;

    /**
     * @param battle the battle this announces for
     */
    BattleEvents(Battle battle) {
        this.battle = battle;
    }

    /**
     * Deliver an event to "the directly involved parties + our entire side (de-duplicated)".
     *
     * @param consumer the delivery action
     * @param targets  the directly involved parties (may contain {@code null}, which is skipped)
     */
    void dispatch(Consumer<CanHit> consumer, CanHit... targets) {
        Set<CanHit> notified = Collections.newSetFromMap(new IdentityHashMap<>());
        for (CanHit target : targets) {
            if (target != null && notified.add(target)) {
                consumer.accept(target);
            }
        }
        for (CanHit ally : battle.allies()) {
            if (notified.add(ally)) {
                consumer.accept(ally);
            }
        }
    }

    /**
     * Energy credited.
     *
     * @param target the one whose bar moved
     * @param added  how much was actually credited
     */
    void energyGain(CanHit target, double added) {
        dispatch(t -> t.onEnergyGain(battle, target, added), target);
    }

    /**
     * HP loss. {@code source} is this hit's attacker and may be {@code null}.
     *
     * @param target the one who lost HP
     * @param before its HP before
     * @param after  its HP after
     * @param source the attacker, or {@code null}
     * @param amount how much was lost
     */
    void hpLoss(CanHit target, double before, double after, CanHit source, double amount) {
        dispatch(t -> t.onHpLoss(battle, target, before, after, source, amount), target, source);
    }

    /**
     * Kill.
     *
     * @param attacker the killer, or {@code null}
     * @param victim   the one who was killed
     */
    void kill(CanHit attacker, CanHit victim) {
        dispatch(t -> t.onKill(battle, attacker, victim), victim, attacker);
    }

    /**
     * Heal.
     *
     * @param healer the one who healed
     * @param target the one healed
     * @param healed how much was restored
     */
    void heal(CanHit healer, CanHit target, double healed) {
        dispatch(t -> t.onHeal(battle, healer, target, healed), target, healer);
    }

    /**
     * Weakness break.
     *
     * @param attacker the one who broke it
     * @param target   the broken enemy
     * @param element  the element it was broken with
     */
    void weaknessBreak(CanHit attacker, CanHit target, DamageElement element) {
        dispatch(t -> t.onBreak(battle, attacker, target, element), target, attacker);
    }

    /**
     * Skill points credited.
     *
     * @param amount how many were gained
     */
    void skillPointGained(int amount) {
        dispatch(t -> t.onSkillPointGained(battle, amount));
    }

    /**
     * Skill points spent.
     *
     * @param amount how many were spent
     */
    void skillPointSpent(int amount) {
        dispatch(t -> t.onSkillPointSpent(battle, amount));
    }
}
