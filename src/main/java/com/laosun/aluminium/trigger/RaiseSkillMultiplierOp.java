package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.SkillType;

import java.util.Locale;

/**
 * {@code RAISE_SKILL_MULTIPLIER}: multiply one skill slot's row share for the rest of the battle.
 *
 * <p>Neither a zone nor a rule amendment: the row number is read at settlement, so only this op can say "the Ultimate's
 * damage multiplier is raised by 240%". The optional enemy-count window is what lets one sentence state disjoint tiers,
 * and an empty window is refused.
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
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireSkill(effect, op, rule);
        TriggerValidator.requirePercent(effect, op, rule);
        if (effect.getPercent() != null && effect.getPercent() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " raises a row multiplier, so \"percent\" must be positive (1.0 = x2.0); a "
                            + "negative share would be a different sentence (source: " + rule.getSource() + ")");
        }
        Integer min = effect.getMinEnemies();
        Integer max = effect.getMaxEnemies();
        if (min != null && min < 1) {
            throw new IllegalArgumentException(
                    "Op " + op + " bounds the enemy count, so \"min_enemies\" is at least 1 (source: "
                            + rule.getSource() + ")");
        }
        if (max != null && max < 1) {
            throw new IllegalArgumentException(
                    "Op " + op + " bounds the enemy count, so \"max_enemies\" is at least 1 (source: "
                            + rule.getSource() + ")");
        }
        if (min != null && max != null && max < min) {
            throw new IllegalArgumentException(
                    "Op " + op + " states an empty enemy window: min_enemies " + min + " > max_enemies " + max
                            + " (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        SkillType slot = SkillType.valueOf(ctx.effect().getSkill().trim().toUpperCase(Locale.ROOT));
        ctx.firing().owner().amendSkillMultiplier(slot, ctx.share(),
                ctx.effect().getMinEnemies(), ctx.effect().getMaxEnemies());
    }
}
