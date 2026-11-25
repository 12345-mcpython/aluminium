package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code APPLY_REGEN}: a heal-over-time state, the healing twin of {@code APPLY_DOT}.
 *
 * <p>Its magnitude is a HEAL amount, so it takes the heal/shield scale vocabulary, not the {@code self_attr:} one the
 * modifier family uses - "equal to 20% of her Max HP" is {@code owner_max_hp}.
 */
final class ApplyRegenOp implements OpHandler<ApplyRegenArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.APPLY_REGEN;
    }

    @Override
    public ApplyRegenArgs parse(EffectSpec effect) {
        return new ApplyRegenArgs(effect.getBuff() == null ? "" : effect.getBuff().trim(), effect, effect.getTurns());
    }

    @Override
    public void validate(ApplyRegenArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireAmountOrScale(effect, op, rule, TriggerValidator.SCALES, "Max HP");
        TriggerValidator.requireDuration(effect, op, rule);
        TriggerValidator.requireBuff(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, ApplyRegenArgs args) {
        TriggerEffects.applyRegen(ctx, args);
    }
}
