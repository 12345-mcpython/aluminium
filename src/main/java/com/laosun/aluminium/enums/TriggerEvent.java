package com.laosun.aluminium.enums;

import java.util.HashMap;
import java.util.Map;

/**
 * The trigger sources a character mechanic can subscribe to (P8-7).
 *
 * <p>This is the data-side name of an engine event. The whole point of the trigger table is that
 * character content is expressed as "on &lt;event&gt;, if &lt;condition&gt;, do &lt;effects&gt;", so
 * this enum is the closed vocabulary of {@code "on"} values in
 * {@code resources/characters/<cid>.json}.
 *
 * <p>⚠ <b>Not every value is wired yet.</b> The ones marked ✅ have an emitter in the engine; the
 * rest are declared so the JSON vocabulary is stable and so an unwired trigger fails <b>loudly at
 * load time</b> ({@link #isWired()}) rather than silently doing nothing. See {@code engine.md} §4
 * for the events that actually fire today.
 */
public enum TriggerEvent {
    /**
     * ✅ {@code Battle.startBattle()} -- once for every combatant.
     */
    BATTLE_START("BATTLE_START", true),
    /**
     * ✅ An ally finished an attack. Carries the hit-target count.
     */
    ALLY_ATTACK("ALLY_ATTACK", true),
    /**
     * ✅ An ally cast a skill, including non-damaging ones — but <b>not</b> their Ultimate.
     *
     * <p>⚠ Companion of {@link #ULT_CAST}: the two are mutually exclusive by design. The DSL has no
     * variable for "which kind of cast this was" (see {@code TriggerTable}'s condition list), so a
     * rule that means "when the wearer uses their Skill" could not otherwise avoid also firing on the
     * ultimate. The split is made at the emitter ({@code SkillExecutor.broadcastSkillCast}).
     */
    SKILL_CAST("SKILL_CAST", true),
    /**
     * ✅ Someone's energy was credited.
     */
    ENERGY_GAINED("ENERGY_GAINED", true),
    /**
     * ✅ Someone really lost HP (shield absorption does not count).
     */
    HP_LOST("HP_LOST", true),
    /**
     * ✅ Someone was really healed.
     */
    HEALED("HEALED", true),
    /**
     * ✅ Someone was killed.
     */
    KILL("KILL", true),
    /**
     * ✅ An enemy was weakness-broken.
     */
    BREAK("BREAK", true),
    /**
     * ✅ Skill points were really spent.
     */
    SKILL_POINT_SPENT("SKILL_POINT_SPENT", true),
    /**
     * ✅ Skill points were really gained.
     */
    SKILL_POINT_GAINED("SKILL_POINT_GAINED", true),
    /**
     * ✅ A character's turn began — emitted by {@code Battle.beforeMove}, after the actor's buffs have
     * been settled and before its {@code MoveEvent.beforeMove} hook.
     *
     * <p>⚠ <b>This is a trigger-table event, not a new buff interface.</b> Turn boundaries stay
     * {@code MoveEvent.beforeMove/afterMove} for buffs — that decision is pinned by
     * {@code EventBusTest.turnBoundariesAreStillMoveEvent} and nothing here revives it. What
     * {@code TURN_START} adds is only the ability for <b>data</b> to subscribe to the same moment
     * ("at the beginning of the turn, if …"), which the buff interfaces cannot express because a JSON
     * rule is not a Java class.
     */
    TURN_START("TURN_START", true),
    /**
     * ✅ The owner was hit by an incoming damage instance.
     *
     * <p><b>Deliberately not the same fact as {@link #HP_LOST}.</b> {@code HP_LOST} means "HP was
     * really lost" (a fully shielded hit does not fire it, and neither does a hit on an invulnerable
     * target); {@code TAKING_HIT} means "an attack landed on me", which is exactly what the relic and
     * talent texts that say "after the wearer is hit / attacked" mean — those effects accumulate even
     * when a shield eats the whole hit. Emitting both from the same place with the same gate would
     * silently make one mean the other, so the two are separate events with separate conditions:
     * {@code HP_LOST} fires only when {@code hpLoss > 0}, {@code TAKING_HIT} fires once per settled
     * instance against a live, non-invulnerable target.
     *
     * <p>{@code actor} = whoever caused the damage, {@code target} = the one who took it (so "I was
     * hit" is {@code target == self}, the same convention as {@code HP_LOST}). It follows
     * {@code HP_LOST}'s broadcast policy, so it is also fired only when the subject is one of ours.
     */
    TAKING_HIT("TAKING_HIT", true),
    /**
     * ✅ An ally cast their Ultimate.
     *
     * <p>Fired by {@code SkillExecutor.broadcastSkillCast} when the parsed skill data's
     * {@code attack_type} is {@code Ultra} — never inferred from a skill's name or slot. Exactly one
     * of {@link #SKILL_CAST} and this event fires per cast.
     */
    ULT_CAST("ULT_CAST", true),
    /**
     * ✅ A follow-up attack was used: an <b>additional-damage</b> instance settled through
     * {@code Battle.applyAdditionalDamage}, with the attacker as {@code actor} and the victim as
     * {@code target}.
     *
     * <p><b>Why it needs its own event rather than {@link #ALLY_ATTACK}.</b> The relic and talent
     * texts that say "when the wearer uses a Follow-Up ATK" mean that category specifically;
     * {@code ALLY_ATTACK} fires for every attack, so a rule hung on it would also fire for basic
     * attacks, skills and ultimates — a silent over-trigger, not a near miss.
     *
     * <p><b>What counts as one.</b> The engine has exactly one notion of an attack that "does not
     * count as dealing 1 attack": {@code DamageType.ADDITIONAL}, which is what a talent-driven
     * follow-up (Clara's counter, the P8-3 shape) is settled as. So this fires from that single
     * settlement point, and nothing else in the engine fires it.
     *
     * <p>It is emitted for every such instance <b>whether or not it dealt damage</b>: the texts that
     * subscribe say "when the wearer uses a Follow-Up ATK", which is the attack being <i>used</i>, and
     * one absorbed entirely by a shield or an invulnerable target was still used.
     *
     * <p>⚠ Note the recursion this creates — a rule that answers {@code FOLLOW_UP} with the
     * {@code DAMAGE} op is a follow-up responding to a follow-up; {@code Battle.MAX_TRIGGER_DEPTH}
     * stops that loudly instead of letting it run away.
     */
    FOLLOW_UP("FOLLOW_UP", true);

    private static final Map<String, TriggerEvent> BY_NAME = new HashMap<>();

    static {
        for (TriggerEvent event : values()) {
            BY_NAME.put(event.name, event);
        }
    }

    private final String name;
    private final boolean wired;

    TriggerEvent(String name, boolean wired) {
        this.name = name;
        this.wired = wired;
    }

    /**
     * The string used in the JSON {@code "on"} field.
     */
    public String value() {
        return name;
    }

    /**
     * Whether the engine currently emits this event.
     *
     * <p>A trigger table referencing an unwired event is almost certainly a mistake (the author
     * expected something to happen and nothing ever will), so
     * {@code TriggerTable} rejects it at load time instead of ignoring it.
     */
    public boolean isWired() {
        return wired;
    }

    /**
     * Parses the JSON {@code "on"} value, case-insensitively.
     *
     * @param raw the data value
     * @return the matching event, or {@code null} when the value is unknown
     */
    public static TriggerEvent fromString(String raw) {
        return raw == null ? null : BY_NAME.get(raw.trim().toUpperCase(java.util.Locale.ROOT));
    }
}
