package com.laosun.aluminium.trigger;

/**
 * What {@link ResetTriggerLimitOp} parsed out of its effect.
 *
 * @param rule the id of the rule whose firing limit is cleared
 */
record ResetTriggerLimitArgs(String rule) implements OpArgs {
}
