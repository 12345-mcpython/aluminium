package com.laosun.aluminium.trigger;

/**
 * What {@link CommandSummonOp} parsed out of its effect.
 *
 * @param skill     the OWNER's slot whose parameter row carries the numbers (the summon is only the one that swings)
 * @param attribute which attribute the multiplier reads, as written
 * @param magnitude the magnitude itself
 */
record CommandSummonArgs(String skill, String attribute, Magnitude magnitude) implements OpArgs {
}
