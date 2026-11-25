package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code APPLY_BUFF}: put a unit into a <b>named state</b>.
 *
 * <p>What the state <i>does</i> is separate effects conditioned on {@code has_state}, which keeps "what the state is"
 * apart from "what it changes"; a plain stat buff is {@code MODIFY_ATTR}. A state may be <b>rolled</b> like a DOT or a
 * control ({@code base_chance}), and unstated means applied directly.
 */
final class ApplyBuffOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.APPLY_BUFF;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireBuff(effect, op, rule);
        TriggerValidator.requireDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        TriggerValidator.requireTickOwner(effect, op, rule);
        if (effect.getBaseChance() != null && (effect.getBaseChance() <= 0 || effect.getBaseChance() > 1)) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"base_chance\": " + effect.getBaseChance() + ", but a base chance is a "
                            + "fraction of 1 in (0, 1]: 0.8 = \"an 80% base chance\" (source: " + rule.getSource() + ")");
        }
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.applyState(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
