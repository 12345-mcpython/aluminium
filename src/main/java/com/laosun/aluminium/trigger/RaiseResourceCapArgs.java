package com.laosun.aluminium.trigger;

/**
 * What {@link RaiseResourceCapOp} parsed out of its effect.
 *
 * @param resource  the resource whose <b>overflow</b> allowance widens
 * @param unbounded whether the allowance is removed entirely (a different claim from "by this many more")
 * @param magnitude how far it widens, when it is not unbounded
 */
record RaiseResourceCapArgs(String resource, boolean unbounded, Magnitude magnitude) implements OpArgs {
}
