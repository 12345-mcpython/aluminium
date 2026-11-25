package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code APPLY_DOT}: a damage-over-time state with its magnitude fixed once, at application.
 *
 * <p>The element is the engine's own spelling (Fire to burn), the magnitude is either flat or derived from the RULE
 * OWNER's attribute, and it is frozen into the buff when it lands - which is why {@code BOOST_DOT} exists separately.
 * A derived ceiling needs <b>both</b> halves, and {@code max_stacks} is allowed here where the general stack-family
 * refusal would reject it.
 *
 * <p>Everything the run-time half needs is in {@link ApplyDotArgs}: the element, the frozen magnitude, the lifetime,
 * the optional name and the layer ceiling.
 */
final class ApplyDotOp implements OpHandler<ApplyDotArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.APPLY_DOT;
    }

    @Override
    public ApplyDotArgs parse(EffectSpec effect) {
        return new ApplyDotArgs(
                effect.getElement() == null ? "" : effect.getElement().trim(),
                effect,
                effect.getTurns(),
                effect.getBuff(),
                effect.getMaxStacks());
    }

    @Override
    public void validate(ApplyDotArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireElement(effect, op, rule, false);
        TriggerValidator.requireDotMagnitude(effect, op, rule);
        if (effect.getCapScale() != null || effect.getCapPercent() != null) {
            if (effect.getCapScale() == null || effect.getCapPercent() == null) {
                throw new IllegalArgumentException(
                        "Op " + op + " states a derived ceiling, which needs BOTH \"cap_scale\" (whose attribute) and "
                                + "\"cap_percent\" (that attribute's share) (source: " + rule.getSource() + ")");
            }
        }
        TriggerValidator.requirePositiveTurns(effect, op, rule);
        TriggerValidator.requireBaseChance(effect, op, rule);
        TriggerValidator.requireDotStackCap(effect, op, rule);
        if (Boolean.TRUE.equals(effect.getPermanent()) || TriggerLifetimes.eventBound(effect)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states a number of turns and nothing else; \"permanent\" / \"until\" "
                            + "would be a damage-over-time that never ends (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireNoMalformedArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, ApplyDotArgs args) {
        TriggerEffects.applyDot(ctx, args);
    }
}
