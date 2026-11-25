package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code DEPART}: take units off the field without killing them.
 *
 * <p>Two engine facts, one op: the unit stops being targetable <b>and</b> leaves the action order. {@code RETURN}
 * is its exact inverse; the content calls one at each end, so a state that never ends cannot leave a phantom behind.
 * A duration is refused - the sentence that says "离场" also says when it comes back, as a separate {@code RETURN}.
 */
final class DepartOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.DEPART;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        TriggerValidator.requireNoDuration(effect, op().name(), rule);
        TriggerValidator.requireNoStackArguments(effect, op().name(), rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        for (CanHit leaving : ctx.targets()) {
            leaving.setUntargetable(true);
            ctx.battle().queue().removeCombatant(leaving);
        }
    }
}
