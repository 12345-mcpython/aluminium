package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;

/**
 * {@code MODIFY_RULE}: amend another rule's own numbers (its per-turn limit, duration, stack cap, effect value or base
 * chance).
 *
 * <p>Which number is decided by <b>which field is stated</b>: an {@code amount} is a count of firings, a {@code percent}
 * is a probability, and each other spelling names its own amendment. Exactly one is allowed. The shape of the named rule
 * is checked by {@code TriggerTable.validateAmendments}, the only place that can see the whole file. Only
 * {@code BATTLE_START}, {@code CAST_SETUP} and {@code SUMMONED} may file one: a bonus granted mid-battle would have to
 * be taken back when whatever granted it ended, and nothing does that.
 */
final class ModifyRuleOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.MODIFY_RULE;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireRuleReference(effect, op, rule);
        TriggerValidator.requireOneAmendment(effect, op, rule);
        if (effect.getAmount() != null) {
            double amount = effect.getAmount();
            if (amount < 1 || amount != Math.floor(amount)) {
                throw new IllegalArgumentException(
                        "Op " + op + " raises a rule's per-turn limit, so \"amount\" is a count of firings: "
                                + "a whole number >= 1, got " + amount + " (a probability is a \"percent\", "
                                + "which is a different number on a different rule) "
                                + "(source: " + rule.getSource() + ")");
            }
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "amount");
        } else if (effect.getEffectTurns() != null) {
            if (effect.getEffectTurns() == 0) {
                throw new IllegalArgumentException(
                        "Op " + op + " raises a rule's effect duration, so \"effect_turns\" must be a whole "
                                + "number of turns other than 0, got 0"
                                + " (source: " + rule.getSource() + ")");
            }
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "effect_turns");
        } else if (effect.getEffectMaxStacks() != null) {
            if (effect.getEffectMaxStacks() == 0) {
                throw new IllegalArgumentException(
                        "Op " + op + " raises a rule's stack cap, so \"effect_max_stacks\" must not be 0 "
                                + "(source: " + rule.getSource() + ")");
            }
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "effect_max_stacks");
        } else if (effect.getEffectPercent() != null) {
            if (effect.getEffectPercent() == 0) {
                throw new IllegalArgumentException(
                        "Op " + op + " raises a rule's effect value, so \"effect_percent\" must not be 0 "
                                + "(source: " + rule.getSource() + ")");
            }
        } else if (effect.getEffectAmount() != null) {
            if (effect.getEffectAmount() == 0) {
                throw new IllegalArgumentException(
                        "Op " + op + " raises the layers an application grants, so \"effect_amount\" must not be 0"
                                + " (source: " + rule.getSource() + ")");
            }
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "effect_amount");
        } else if (effect.getEffectPercentScale() != null) {
            if (!(effect.getEffectPercentScale() > 0)) {
                throw new IllegalArgumentException(
                        "Op " + op + " scales a rule effect values, so \"effect_percent_scale\" is a positive factor "
                                + "(1.15 = \"increased by 15%\"); a non-positive factor would flip or erase them "
                                + "(source: " + rule.getSource() + ")");
            }
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "effect_percent_scale");
        } else if (effect.getEffectPercentFromResource() != null) {
            // A fifth amendment spelling: the SIZE is a resource, in basis points. Not `requireResource`: that checks
            // `resource`, and this spelling names its resource elsewhere.
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "effect_percent_from_resource");
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "effect_percent");
        } else if (effect.getCooldownDelta() != null) {
            // "天赋的冷却时间减少 1 回合": a delta in whole turns, and zero is not a delta - the same reason
            // `effect_turns` refuses 0.
            if (effect.getCooldownDelta() == 0) {
                throw new IllegalArgumentException(
                        "Op " + op + " amends a rule's cooldown, so \"cooldown_delta\" is a whole number of "
                                + "turns other than 0 (negative lowers it) (source: " + rule.getSource() + ")");
            }
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "cooldown_delta");
        } else {
            TriggerValidator.requirePercent(effect, op, rule);
            if (effect.getPercent() <= 0 || effect.getPercent() > 1) {
                throw new IllegalArgumentException(
                        "Op " + op + " raises a rule's base chance, so \"percent\" is a fraction of 1 in "
                                + "(0, 1]: 0.15 = \"raised by 15%\", got " + effect.getPercent()
                                + " (source: " + rule.getSource() + ")");
            }
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "percent");
        }
        TriggerEvent firesOn = TriggerEvent.fromString(rule.getOn());
        if (firesOn != TriggerEvent.BATTLE_START && firesOn != TriggerEvent.CAST_SETUP
                && firesOn != TriggerEvent.SUMMONED) {
            throw new IllegalArgumentException(
                    "Op " + op + " amends a rule for the whole battle, so it only makes sense on "
                            + "BATTLE_START (a bonus granted mid-battle would have to be taken back when "
                            + "whatever granted it ended, and nothing does that); this rule fires on "
                            + rule.getOn() + " (source: " + rule.getSource() + ")");
        }
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.modifyRule(ctx.effect(), ctx.firing());
    }
}
