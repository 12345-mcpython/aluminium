package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code APPLY_CONTROL}: a control state, resisted through the effect's own chance pipeline.
 *
 * <p>A named control, a duration, and optionally a {@code base_chance} that goes through Effect Hit Rate / Effect RES.
 * The effect may also carry the state's OWN per-turn damage ({@code element} + a magnitude), which lands with it.
 * {@code permanent} / {@code until} are refused: a control with no clock would never end.
 */
final class ApplyControlOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.APPLY_CONTROL;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireControl(effect, op, rule);
        TriggerValidator.requirePositiveTurns(effect, op, rule);
        TriggerValidator.requireBaseChance(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        if (Boolean.TRUE.equals(effect.getPermanent()) || TriggerLifetimes.eventBound(effect)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states a number of turns and nothing else; \"permanent\" / \"until\" "
                            + "would be a control that never ends (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireStateDamage(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.applyControl(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
