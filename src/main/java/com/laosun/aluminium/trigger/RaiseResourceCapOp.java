package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.state.Resource;

/**
 * {@code RAISE_RESOURCE_CAP}: widen a declared resource's <b>overflow</b> allowance.
 *
 * <p>Not {@code RAISE_RESOURCE_MAX}, which raises the declaration itself. {@code unbounded} and a count are refused
 * together: "no upper limit" and "by this many more" are different claims.
 */
final class RaiseResourceCapOp implements OpHandler<RaiseResourceCapArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_RESOURCE_CAP;
    }

    @Override
    public RaiseResourceCapArgs parse(EffectSpec effect) {
        return new RaiseResourceCapArgs(
                effect.getResource() == null ? "" : effect.getResource().trim(),
                Boolean.TRUE.equals(effect.getUnbounded()),
                effect);
    }

    @Override
    public void validate(RaiseResourceCapArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireResource(effect, op, rule);
        if (args.unbounded()) {
            if (args.magnitude().getAmount() != null || args.magnitude().getValue() != null) {
                throw new IllegalArgumentException(
                        "Op " + op + " states both \"unbounded\" and a count (\"amount\" / \"value\"): "
                                + "\"no upper limit\" and \"by this many more\" are different claims "
                                + "(source: " + rule.getSource() + ")");
            }
        } else if (args.magnitude().getValue() == null
                && (args.magnitude().getAmount() == null || args.magnitude().getAmount() <= 0)) {
            throw new IllegalArgumentException(
                    "Op " + op + " widens a resource's overflow, so \"amount\" is a positive count of points: got "
                            + (args.magnitude().getAmount() == null ? "nothing" : args.magnitude().getAmount())
                            + " (source: " + rule.getSource() + ")");
        }
    }

    @Override
    public void apply(OpContext ctx, RaiseResourceCapArgs args) {
        CanHit holder = ctx.requireTarget("target");
        Resource resource = TriggerInterpreter.resourceFor(ctx.battle(), holder, args.resource(), op().name());
        if (args.unbounded()) {
            resource.setMaxOverflow(Integer.MAX_VALUE);
            return;
        }
        int raise = args.magnitude().getValue() != null
                ? (int) Math.round(TriggerMagnitudes.valueOf(args.magnitude(), ctx.firing()))
                : (int) Math.round(args.magnitude().getAmount());
        resource.setMaxOverflow(resource.getMaxOverflow() + raise);
    }
}
