package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code ADVANCE}: pull targets forward on the action bar by a share of the time they have left.
 *
 * <p>One op for one unit and for a group, because the list resolver is what tells them apart. The ban on advancing
 * <b>others</b> lives on the state the <b>giver</b> holds, not on the target, and it does not stop the holder from
 * advancing herself - which is why the check reads {@code ctx.owner()} rather than the unit being moved.
 */
final class AdvanceOp implements OpHandler<MagnitudeArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.ADVANCE;
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
        CanHit giver = ctx.firing().owner();
        boolean banned = giver != null && giver.getBuffManager() != null
                && giver.getBuffManager().suppresses("advance_to_others");
        for (CanHit target : ctx.targets()) {
            if (banned && target != giver) {
                continue;
            }
            ctx.battle().queue().advanceActionByPercent(target, TriggerMagnitudes.shareOf(args.magnitude(), ctx.firing()));
        }
    }
}
