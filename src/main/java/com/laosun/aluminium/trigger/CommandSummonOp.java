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
final class CommandSummonOp implements OpHandler<CommandSummonArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.COMMAND_SUMMON;
    }

    @Override
    public CommandSummonArgs parse(EffectSpec effect) {
        return new CommandSummonArgs(effect.getSkill() == null ? "" : effect.getSkill().trim(), effect.getAttribute(), effect);
    }

    @Override
    public void validate(CommandSummonArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireSkill(effect, op, rule);
        TriggerValidator.requireDamageParam(effect, op, rule);
        TriggerValidator.requireAttribute(effect, op, rule);
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        TriggerValidator.requireNoTarget(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, CommandSummonArgs args) {
        TriggerEffects.commandSummon(ctx, args);
    }
}
