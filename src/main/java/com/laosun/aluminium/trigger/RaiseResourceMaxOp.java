package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.state.Resource;

/**
 * {@code RAISE_RESOURCE_MAX}: raise the resource's <b>declaration</b>, for the rest of the battle.
 *
 * <p>The tier below the overflow allowance {@code RAISE_RESOURCE_CAP} widens - one op must not mean two numbers.
 */
final class RaiseResourceMaxOp implements OpHandler<RaiseResourceMaxArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_RESOURCE_MAX;
    }

    @Override
    public RaiseResourceMaxArgs parse(EffectSpec effect) {
        return new RaiseResourceMaxArgs(
                effect.getResource() == null ? "" : effect.getResource().trim(),
                effect);
    }

    @Override
    public void validate(RaiseResourceMaxArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireResource(effect, op, rule);
        TriggerValidator.requirePositiveAmount(effect, op, rule);
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, RaiseResourceMaxArgs args) {
        CanHit holder = ctx.requireTarget("target");
        Resource resource = TriggerInterpreter.resourceFor(ctx.battle(), holder, args.resource(), op().name());
        resource.raiseMax(args.magnitude().getValue() != null
                ? (int) Math.round(TriggerMagnitudes.valueOf(args.magnitude(), ctx.firing()))
                : (int) Math.round(args.magnitude().getAmount()));
    }
}
