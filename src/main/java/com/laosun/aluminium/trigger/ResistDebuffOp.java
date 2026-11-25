package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code RESIST_DEBUFF}: raise one unit's resistance to a class of negative effect.
 *
 * <p>Two shapes. A <b>charge shield</b> ({@code charges} + {@code cooldown}) has no percentage and no duration of its
 * own - the cooldown is its clock - while a class resistance is a buff on the carrier (timed, or permanent for a
 * trace). A resistance of 0 is refused: it is a rule that provably does nothing.
 */
final class ResistDebuffOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RESIST_DEBUFF;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (effect.getCharges() != null) {
            if (effect.getCharges() < 1) {
                throw new IllegalArgumentException(
                        "Op " + op + " has \"charges\": " + effect.getCharges()
                                + ", but a charge shield must refuse at least one application (source: "
                                + rule.getSource() + ")");
            }
            if (effect.getCooldown() == null || effect.getCooldown() < 0) {
                throw new IllegalArgumentException(
                        "Op " + op + " states \"charges\" but no non-negative \"cooldown\" (the turns "
                                + "before the charge comes back) (source: " + rule.getSource() + ")");
            }
            TriggerValidator.requireNoStackArguments(effect, op, rule);
            return;
        }
        TriggerValidator.requireDebuffClass(effect, op, rule);
        TriggerValidator.requirePercent(effect, op, rule);
        TriggerValidator.requireValueFraction(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        TriggerValidator.requireDuration(effect, op, rule);
        TriggerValidator.requireTickOwner(effect, op, rule);
        // `buff` is READ: the name the resistance carries, so "immune to control-class while in the [协奏] state" can
        // be taken off together with the state it belongs to. Everything else is refused.
        if (effect.getAmount() != null || effect.getScale() != null || effect.getAttribute() != null
                || effect.getSkill() != null
                || effect.getDamageParam() != null || effect.getElement() != null
                || effect.getControl() != null || effect.getBaseChance() != null) {
            throw new IllegalArgumentException(
                    "Op " + op + " takes a class (\"kind\") and a \"percent\", plus how long it lasts "
                            + "(and an optional \"buff\" name so a state's own effect can be removed with "
                            + "the state); it has no amount / scale / attribute / skill / element / control / "
                            + "base_chance (source: " + rule.getSource() + ")");
        }
        if (effect.getPercent() != null && (effect.getPercent() <= 0 || effect.getPercent() > 1)) {
            throw new IllegalArgumentException(
                    "Op " + op + " has \"percent\": " + effect.getPercent() + ", but a class resistance is "
                            + "a fraction of 1 in (0, 1] (0.35 = \"raise the chance to resist ... by 35%\", 1.0 = \"immune\"); a "
                            + "resistance of 0 is a rule that provably does nothing "
                            + "(source: " + rule.getSource() + ")");
        }
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.resistDebuff(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
