package com.laosun.aluminium.trigger;

/**
 * What {@link SpendStepsOp} parsed out of its effect.
 *
 * @param steps the written {@code "<counter>:<step>"} - which counter is consumed and how wide one milestone is
 */
record SpendStepsArgs(String steps) implements OpArgs {
}
