package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;

/**
 * {@code RESET_TRIGGER_LIMIT}: clear one rule's firing limit on the target.
 *
 * <p>Names the rule by <b>id</b>, and refuses an id the unit does not carry: an id that matches nothing would clear
 * nothing, silently.
 */
final class ResetTriggerLimitOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RESET_TRIGGER_LIMIT;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        if (effect.getRule() == null || effect.getRule().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op().name() + " requires \"rule\" (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireNoStackArguments(effect, op().name(), rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        String wanted = ctx.effect().getRule().trim();
        for (CanHit cleared : ctx.targets()) {
            TriggerTable table = cleared instanceof Character character ? character.getTriggerTable() : null;
            String limitKey = table == null ? null : table.keyOf(wanted);
            if (limitKey == null) {
                throw new IllegalStateException(
                        "Op RESET_TRIGGER_LIMIT names the rule " + wanted + ", which " + cleared.getName()
                                + " does not carry -- an id that matches nothing would clear nothing, silently");
            }
            cleared.resetTriggerLimit(limitKey);
        }
    }
}
