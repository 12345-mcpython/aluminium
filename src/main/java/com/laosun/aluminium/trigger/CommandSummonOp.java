package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code COMMAND_SUMMON}: order somebody's summoned unit to cast one of its skills.
 *
 * <p>The summoner keeps the action bar; this only makes the summon act out of turn. Registered here so the op has one
 * dispatch path; its load-time checks are still in {@code TriggerValidator}.
 */
final class CommandSummonOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.COMMAND_SUMMON;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.commandSummon(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
