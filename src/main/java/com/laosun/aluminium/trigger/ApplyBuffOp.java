package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code APPLY_BUFF}: put a unit into a <b>named state</b>.
 *
 * <p>What the state <i>does</i> is separate effects conditioned on {@code has_state}, which keeps "what the state is"
 * apart from "what it changes"; a plain stat buff is {@code MODIFY_ATTR}. A state may be <b>rolled</b> like a DOT or a
 * control ({@code base_chance}), and unstated means applied directly.
 *
 * <p>Everything that describes the state is parsed once, in {@link ApplyBuffArgs}; the two lifetime anchors
 * ({@code until} / {@code ticks_on}) are still read from the effect, because that cluster has its own reader and has not
 * been parsed yet.
 */
final class ApplyBuffOp implements OpHandler<ApplyBuffArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.APPLY_BUFF;
    }

    @Override
    public ApplyBuffArgs parse(EffectSpec effect) {
        boolean permanent = TriggerLifetimes.unticked(effect);
        // ⚠ `Integer.valueOf(...)` on the placeholder, not a bare `int`: a `?:` that mixes the primitive placeholder with
        // the boxed `getTurns()` is evaluated as `int` and UNBOXES the null - the same trap that broke RESIST_DEBUFF,
        // and this time the case is a rule that states no duration at all, whose refusal is the validator's job.
        Integer turns = permanent
                ? Integer.valueOf(TriggerEffects.UNBOUNDED_DURATION_PLACEHOLDER)
                : effect.getTurns();
        return new ApplyBuffArgs(
                effect.getBuff(),
                effect.getRandomOf() == null ? null : java.util.List.copyOf(effect.getRandomOf()),
                Boolean.TRUE.equals(effect.getStackable()),
                Boolean.TRUE.equals(effect.getDefersDeath()),
                Boolean.TRUE.equals(effect.getSuspendsTurns()),
                turns,
                permanent,
                effect.getMaxStacks(),
                effect.getBlocks(),
                effect.getCountsAs() == null ? null : java.util.List.copyOf(effect.getCountsAs()),
                effect.getCountsAsRequires(),
                effect);
    }

    @Override
    public void validate(ApplyBuffArgs args, EffectSpec effect, TriggerSpec rule) {
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
    public void apply(OpContext ctx, ApplyBuffArgs args) {
        TriggerEffects.applyState(ctx, args);
    }
}
