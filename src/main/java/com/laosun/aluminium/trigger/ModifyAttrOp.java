package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;

/**
 * {@code MODIFY_ATTR}: a stat modifier for a stated lifetime.
 *
 * <p>The one op that takes a ratio attribute ({@code CRIT_ATTACK} and friends) as the value itself rather than as an
 * additive percentage - the sign decides buff versus debuff, and {@code TriggerEffects.modifyAttr} is where both live.
 *
 * <p><b>"Exactly one magnitude" is the rule this check enforces.</b> A modifier is a share, a flat value, a whole
 * {@code value} expression, or a derived {@code scale}: a derived one reads {@code percent x attribute + amount}, a
 * plain one is either a share or a flat value, and writing both of those would silently let one win. {@code per_stack}
 * multiplies that number, so it cannot sit beside a {@code scale} (a second multiplier on the same value).
 */
final class ModifyAttrOp implements OpHandler<ModifyAttrArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.MODIFY_ATTR;
    }

    @Override
    public ModifyAttrArgs parse(EffectSpec effect) {
        boolean permanent = TriggerLifetimes.unticked(effect);
        // ⚠ `Integer.valueOf` on the placeholder: a `?:` mixing it with the boxed `getTurns()` would unbox a null and
        // NPE before this op's own validator could refuse a missing duration.
        Integer turns = permanent
                ? Integer.valueOf(TriggerEffects.UNBOUNDED_DURATION_PLACEHOLDER)
                : effect.getTurns();
        return new ModifyAttrArgs(
                effect.getAttribute(),
                effect.getBuff(),
                turns,
                permanent,
                effect.getRatio(),
                Boolean.TRUE.equals(effect.getInstance()),
                effect.getDamageType(),
                Boolean.TRUE.equals(effect.getCoexist()),
                effect.getWhileState(),
                effect.getWhileStacks(),
                effect.getWhileStacksMin(),
                effect.getWhileHolderState(),
                effect.getWhileHolderHpPercent(),
                effect.getWhileHpPercent(),
                effect.getWhileResource(),
                effect.getWhileResourceMin(),
                effect.getWhileSummonCount());
    }

    @Override
    public void validate(ModifyAttrArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        // A damage-type scope is only read on the INSTANCE route; stating it on an ordinary modifier would be silently
        // ignored, which is the hole the closed-set checks exist to close.
        if (effect.getDamageType() != null && !effect.getDamageType().isBlank()
                && !Boolean.TRUE.equals(effect.getInstance())) {
            throw new IllegalArgumentException(
                    "Op " + op + " states \"damage_type\" without \"instance\": true; only an instance-scoped modifier reads it "
                            + "(source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireAttribute(effect, op, rule);
        if (effect.getRatio() != null) {
            AttributeType ratioAttribute = AttributeType.fromString(effect.getAttribute().trim());
            if (!ratioAttribute.isPercent) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"ratio\": " + effect.getRatio() + " on the flat attribute "
                                + effect.getAttribute() + "; ratio is only for ratio attributes "
                                + "(CRIT_CHANCE / DAMAGE_BOOST / ...). A flat attribute uses \"amount\" "
                                + "(points) or \"percent\" (a share of its base) "
                                + "(source: " + rule.getSource() + ")");
            }
            if (effect.getPercent() != null || effect.getAmount() != null
                    || (effect.getScale() != null && !effect.getScale().isBlank())
                    || effect.getPercentFrom() != null || effect.getPercentFromCastParam() != null
                    || effect.getPercentFromSkillParam() != null || effect.getPercentFromResource() != null
                    || effect.getPercentFromSummonParam() != null || effect.getValue() != null) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"ratio\" beside another magnitude spelling; ratio is the whole "
                                + "number for a ratio attribute (5 = +500%), so state it alone "
                                + "(source: " + rule.getSource() + ")");
            }
        }
        boolean derivedModifier = effect.getScale() != null && !effect.getScale().isBlank();
        if (effect.getRatio() != null || effect.getValue() != null) {
            // Validated above / centrally: one absolute fraction, or one whole magnitude expression.
        } else if (derivedModifier) {
            TriggerValidator.requirePercent(effect, op, rule);
        } else if ((effect.getPercent() == null && effect.getPercentFrom() == null
                && effect.getPercentFromCastParam() == null
                && effect.getPercentFromSkillParam() == null && effect.getPercentFromResource() == null
                && effect.getPercentFromSummonParam() == null)
                // "使我方全体速度提高50点" reads that 50 out of the skill's own row, because the column moves with the
                // level: a level-aware FLAT spelling is still the flat half, so amount_from_skill_param counts as one.
                == (effect.getAmount() == null && effect.getAmountFromSkillParam() == null)) {
            throw new IllegalArgumentException(
                    "Op " + op + " needs exactly one of \"percent\" (a share) or \"amount\" (a flat value) unless it also states a \"scale\", in which "
                            + "case both are read as percent x scale + amount; it states "
                            + ((effect.getPercent() == null && effect.getPercentFrom() == null && effect.getAmount() == null) ? "neither" : "both")
                            + " (source: " + rule.getSource() + ")");
        }
        // The share may be a number provider: validated here because the branch above never reaches requirePercent.
        NumberProviders.validate(effect.getPercentFrom(), "percent_from", op, rule);
        if (effect.getPercentFrom() != null && (effect.getPercentFromCastParam() != null
                || effect.getPercentFromSkillParam() != null || effect.getPercentFromResource() != null
                || effect.getPercentFromSummonParam() != null)) {
            throw new IllegalArgumentException("Op " + op + " states a share twice: \"percent_from\" is the"
                    + " general spelling and replaces \"percent_from_cast_param\" / \"percent_from_skill_param\" /"
                    + " \"percent_from_summon_param\" / \"percent_from_resource\" (source: " + rule.getSource() + ")");
        }
        // A derived ceiling on a modifier: both fields, or neither.
        if (effect.getCapAmount() != null
                && (effect.getCapScale() != null || effect.getCapPercent() != null)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states BOTH a derived ceiling and a constant \"cap_amount\"; state one "
                            + "(source: " + rule.getSource() + ")");
        }
        if ((effect.getCapScale() != null || effect.getCapPercent() != null)
                && (effect.getCapScale() == null || effect.getCapPercent() == null)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states a derived ceiling, which needs BOTH \"cap_scale\" and \"cap_percent\" "
                            + "(source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireDerivedScale(effect, op, rule, true);
        if (effect.getPerStackCap() != null) {
            if (effect.getPerStack() == null || effect.getPerStack().isBlank()) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"per_stack_cap\" but no \"per_stack\": there is no layer to cap (source: "
                                + rule.getSource() + ")");
            }
            if (effect.getPerStackCap() < 1) {
                throw new IllegalArgumentException(
                        "Op " + op + " caps per_stack at " + effect.getPerStackCap() + ", which is below one layer "
                                + "(source: " + rule.getSource() + ")");
            }
        }
        if (effect.getPerStack() != null && !effect.getPerStack().isBlank()) {
            if (derivedModifier) {
                throw new IllegalArgumentException(
                        "Op " + op + " states BOTH \"scale\" and \"per_stack\" (source: " + rule.getSource() + ")");
            }
            if (effect.getPercent() == null && effect.getValue() == null && effect.getRatio() == null
                    && !TriggerMagnitudes.statesShare(effect)) {
                // A share may also be READ from the skill row ("每层蓄能使我方全体攻击力提高14%", whose column moves with
                // the level) - the same spellings the magnitude reader accepts, asked of its own predicate.
                throw new IllegalArgumentException(
                        "Op " + op + " states \"per_stack\" without \"percent\", \"ratio\" or \"value\" (source: "
                                + rule.getSource() + ")");
            }
        }
        TriggerValidator.requireDuration(effect, op, rule);
        TriggerValidator.requireStackCap(effect, op, rule);
        TriggerValidator.requireTickOwner(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, ModifyAttrArgs args) {
        TriggerEffects.modifyAttr(args, ctx.effect(), ctx.battle(), ctx.firing(), ctx.effect());
    }
}
