package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.enums.TriggerEvent;

/**
 * {@code MODIFY_RULE}: amend another rule's own numbers (its per-turn limit, duration, stack cap, effect value or base
 * chance).
 *
 * <p>Which number is decided by <b>which field is stated</b>: an {@code amount} is a count of firings, a {@code percent}
 * is a probability, and each other spelling names its own amendment. Exactly one is allowed ({@link ModifyRuleArgs}
 * records which). The shape of the named rule is checked by {@code TriggerTable.validateAmendments}, the only place that
 * can see the whole file. Only {@code BATTLE_START}, {@code CAST_SETUP} and {@code SUMMONED} may file one: a bonus
 * granted mid-battle would have to be taken back when whatever granted it ended, and nothing does that.
 */
final class ModifyRuleOp implements OpHandler<ModifyRuleArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.MODIFY_RULE;
    }

    @Override
    public ModifyRuleArgs parse(EffectSpec effect) {
        return new ModifyRuleArgs(effect.getRule() == null ? "" : effect.getRule().trim(),
                effect.getAmount(),
                effect.getEffectTurns(),
                effect.getEffectMaxStacks(),
                effect.getEffectPercent(),
                effect.getEffectPercentScale(),
                effect.getEffectAmount(),
                effect.getEffectPercentFromResource(),
                effect.getCooldownDelta(),
                effect.getPercent());
    }

    @Override
    public void validate(ModifyRuleArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireRuleReference(effect, op, rule);
        TriggerValidator.requireOneAmendment(effect, op, rule);
        if (args.amount() != null) {
            double amount = args.amount();
            if (amount < 1 || amount != Math.floor(amount)) {
                throw new IllegalArgumentException(
                        "Op " + op + " raises a rule's per-turn limit, so \"amount\" is a count of firings: "
                                + "a whole number >= 1, got " + amount + " (a probability is a \"percent\", "
                                + "which is a different number on a different rule) "
                                + "(source: " + rule.getSource() + ")");
            }
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "amount");
        } else if (args.effectTurns() != null) {
            if (args.effectTurns() == 0) {
                throw new IllegalArgumentException(
                        "Op " + op + " raises a rule's effect duration, so \"effect_turns\" must be a whole "
                                + "number of turns other than 0, got 0"
                                + " (source: " + rule.getSource() + ")");
            }
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "effect_turns");
        } else if (args.effectMaxStacks() != null) {
            if (args.effectMaxStacks() == 0) {
                throw new IllegalArgumentException(
                        "Op " + op + " raises a rule's stack cap, so \"effect_max_stacks\" must not be 0 "
                                + "(source: " + rule.getSource() + ")");
            }
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "effect_max_stacks");
        } else if (args.effectPercent() != null) {
            if (args.effectPercent() == 0) {
                throw new IllegalArgumentException(
                        "Op " + op + " raises a rule's effect value, so \"effect_percent\" must not be 0 "
                                + "(source: " + rule.getSource() + ")");
            }
        } else if (args.effectAmount() != null) {
            if (args.effectAmount() == 0) {
                throw new IllegalArgumentException(
                        "Op " + op + " raises the layers an application grants, so \"effect_amount\" must not be 0"
                                + " (source: " + rule.getSource() + ")");
            }
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "effect_amount");
        } else if (args.effectPercentScale() != null) {
            if (!(args.effectPercentScale() > 0)) {
                throw new IllegalArgumentException(
                        "Op " + op + " scales a rule effect values, so \"effect_percent_scale\" is a positive factor "
                                + "(1.15 = \"increased by 15%\"); a non-positive factor would flip or erase them "
                                + "(source: " + rule.getSource() + ")");
            }
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "effect_percent_scale");
        } else if (args.effectPercentFromResource() != null) {
            // A fifth amendment spelling: the SIZE is a resource, in basis points. Not `requireResource`: that checks
            // `resource`, and this spelling names its resource elsewhere.
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "effect_percent_from_resource");
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "effect_percent");
        } else if (args.cooldownDelta() != null) {
            // "天赋的冷却时间减少 1 回合": a delta in whole turns, and zero is not a delta - the same reason
            // `effect_turns` refuses 0.
            if (args.cooldownDelta() == 0) {
                throw new IllegalArgumentException(
                        "Op " + op + " amends a rule's cooldown, so \"cooldown_delta\" is a whole number of "
                                + "turns other than 0 (negative lowers it) (source: " + rule.getSource() + ")");
            }
            TriggerValidator.rejectAmendmentExtras(effect, op, rule, "cooldown_delta");
        } else {
            TriggerValidator.requirePercent(effect, op, rule);
            if (args.percent() <= 0 || args.percent() > 1) {
                throw new IllegalArgumentException(
                        "Op " + op + " raises a rule's base chance, so \"percent\" is a fraction of 1 in "
                                + "(0, 1]: 0.15 = \"raised by 15%\", got " + args.percent()
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
    public void apply(OpContext ctx, ModifyRuleArgs args) {
        CanHit owner = ctx.firing().owner();
        if (owner == null) {
            throw new IllegalStateException(
                    "MODIFY_RULE ran without a rule owner, so there is no combatant to file the amendment under; this "
                            + "is an engine fault (the op is validated onto BATTLE_START, which always has one)");
        }
        String target = args.rule();
        if (args.amount() != null) {
            owner.addRulePerTurnBonus(target, (int) Math.round(args.amount()));
        } else if (args.effectTurns() != null) {
            // "the Ultimate's duration is additionally increased by 1 turn": the rule's own effect durations, raised
            // where they are read (see amendedEffect). Not an extra rule with a longer `turns`: the two modifiers of
            // one skill are the same kind, so a second one would REPLACE the first rather than stack.
            owner.amendRuleEffectTurns(target, args.effectTurns());
        } else if (args.effectMaxStacks() != null) {
            // "raise the talent's effect max stack count by 2": a CAP, not a value - and it must be in place before
            // any stack is attached, which it is: an amendment is filed at battle start.
            owner.amendRuleEffectMaxStacks(target, args.effectMaxStacks());
        } else if (args.effectPercent() != null) {
            owner.amendRuleEffectPercent(target, args.effectPercent());
        } else if (args.effectPercentScale() != null) {
            owner.amendRuleEffectPercentScale(target, args.effectPercentScale());
        } else if (args.effectAmount() != null) {
            // "本次叠加的层数额外 +1 层": the LAYER COUNT an application grants.
            owner.amendRuleEffectAmount(target, args.effectAmount());
        } else if (args.effectPercentFromResource() != null) {
            // The SAME amendment, sized by a resource in basis points ("for each 1% of overflow spent...").
            int basisPoints = TriggerInterpreter.resourceAmount(ctx.battle(), owner,
                    args.effectPercentFromResource().trim());
            owner.amendRuleEffectPercent(target, basisPoints / 10000.0);
        } else if (args.cooldownDelta() != null) {
            // "天赋的冷却时间减少 1 回合": a delta on the number the named rule states. Read where the cooldown is
            // recorded, so the rule keeps saying "2" and the Eidolon is the correction.
            owner.addRuleCooldownDelta(target, args.cooldownDelta());
        } else {
            owner.addRuleBaseChanceBonus(target, args.percent());
        }
    }
}
