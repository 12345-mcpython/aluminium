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
final class DelayOp implements OpHandler<MagnitudeArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.DELAY;
    }

    @Override
    public MagnitudeArgs parse(EffectSpec effect) {
        return new MagnitudeArgs(effect);
    }

    @Override
    public void validate(MagnitudeArgs args, EffectSpec effect, TriggerSpec rule) {
        TriggerValidator.requirePercent(effect, op().name(), rule);
        TriggerValidator.requireNoStackArguments(effect, op().name(), rule);
    }

    @Override
    public void apply(OpContext ctx, MagnitudeArgs args) {
        for (CanHit target : ctx.targets()) {
            ctx.battle().delayMovePercent(target, TriggerMagnitudes.shareOf(args.magnitude(), ctx.firing()));
        }
    }
}
