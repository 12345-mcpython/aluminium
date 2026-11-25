package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code REMOVE_BUFF}: strip named states off the target.
 *
 * <p>The mirror of {@code DISPEL}: that one cleans OUR side's debuffs, this one takes the TARGET's benefits away.
 * Permanent buffs are skipped by {@code BuffManager.removeBuffs} - nobody's skill dispels a loadout.
 */
final class RemoveBuffOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.REMOVE_BUFF;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requirePositiveAmount(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.removeBuffs(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
