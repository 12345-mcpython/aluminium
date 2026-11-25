package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code RAISE_SKILL_HITS}: add damage segments to a data slot, for the rest of the battle.
 *
 * <p>The DATA SLOT, not a {@code SkillType} letter: the executor knows the slot number. A negative amount takes the
 * segments back, which is how a rule states a duration.
 */
final class RaiseSkillHitsOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_SKILL_HITS;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (effect.getSkillId() == null || effect.getSkillId() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " needs \"skill_id\": the data slot whose hit count changes (source: "
                            + rule.getSource() + ")");
        }
        TriggerValidator.requireAmount(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.raiseSkillHits(ctx.effect(), ctx.firing());
    }
}
