package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code DELAY}: push targets back on the action bar by a share of their action period.
 *
 * <p>The mirror of {@code ADVANCE} with one asymmetry that is content, not an oversight: the sentence that bans
 * advancing others (1512's 【特邀嘉宾】) bans advances only, so a delay reads no suppression.
 */
final class DelayOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.DELAY;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        TriggerValidator.requirePercent(effect, op().name(), rule);
        TriggerValidator.requireNoStackArguments(effect, op().name(), rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        for (CanHit target : ctx.targets()) {
            ctx.battle().delayMovePercent(target, ctx.share());
        }
    }
}
