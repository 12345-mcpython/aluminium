package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code REMOVE_BUFF}: strip named states off the target.
 *
 * <p>The mirror of {@code DISPEL}: that one cleans OUR side's debuffs, this one takes the TARGET's benefits away.
 * Permanent buffs are skipped by {@code BuffManager.removeBuffs} - nobody's skill dispels a loadout.
 */
final class RemoveBuffOp implements OpHandler<MagnitudeArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.REMOVE_BUFF;
    }

    @Override
    public MagnitudeArgs parse(EffectSpec effect) {
        return new MagnitudeArgs(effect);
    }

    @Override
    public void validate(MagnitudeArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requirePositiveAmount(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, MagnitudeArgs args) {
        TriggerEffects.removeBuffs(ctx, args);
    }
}
