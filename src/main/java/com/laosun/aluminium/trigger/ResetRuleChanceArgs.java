package com.laosun.aluminium.trigger;

/**
 * What {@link ResetRuleChanceOp} parsed out of its effect.
 *
 * @param rule the id of the rule whose decaying probability is reset
 */
record ResetRuleChanceArgs(String rule) implements OpArgs {
}
