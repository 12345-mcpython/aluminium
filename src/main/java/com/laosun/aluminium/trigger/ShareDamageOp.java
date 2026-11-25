package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;

/**
 * {@code SHARE_DAMAGE}: one unit bears a share of the damage another would take.
 *
 * <p>The settled instance keeps only its own share ({@code percent} is removed from it) and the same share, element and
 * type is settled against the named unit through that unit's own zones. The share is the part the target <b>bears</b>,
 * so it is a fraction in (0,1), and the target is required rather than defaulted.
 */
final class ShareDamageOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.SHARE_DAMAGE;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireEvent(rule, op, TriggerEvent.DEALING_DAMAGE);
        TriggerValidator.requirePercent(effect, op, rule);
        if (effect.getPercent() <= 0 || effect.getPercent() >= 1) {
            throw new IllegalArgumentException(
                    "Op " + op + " states the share BORNE BY the target, so \"percent\" is a fraction in (0,1): "
                            + "0.65 = \"65% is shared\" (source: " + rule.getSource() + ")");
        }
        if (effect.getTarget() == null || effect.getTarget().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " needs a \"target\": the unit that bears the share (source: "
                            + rule.getSource() + ")");
        }
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.shareDamage(ctx.effect(), ctx.firing());
    }
}
