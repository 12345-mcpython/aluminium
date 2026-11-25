package com.laosun.aluminium.trigger;

/**
 * What {@link RemoveStateOp} parsed out of its effect.
 *
 * <p>{@code buff} is the state's name - the same spelling {@code has_state} reads and {@code APPLY_BUFF} writes, so the
 * three are one vocabulary. {@code attribute} is the other filter (what a modifier modifies), and {@code kind: "own"} is
 * the origin filter {@code EXTEND_BUFF} has always had.
 *
 * @param buff      the named state to drop, when the sentence names one
 * @param attribute the attribute whose modifier to drop, when it names that instead
 * @param kind      {@code "own"} to remove only what this rule's owner applied, or {@code null}
 */
record RemoveStateArgs(String buff, String attribute, String kind) implements OpArgs {
}
