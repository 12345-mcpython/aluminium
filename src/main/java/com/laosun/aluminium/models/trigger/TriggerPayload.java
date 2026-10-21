package com.laosun.aluminium.models.trigger;

import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Damage;

import java.util.List;
import java.util.Objects;

/**
 * One trigger firing's data, typed by the family of event that produced it.
 *
 * <p><b>Why this exists.</b> {@code Battle.fireTriggers} grew a family of overloads because each
 * event carries a different subset of {actor, target, hit count, amount, damage, cast category, skill
 * row, weakness count, hit set}. A call site that puts a skill id where a hit count belongs still
 * compiles, and the reader has to count argument positions. A typed payload makes the shape of one
 * event explicit; the overloads remain as adapters while the rest of the events move over.
 *
 * <p>This file currently carries three families: the <b>cast family</b> ({@code CAST_SETUP},
 * {@code SKILL_CAST}, {@code ULT_CAST}, {@code BASIC_ATTACK}, {@code ALLY_ATTACK}), the
 * <b>damage family</b> ({@code DEALING_DAMAGE}, {@code DAMAGE_SETTLED}, {@code DEATH}) and the
 * <b>subject family</b> ({@code KILL}, {@code BREAK}, {@code CRIT_DEALT}). The other events keep
 * their existing overloads until they are migrated one family at a time.
 */
public sealed interface TriggerPayload permits TriggerPayload.Cast, TriggerPayload.DamagePayload,
        TriggerPayload.Subject, TriggerPayload.AllySubject, TriggerPayload.StateEnded,
        TriggerPayload.ResourceChanged, TriggerPayload.Simple {

    /**
     * The event this payload is for.
     *
     * @return the event
     */
    TriggerEvent event();

    /**
     * Who caused the event, or {@code null}.
     *
     * @return the actor
     */
    CanHit actor();

    /**
     * What the event happened to, or {@code null}.
     *
     * @return the subject
     */
    CanHit target();

    /**
     * How many targets the attack connected with, or {@code 0}.
     *
     * @return the hit count
     */
    int hitCount();

    /**
     * The event's magnitude where it has one.
     *
     * @return the amount
     */
    double amount();

    /**
     * A cast-family firing.
     *
     * <p>{@code skillId} is the skill slot for these events (the same number the cast events report
     * as {@code from_skill_id}), and {@code fromCast} is the parsed category the rules gate on with
     * {@code from_skill}.
     *
     * @param event           one of the cast events
     * @param actor           the caster
     * @param target          the unit the cast was aimed at
     * @param hitCount        how many targets this cast connected with, or {@code 0} before settlement
     * @param amount          the event's magnitude (for {@code ULT_CAST}, the energy the ultimate spent)
     * @param fromCast        the parsed cast category, or {@code null}
     * @param skillId         the skill slot, or {@code 0} when unknown
     * @param weakHitCount    how many hit targets are weak to the cast's element
     * @param attackHitTargets every target the whole attack hit, in hit order
     */
    record Cast(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount,
                SkillCategory fromCast, int skillId, int weakHitCount,
                List<CanHit> attackHitTargets) implements TriggerPayload {

        /**
         * @throws NullPointerException when {@code event} is {@code null}
         */
        public Cast {
            Objects.requireNonNull(event, "a cast payload needs its event");
            attackHitTargets = attackHitTargets == null ? List.of() : List.copyOf(attackHitTargets);
        }

        /**
         * A convenience factory with the same argument order every cast event used before.
         *
         * @param event           one of the cast events
         * @param actor           the caster
         * @param target          the unit the cast was aimed at
         * @param hitCount        how many targets this cast connected with, or {@code 0} before settlement
         * @param amount          the event's magnitude
         * @param fromCast        the parsed cast category, or {@code null}
         * @param skillId         the skill slot, or {@code 0} when unknown
         * @param weakHitCount    how many hit targets are weak to the cast's element
         * @param attackHitTargets every target the whole attack hit, in hit order
         * @return the payload
         */
        public static Cast of(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount,
                              SkillCategory fromCast, int skillId, int weakHitCount,
                              List<CanHit> attackHitTargets) {
            return new Cast(event, actor, target, hitCount, amount, fromCast, skillId, weakHitCount,
                    attackHitTargets);
        }
    }

    /**
     * A firing that carries the damage instance it is about.
     *
     * <p>The instance is <b>read-only by contract</b>: {@code DEALING_DAMAGE} is the one event where a
     * rule may still mutate it ({@code BOOST_DAMAGE}), and that is why the instance is carried here
     * rather than reconstructed from numbers. The settlement entry point is still
     * {@code Battle.applyDamage}; this payload only announces a firing.
     *
     * @param event            {@code DEALING_DAMAGE}, {@code DAMAGE_SETTLED} or {@code DEATH}
     * @param actor            who caused it
     * @param target           what it happened to
     * @param amount           the event's magnitude (settled damage, or the instance's base at the pre-settlement hook)
     * @param damage           the instance
     * @param fromCast         the cast category that produced it, or {@code null}
     * @param skillId          the skill slot the instance names, or {@code 0}
     * @param attackHitTargets every target the whole attack hit, in hit order
     */
    record DamagePayload(TriggerEvent event, CanHit actor, CanHit target, double amount, Damage damage,
                         SkillCategory fromCast, int skillId, List<CanHit> attackHitTargets)
            implements TriggerPayload {

        /**
         * @throws NullPointerException when {@code event} or {@code damage} is {@code null}
         */
        public DamagePayload {
            Objects.requireNonNull(event, "a damage payload needs its event");
            Objects.requireNonNull(damage, "a damage payload needs its instance");
            attackHitTargets = attackHitTargets == null ? List.of() : List.copyOf(attackHitTargets);
        }

        /**
         * Damage events do not report a hit count; the instance and its hit set carry that.
         *
         * @return always {@code 0}
         */
        @Override
        public int hitCount() {
            return 0;
        }

        /**
         * @param event            the event
         * @param actor            who caused it
         * @param target           what it happened to
         * @param amount           the event's magnitude
         * @param damage           the instance
         * @param fromCast         the cast category, or {@code null}
         * @param skillId          the skill slot, or {@code 0}
         * @param attackHitTargets every target the whole attack hit, in hit order
         * @return the payload
         */
        public static DamagePayload of(TriggerEvent event, CanHit actor, CanHit target, double amount,
                                       Damage damage, SkillCategory fromCast, int skillId,
                                       List<CanHit> attackHitTargets) {
            return new DamagePayload(event, actor, target, amount, damage, fromCast, skillId, attackHitTargets);
        }
    }

    /**
     * A firing about a unit, with no damage instance of its own.
     *
     * <p>{@code KILL} and {@code BREAK} name the cast that caused them; {@code CRIT_DEALT} names the
     * cast whose instance crit. The instance itself is deliberately absent: the settlement entry point
     * is the only public API that takes a {@code Damage}.
     *
     * @param event    the event
     * @param actor    who caused it
     * @param target   what it happened to
     * @param amount   the event's magnitude, or {@code 0}
     * @param fromCast the cast category that produced it, or {@code null}
     */
    record Subject(TriggerEvent event, CanHit actor, CanHit target, double amount, SkillCategory fromCast)
            implements TriggerPayload {

        /**
         * @throws NullPointerException when {@code event} is {@code null}
         */
        public Subject {
            Objects.requireNonNull(event, "a subject payload needs its event");
        }

        /**
         * Subject events do not report a hit count.
         *
         * @return always {@code 0}
         */
        @Override
        public int hitCount() {
            return 0;
        }

        /**
         * @param event    the event
         * @param actor    who caused it
         * @param target   what it happened to
         * @param amount   the event's magnitude, or {@code 0}
         * @param fromCast the cast category, or {@code null}
         * @return the payload
         */
        public static Subject of(TriggerEvent event, CanHit actor, CanHit target, double amount,
                                 SkillCategory fromCast) {
            return new Subject(event, actor, target, amount, fromCast);
        }
    }

    /**
     * A subject firing that is delivered only when the subject is on our side.
     *
     * <p>{@code TAKING_HIT}, {@code HP_LOST} and {@code LETHAL_DAMAGE} are facts about our characters;
     * the engine deliberately does not announce an enemy taking damage to our tables. The camp check
     * is part of this payload's contract, not something each call site has to remember.
     *
     * @param event  the event
     * @param actor  who caused it
     * @param target the subject
     * @param amount the event's magnitude, or {@code 0}
     */
    record AllySubject(TriggerEvent event, CanHit actor, CanHit target, double amount) implements TriggerPayload {

        /**
         * @throws NullPointerException when {@code event} is {@code null}
         */
        public AllySubject {
            Objects.requireNonNull(event, "an ally payload needs its event");
        }

        /**
         * These events do not report a hit count.
         *
         * @return always {@code 0}
         */
        @Override
        public int hitCount() {
            return 0;
        }

        /**
         * @param event  the event
         * @param actor  who caused it
         * @param target the subject
         * @param amount the event's magnitude, or {@code 0}
         * @return the payload
         */
        public static AllySubject of(TriggerEvent event, CanHit actor, CanHit target, double amount) {
            return new AllySubject(event, actor, target, amount);
        }
    }

    /**
     * A named state has just left its carrier.
     *
     * <p>The name rides on the payload because the state is gone by the time the rules run; the
     * interpreter's {@code state_ended} condition reads it from the battle for the duration of the
     * firing. Both {@code actor} and {@code target} are the carrier, the same spelling the engine used
     * before, so existing rules keep working.
     *
     * @param event     {@code STATE_ENDED}
     * @param carrier   the unit the state left
     * @param stateName the state's name as the data spells it
     * @param magnitude how many instances left, or {@code 0}
     */
    record StateEnded(TriggerEvent event, CanHit carrier, String stateName, int magnitude)
            implements TriggerPayload {

        /**
         * @throws NullPointerException when {@code event} is {@code null}
         */
        public StateEnded {
            Objects.requireNonNull(event, "a state-ended payload needs its event");
        }

        @Override
        public CanHit actor() {
            return carrier;
        }

        @Override
        public CanHit target() {
            return carrier;
        }

        @Override
        public int hitCount() {
            return 0;
        }

        @Override
        public double amount() {
            return magnitude;
        }

        /**
         * @param event     the event
         * @param carrier   the unit the state left
         * @param stateName the state's name
         * @param magnitude how many instances left
         * @return the payload
         */
        public static StateEnded of(TriggerEvent event, CanHit carrier, String stateName, int magnitude) {
            return new StateEnded(event, carrier, stateName, magnitude);
        }
    }

    /**
     * A resource counter moved.
     *
     * <p>The resource's name rides on the payload because the condition
     * ({@code resource_changed:<name>}) reads it from the battle for the duration of the firing; the
     * save/restore that keeps a nested firing from overwriting it belongs to {@code Battle.fire}, not
     * to every caller.
     *
     * @param event    {@code RESOURCE_CHANGED}
     * @param holder   the unit whose store moved
     * @param resource the counter's name
     * @param amount   the delta
     */
    record ResourceChanged(TriggerEvent event, CanHit holder, String resource, double amount)
            implements TriggerPayload {

        /**
         * @throws NullPointerException when {@code event} or {@code resource} is {@code null}
         */
        public ResourceChanged {
            Objects.requireNonNull(event, "a resource-changed payload needs its event");
            Objects.requireNonNull(resource, "a resource-changed payload needs the resource name");
        }

        @Override
        public CanHit actor() {
            return holder;
        }

        @Override
        public CanHit target() {
            return holder;
        }

        @Override
        public int hitCount() {
            return 0;
        }

        /**
         * @param event    the event
         * @param holder   the unit whose store moved
         * @param resource the counter's name
         * @param amount   the delta
         * @return the payload
         */
        public static ResourceChanged of(TriggerEvent event, CanHit holder, String resource, double amount) {
            return new ResourceChanged(event, holder, resource, amount);
        }
    }

    /**
     * A firing with no damage instance, cast category or named subject beyond the event's own fields.
     *
     * <p>This is the last shape: a plain event with an actor, a subject, a hit count and an amount.
     * It covers the turn/battle/wave/skill-point announcements that do not need any of the richer
     * families.
     *
     * @param event    the event
     * @param actor    who caused it, or {@code null}
     * @param target   what it happened to, or {@code null}
     * @param hitCount how many hits or instances the event reports, or {@code 0}
     * @param amount   the event's magnitude, or {@code 0}
     */
    record Simple(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount)
            implements TriggerPayload {

        /**
         * @throws NullPointerException when {@code event} is {@code null}
         */
        public Simple {
            Objects.requireNonNull(event, "a simple payload needs its event");
        }

        /**
         * @param event    the event
         * @param actor    who caused it
         * @param target   what it happened to
         * @param hitCount how many hits or instances the event reports
         * @param amount   the event's magnitude
         * @return the payload
         */
        public static Simple of(TriggerEvent event, CanHit actor, CanHit target, int hitCount, double amount) {
            return new Simple(event, actor, target, hitCount, amount);
        }

        /**
         * A payload for an event with neither actor, subject, hits nor amount.
         *
         * @param event the event
         * @return the payload
         */
        public static Simple of(TriggerEvent event) {
            return new Simple(event, null, null, 0, 0);
        }
    }
}
