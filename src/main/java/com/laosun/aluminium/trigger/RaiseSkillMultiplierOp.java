package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.SkillType;

import java.util.Locale;

/**
 * {@code RAISE_SKILL_MULTIPLIER}: multiply one skill slot's row share for the rest of the battle.
 *
 * <p>Neither a zone nor a rule amendment: the row number is read at settlement, so only this op can say "the Ultimate's
 * damage multiplier is raised by 240%". The enemy-count bounds let one sentence state disjoint tiers. Registered here so
 * the op has one dispatch path; its load-time checks are still in {@code TriggerValidator}.
 */
final class RaiseSkillMultiplierOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_SKILL_MULTIPLIER;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        SkillType slot = SkillType.valueOf(ctx.effect().getSkill().trim().toUpperCase(Locale.ROOT));
        ctx.firing().owner().amendSkillMultiplier(slot, ctx.share(),
                ctx.effect().getMinEnemies(), ctx.effect().getMaxEnemies());
    }
}
