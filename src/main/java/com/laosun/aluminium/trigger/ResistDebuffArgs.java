package com.laosun.aluminium.trigger;

/**
 * What {@link ResistDebuffOp} parsed out of its effect.
 *
 * <p>Two shapes share this record. A <b>charge shield</b> states {@code charges} + {@code cooldown} and has no
 * percentage or duration of its own - the cooldown is its clock; a <b>class resistance</b> states a {@code kind} and a
 * {@code magnitude}, and lasts {@code turns} (or the battle, when {@code permanent}).
 *
 * @param kind      the class of negative effect resisted, or {@code null} for "any"
 * @param charges   how many applications the shield refuses, or {@code null} for the percentage shape
 * @param cooldown  the turns before the charge comes back
 * @param turns     the lifetime of a class resistance
 * @param permanent whether that resistance lasts the battle
 * @param magnitude the share (1.0 = immune) of a class resistance
 * @param buffName  the optional name the resistance carries, so it can be removed with the state it belongs to
 */
record ResistDebuffArgs(String kind, Integer charges, Integer cooldown, Integer turns, boolean permanent,
                        Magnitude magnitude, String buffName) implements OpArgs {
}
