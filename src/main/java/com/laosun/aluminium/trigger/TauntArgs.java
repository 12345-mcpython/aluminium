package com.laosun.aluminium.trigger;

/**
 * What {@link TauntOp} parsed out of its effect.
 *
 * @param turns how long the taunt lasts, in the holder's turns
 */
record TauntArgs(Integer turns) implements OpArgs {
}
