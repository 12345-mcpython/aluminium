package com.laosun.aluminium.trigger;

/**
 * What {@link BoostDotOp} parsed out of its effect.
 *
 * @param magnitude the multiplier on the DoTs already on the target, as a share (0.4 = +40%)
 * @param element   the element whose DoTs are boosted, or {@code null} for all of them
 */
record BoostDotArgs(Magnitude magnitude, String element) implements OpArgs {
}
