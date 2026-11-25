package com.laosun.aluminium.trigger;

/**
 * What {@link IgnoreWeaknessOp} parsed out of its effect.
 *
 * @param casts how many casts the flag covers; an unstated {@code amount} means one
 */
record IgnoreWeaknessArgs(int casts) implements OpArgs {
}
