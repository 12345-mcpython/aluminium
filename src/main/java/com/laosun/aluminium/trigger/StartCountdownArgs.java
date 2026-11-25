package com.laosun.aluminium.trigger;

/**
 * What {@link StartCountdownOp} parsed out of its effect.
 *
 * @param statedName the name the sentence gives the countdown, or {@code null} for "name it after whoever put it there"
 * @param speed      the countdown's fixed Speed, which decides when its turn comes
 */
record StartCountdownArgs(String statedName, Double speed) implements OpArgs {
}
