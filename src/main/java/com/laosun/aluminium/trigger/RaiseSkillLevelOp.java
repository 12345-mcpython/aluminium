package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;

/**
 * {@code RAISE_SKILL_LEVEL}: raise one of the owner's skill slots by N levels for this battle.
 *
 * <p>The raise is stored per combatant, not on the skill instance - a skill belongs to a {@code Character} that a stage
 * can put into more than one battle, so writing the level onto it would stack once per battle and never come off.
 * Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator.validateOne}.
 */
final class RaiseSkillLevelOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_SKILL_LEVEL;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.raiseSkillLevel(ctx.effect(), ctx.firing());
    }
}
