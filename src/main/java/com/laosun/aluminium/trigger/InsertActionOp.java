package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code INSERT_ACTION}: a unit outside the action order acts <b>now</b>.
 *
 * <p>Not {@code EXTRA_TURN}: that one cuts into a queue the unit is already in, and a memosprite at Speed 0 is not in
 * it at all (the game pins it there with a speed override). Registered here so the op has one dispatch path; it has no
 * load-time checks today (it falls through to {@code TriggerValidator}'s default arm).
 */
final class InsertActionOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.INSERT_ACTION;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        var acting = ctx.requireTarget("target");
        if (!ctx.battle().insertAction(acting)) {
            throw new IllegalStateException("INSERT_ACTION makes " + acting.getName()
                    + " act now, but it is dead or already waiting to act");
        }
    }
}
