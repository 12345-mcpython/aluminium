package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code REPLACE_SKILL}: swap one of the owner's skill slots, or put the original back.
 *
 * <p>Two directions with different fields. Installing needs a slot, the data row of the replacement and a lifetime;
 * {@code { remove: true }} needs none of them - the lifetime was the install's and the removal is immediate - so
 * stating any would be a field with no reader.
 */
final class ReplaceSkillOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.REPLACE_SKILL;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireSkill(effect, op, rule);
        if (Boolean.TRUE.equals(effect.getRemove())) {
            if (effect.getSkillId() != null) {
                throw new IllegalArgumentException(
                        "Op " + op + " with \"remove\": true must not state \"skill_id\": there is nothing to "
                                + "install (source: " + rule.getSource() + ")");
            }
            if (effect.getTurns() != null || effect.getUntil() != null
                    || Boolean.TRUE.equals(effect.getPermanent())) {
                throw new IllegalArgumentException(
                        "Op " + op + " with \"remove\": true takes the swap off at once, so a lifetime says "
                                + "nothing (source: " + rule.getSource() + ")");
            }
            TriggerValidator.requireNoStackArguments(effect, op, rule);
            return;
        }
        if (effect.getSkillId() == null || effect.getSkillId() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " needs \"skill_id\": the data row of the replacement skill (an enhanced attack is "
                            + "its own row and belongs to no slot) (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.replaceSkill(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
