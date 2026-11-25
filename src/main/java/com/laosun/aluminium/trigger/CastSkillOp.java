package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.SkillType;

import java.util.Locale;

/**
 * {@code CAST_SKILL}: perform a whole skill as an effect of a rule.
 *
 * <p>The escape hatch for "immediately casts X": it runs the normal cast pipeline, so the skill's own zones, targets and
 * misses all apply. It names the skill either as a {@code SkillType} slot or as a memosprite data slot, and refuses the
 * row fields - the named skill is executed by the engine's own path, so there is no column to name.
 */
final class CastSkillOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.CAST_SKILL;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (effect.getSkillId() == null && (effect.getSkill() == null || effect.getSkill().isBlank())) {
            throw new IllegalArgumentException("Op " + op + " requires skill (a SkillType slot name) or skill_id "
                    + "(a memosprite data slot) (source: " + rule.getSource() + ")");
        }
        if (effect.getSkill() == null || effect.getSkill().isBlank()) {
            TriggerValidator.requireNoRowArguments(effect, op, rule);
            return;
        }
        try {
            SkillType.valueOf(effect.getSkill().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException notASlot) {
            throw new IllegalArgumentException("Op " + op + " names skill \"" + effect.getSkill()
                    + "\", which is not a SkillType (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireNoRowArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.castSkill(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
