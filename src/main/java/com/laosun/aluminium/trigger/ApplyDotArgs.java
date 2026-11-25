package com.laosun.aluminium.trigger;

/**
 * What {@link ApplyDotOp} parsed out of its effect.
 *
 * @param element    the element whose DoT to apply
 * @param magnitude  the damage per tick, as the effect states it
 * @param turns      the state's lifetime
 * @param buffName   the name the DoT answers to, or {@code null} for the element's own state name
 * @param maxStacks  the layer ceiling, or {@code null}
 */
record ApplyDotArgs(String element, Magnitude magnitude, Integer turns, String buffName, Integer maxStacks)
        implements OpArgs {
}
