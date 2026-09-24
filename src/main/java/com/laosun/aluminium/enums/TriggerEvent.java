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
    /** ✅ {@code Battle.startBattle()} -- once for every combatant. */
    BATTLE_START("BATTLE_START", true),
    /** ✅ An ally finished an attack. Carries the hit-target count. */
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
    /** ✅ Someone's energy was credited. */
    ENERGY_GAINED("ENERGY_GAINED", true),
    /** ✅ Someone really lost HP (shield absorption does not count). */
    HP_LOST("HP_LOST", true),
    /** ✅ Someone was really healed. */
    HEALED("HEALED", true),
    /** ✅ Someone was killed. */
    KILL("KILL", true),
    /** ✅ An enemy was weakness-broken. */
    BREAK("BREAK", true),
    /** ✅ Skill points were really spent. */
    SKILL_POINT_SPENT("SKILL_POINT_SPENT", true),
    /** ✅ Skill points were really gained. */
    SKILL_POINT_GAINED("SKILL_POINT_GAINED", true),
    /** ☐ A turn started. Expressed by {@code MoveEvent} today; declared for completeness. */
    TURN_START("TURN_START", false),
    /** ☐ The owner took a hit. */
    TAKING_HIT("TAKING_HIT", false),
    /**
     * ✅ An ally cast their Ultimate.
     *
     * <p>Fired by {@code SkillExecutor.broadcastSkillCast} when the parsed skill data's
     * {@code attack_type} is {@code Ultra} — never inferred from a skill's name or slot. Exactly one
     * of {@link #SKILL_CAST} and this event fires per cast.
     */
    ULT_CAST("ULT_CAST", true);

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

    /** The string used in the JSON {@code "on"} field. */
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
