package com.laosun.aluminium.trigger;

/**
 * What {@link RaiseResourceMaxOp} parsed out of its effect.
 *
 * @param resource  the resource whose <b>declaration</b> rises
 * @param magnitude how far it rises
 */
record RaiseResourceMaxArgs(String resource, Magnitude magnitude) implements OpArgs {
}
