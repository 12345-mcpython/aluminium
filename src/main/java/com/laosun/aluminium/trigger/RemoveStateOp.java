package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code REMOVE_STATE}: drop one named state, without the class question {@code DISPEL} asks.
 *
 * <p>{@code buff} is the state's name - the same spelling {@code has_state} reads and {@code APPLY_BUFF} writes, so the
 * three are one vocabulary. {@code attribute} is the other filter, and {@code kind: "own"} is the origin filter
 * {@code EXTEND_BUFF} has always had. No count: "remove ... state" takes the state off, it does not take N of them.
 */
final class RemoveStateOp implements OpHandler<RemoveStateArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.REMOVE_STATE;
    }

    @Override
    public RemoveStateArgs parse(EffectSpec effect) {
        return new RemoveStateArgs(effect.getBuff(), effect.getAttribute(), effect.getKind());
    }

    @Override
    public void validate(RemoveStateArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (effect.getAttribute() != null && !effect.getAttribute().isBlank()) {
            TriggerValidator.requireAttribute(effect, op, rule);
        } else {
            TriggerValidator.requireBuff(effect, op, rule);
        }
        if (effect.getKind() != null && !effect.getKind().isBlank()
                && !"own".equalsIgnoreCase(effect.getKind().trim())) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"kind\": \"" + effect.getKind()
                            + "\"; the only kind it knows is \"own\" (only what this rule's owner applied)"
                            + " (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        if (effect.getAmount() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " takes the named state off entirely and has no \"amount\" (it states "
                            + effect.getAmount() + "); write one effect per state to remove "
                            + "(source: " + rule.getSource() + ")");
        }
    }

    @Override
    public void apply(OpContext ctx, RemoveStateArgs args) {
        TriggerEffects.removeState(ctx, args);
    }
}
