package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.SkillType;

import java.util.Locale;

/**
 * {@code RAISE_SKILL_MULTIPLIER}: multiply one skill slot's row share for the rest of the battle.
 *
 * <p>Neither a zone nor a rule amendment: the row number is read at settlement, so only this op can say "the Ultimate's
 * damage multiplier is raised by 240%".
 */
final class RaiseSkillMultiplierOp implements OpHandler<RaiseSkillMultiplierArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_SKILL_MULTIPLIER;
    }

    @Override
    public RaiseSkillMultiplierArgs parse(EffectSpec effect) {
        return new RaiseSkillMultiplierArgs(
                effect.getSkill() == null ? "" : effect.getSkill().trim(),
                effect,
                effect.getMinEnemies(),
                effect.getMaxEnemies());
    }

    @Override
    public void validate(RaiseSkillMultiplierArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireSkill(effect, op, rule);
        TriggerValidator.requirePercent(effect, op, rule);
        if (args.magnitude().getPercent() != null && args.magnitude().getPercent() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " raises a row multiplier, so \"percent\" must be positive (1.0 = x2.0); a "
                            + "negative share would be a different sentence (source: " + rule.getSource() + ")");
        }
        if (args.minEnemies() != null && args.minEnemies() < 1) {
            throw new IllegalArgumentException(
                    "Op " + op + " bounds the enemy count, so \"min_enemies\" is at least 1 (source: "
                            + rule.getSource() + ")");
        }
        if (args.maxEnemies() != null && args.maxEnemies() < 1) {
            throw new IllegalArgumentException(
                    "Op " + op + " bounds the enemy count, so \"max_enemies\" is at least 1 (source: "
                            + rule.getSource() + ")");
        }
        if (args.minEnemies() != null && args.maxEnemies() != null && args.maxEnemies() < args.minEnemies()) {
            throw new IllegalArgumentException(
                    "Op " + op + " states an empty enemy window: min_enemies " + args.minEnemies()
                            + " > max_enemies " + args.maxEnemies()
                            + " (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, RaiseSkillMultiplierArgs args) {
        SkillType slot = SkillType.valueOf(args.skill().toUpperCase(Locale.ROOT));
        ctx.firing().owner().amendSkillMultiplier(slot,
                TriggerMagnitudes.shareOf(args.magnitude(), ctx.firing()),
                args.minEnemies(), args.maxEnemies());
    }
}
