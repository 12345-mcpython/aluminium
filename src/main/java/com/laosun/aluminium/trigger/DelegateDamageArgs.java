package com.laosun.aluminium.trigger;

/**
 * What {@link DelegateDamageOp} parsed out of its effect.
 *
 * @param skill the slot whose cast this rule hands to somebody else
 */
record DelegateDamageArgs(String skill) implements OpArgs {
}
