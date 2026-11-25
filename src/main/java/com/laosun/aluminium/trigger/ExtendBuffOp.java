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
final class ExtendBuffOp implements OpHandler<ExtendBuffArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.EXTEND_BUFF;
    }

    @Override
    public ExtendBuffArgs parse(EffectSpec effect) {
        return new ExtendBuffArgs(effect.getTurns(), effect.getKind(), effect.getBuff(), effect.getAttribute());
    }

    @Override
    public void validate(ExtendBuffArgs args, EffectSpec effect, TriggerSpec rule) {
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
    public void apply(OpContext ctx, ExtendBuffArgs args) {
        TriggerEffects.extendBuff(ctx, args);
    }
}
