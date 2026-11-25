package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code APPLY_CONTROL}: a control state, resisted through the effect's own chance pipeline.
 *
 * <p>A named control, a duration, and optionally a {@code base_chance} that goes through Effect Hit Rate / Effect RES.
 * The effect may also carry the state's OWN per-turn damage ({@code element} + a magnitude), which lands with it.
 * {@code permanent} / {@code until} are refused: a control with no clock would never end.
 *
 * <p>Everything the run-time half needs is in {@link ApplyControlArgs}; the base chance and the per-turn damage are
 * still resolved at fire time, because both depend on the battle.
 */
final class ApplyControlOp implements OpHandler<ApplyControlArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.APPLY_CONTROL;
    }

    @Override
    public ApplyControlArgs parse(EffectSpec effect) {
        return new ApplyControlArgs(
                effect.getControl() == null ? "" : effect.getControl().trim(),
                effect.getElement(),
                effect,
                effect.getTurns());
    }

    @Override
    public void validate(ApplyControlArgs args, EffectSpec effect, TriggerSpec rule) {
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
    public void apply(OpContext ctx, ApplyControlArgs args) {
        TriggerEffects.applyControl(ctx, args);
    }
}
