package com.laosun.aluminium.trigger;

/**
 * What {@link RaiseEnergyBankOp} parsed out of its effect.
 *
 * @param magnitude the total overflow allowance (the sentence states the total, not a delta)
 */
record RaiseEnergyBankArgs(Magnitude magnitude) implements OpArgs {
}
