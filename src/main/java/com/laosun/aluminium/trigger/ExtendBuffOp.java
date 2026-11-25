package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code EXTEND_BUFF}: lengthen a state that is already on the unit.
 *
 * <p>The mirror of a duration and not of a grant: applying the state again would replace it, which is why "one more
 * turn" needs its own op. It names the buffs by state or attribute and adds turns to them; {@code permanent} /
 * {@code until} describe how a NEW buff ends and are not read here.
 */
final class ExtendBuffOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.EXTEND_BUFF;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireSignedTurns(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        if (Boolean.TRUE.equals(effect.getPermanent()) || TriggerLifetimes.eventBound(effect)) {
            throw new IllegalArgumentException(
                    "Op " + op + " adds turns to a buff that already exists; \"permanent\" / \"until\" "
                            + "describe how a NEW buff ends and are not read here "
                            + "(source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireExtendFilter(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.extendBuff(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
