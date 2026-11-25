package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code SET_STACKS}: move a counter <b>to</b> a stated number rather than adding to it.
 *
 * <p>Distinct from {@code ADD_STACK}, which only ever adds; this one removes layers too, until the counter matches the
 * number the sentence states. Registered here so the op has one dispatch path; it has <b>no</b> load-time checks in
 * {@code TriggerValidator} today (it falls through to the default arm) - writing them is separate work, because a new
 * check can refuse content that loads today.
 */
final class SetStacksOp implements OpHandler<SetStacksArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.SET_STACKS;
    }

    @Override
    public SetStacksArgs parse(EffectSpec effect) {
        return new SetStacksArgs(effect.getBuff(), effect);
    }

    @Override
    public void validate(SetStacksArgs args, EffectSpec effect, TriggerSpec rule) {
        // This op has no arm of its own in TriggerValidator, so this was the default check it used to get there.
        TriggerValidator.requireNoStackArguments(effect, op().name(), rule);
    }

    @Override
    public void apply(OpContext ctx, SetStacksArgs args) {
        TriggerEffects.setStacks(ctx, args);
    }
}
