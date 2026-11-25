package com.laosun.aluminium.trigger;

/**
 * What {@link RaiseSkillHitsOp} parsed out of its effect.
 *
 * @param slot     the DATA slot whose hit count changes (not a {@code SkillType} letter - the executor knows the number)
 * @param magnitude how many segments to add; negative takes them back
 */
record RaiseSkillHitsArgs(int slot, Magnitude magnitude) implements OpArgs {
}
