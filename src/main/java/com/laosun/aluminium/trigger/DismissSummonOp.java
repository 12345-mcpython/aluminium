package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code DISMISS_SUMMON}: take a summoned unit off the field.
 *
 * <p>The inverse of {@code SUMMON}, and unlike {@code DEPART} it removes somebody else's unit rather than the target's
 * own presence. Registered here so the op has one dispatch path; it has <b>no</b> load-time checks today (it falls
 * through to {@code TriggerValidator}'s default arm).
 */
final class DismissSummonOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.DISMISS_SUMMON;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        // This op has no arm of its own in TriggerValidator, so this was the default check it used to get there.
        TriggerValidator.requireNoStackArguments(effect, op().name(), rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        for (com.laosun.aluminium.entity.CanHit target : ctx.targets()) {
            if (!(target instanceof com.laosun.aluminium.entity.Summon summon)) {
                throw new IllegalStateException("DISMISS_SUMMON resolved "
                        + (target == null ? "null" : target.getName()) + ", which is not a summon");
            }
            if (!summon.isDeath()) {
                summon.perish();
            }
        }
    }
}
