package com.laosun.aluminium.trigger;

/**
 * What {@link TickDotOp} parsed out of its effect.
 *
 * @param element   which damage-over-time state to tick, as the effect spells it
 * @param magnitude what share of that state's damage to settle
 */
record TickDotArgs(String element, Magnitude magnitude) implements OpArgs {
}
