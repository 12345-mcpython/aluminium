package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code SHARE_DAMAGE}: one unit bears a share of the damage another would take.
 *
 * <p>The settled instance keeps only its own share ({@code percent} is removed from it) and the same share, element and
 * type is settled against the named unit through that unit's own zones. Registered here so the op has one dispatch
 * path; its load-time checks are still in {@code TriggerValidator}.
 */
final class ShareDamageOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.SHARE_DAMAGE;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.shareDamage(ctx.effect(), ctx.firing());
    }
}
