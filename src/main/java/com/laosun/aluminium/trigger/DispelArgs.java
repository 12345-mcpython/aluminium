package com.laosun.aluminium.trigger;

/**
 * What {@link DispelOp} parsed out of its effect.
 *
 * <p>Either an amount, or a <b>class</b> - and with a class the amount is optional, because "all control-class negative
 * states" counts nothing. Both together is legal too: "the newest two of that class".
 *
 * @param kind      the class to remove, or {@code null} for "any negative effect"
 * @param magnitude how many to remove; zero with a class means all of them
 */
record DispelArgs(String kind, Magnitude magnitude) implements OpArgs {
}
