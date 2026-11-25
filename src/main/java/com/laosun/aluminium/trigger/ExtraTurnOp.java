package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code EXTRA_TURN}: give a unit that is already in the action order one more turn.
 *
 * <p>Refused rather than ignored when the unit is not in the order: a memosprite at Speed 0 is exactly that, and the
 * rule used to look like it had worked ("extra turn actor = none" and nothing said why). {@code INSERT_ACTION} is the
 * op for making a unit outside the order act now. Registered here so the op has one dispatch path; it has no load-time
 * checks today (it falls through to {@code TriggerValidator}'s default arm).
 */
final class ExtraTurnOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.EXTRA_TURN;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        var holder = ctx.requireTarget("target");
        if (!ctx.battle().grantExtraTurn(holder)) {
            throw new IllegalStateException("EXTRA_TURN gives " + holder.getName()
                    + " an extra turn, but it is not in the action order (a memosprite at Speed 0 is skipped "
                    + "when the battle starts), so no extra turn can be granted");
        }
    }
}
