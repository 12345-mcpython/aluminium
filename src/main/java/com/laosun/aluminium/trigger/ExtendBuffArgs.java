package com.laosun.aluminium.trigger;

/**
 * What {@link ExtendBuffOp} parsed out of its effect.
 *
 * <p>Every sentence in this family identifies its target by <b>origin</b> ("the shield provided by the Skill") or by
 * <b>state/attribute</b>; {@code kind: "all"} is the one spelling that drops the origin filter.
 *
 * @param turns     how many turns to add
 * @param kind      {@code "all"} to lengthen every buff the target holds, or {@code null}
 * @param buff      the state's name, when the sentence names one
 * @param attribute the attribute whose modifier to lengthen, when it names that instead
 */
record ExtendBuffArgs(Integer turns, String kind, String buff, String attribute) implements OpArgs {
}
