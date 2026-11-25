package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code COMMAND_SUMMON}: order a summon to cast one of its skills.
 *
 * <p>The numbers are the NAMED SKILL's, not this file's: stating them here would be a second copy that drifts (and would
 * lose the per-level table). The victims come from the skill's own shape, not from a selector - pointing this op at
 * {@code self} or at a teammate would deal the summon's damage to our own side.
 */
final class CommandSummonOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.COMMAND_SUMMON;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireSkill(effect, op, rule);
        TriggerValidator.requireDamageParam(effect, op, rule);
        TriggerValidator.requireAttribute(effect, op, rule);
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        TriggerValidator.requireNoTarget(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.commandSummon(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
