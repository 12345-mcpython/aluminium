package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code MODIFY_ATTR}: a stat modifier for a stated lifetime.
 *
 * <p>The one op that takes a ratio attribute ({@code CRIT_ATTACK} and friends) as the value itself rather than as an
 * additive percentage - the sign decides buff versus debuff, and {@code TriggerEffects.modifyAttr} is where both
 * live. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator.validateOne} and move here when that arm is deleted.
 */
final class ModifyAttrOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.MODIFY_ATTR;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.modifyAttr(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
