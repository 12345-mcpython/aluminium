package com.laosun.aluminium.trigger;

/**
 * What {@link SetStacksOp} parsed out of its effect.
 *
 * @param buffName  the counter to set
 * @param magnitude how many layers it should end up with
 */
record SetStacksArgs(String buffName, Magnitude magnitude) implements OpArgs {
}
