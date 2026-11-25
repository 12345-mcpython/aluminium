package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code SHARE_DAMAGE}: one unit bears a share of the damage another would take.
 *
 * <p>The settled instance keeps only its own share ({@code percent} is removed from it) and the same share, element and
 * type is settled against the named unit through that unit's own zones. The share is the part the target <b>bears</b>,
 * so it is a fraction in (0,1), and the target is required rather than defaulted.
 *
 * <p>The helper was inlined here when the share moved into {@link MagnitudeArgs}: what it held was the instance check,
 * the share, and a loop over {@code ctx.targets()}.
 */
final class ShareDamageOp implements OpHandler<MagnitudeArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.SHARE_DAMAGE;
    }

    @Override
    public MagnitudeArgs parse(EffectSpec effect) {
        return new MagnitudeArgs(effect);
    }

    @Override
    public void validate(MagnitudeArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireEvent(rule, op, com.laosun.aluminium.enums.TriggerEvent.DEALING_DAMAGE);
        TriggerValidator.requirePercent(effect, op, rule);
        Double percent = args.magnitude().getPercent();
        if (percent != null && (percent <= 0 || percent >= 1)) {
            throw new IllegalArgumentException(
                    "Op " + op + " states the share BORNE BY the target, so \"percent\" is a fraction in (0,1): "
                            + "0.65 = \"65% is shared\" (source: " + rule.getSource() + ")");
        }
        if (args.magnitude().getTarget() == null || args.magnitude().getTarget().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " needs a \"target\": the unit that bears the share (source: "
                            + rule.getSource() + ")");
        }
    }

    @Override
    public void apply(OpContext ctx, MagnitudeArgs args) {
        var damage = ctx.firing().damage();
        if (ctx.battle() == null || damage == null) {
            throw new IllegalStateException(
                    "SHARE_DAMAGE runs on DEALING_DAMAGE, the event that carries the instance being settled; there is none "
                            + "here (rule: " + ctx.firing().ruleId() + ")");
        }
        double share = TriggerMagnitudes.shareOf(args.magnitude(), ctx.firing());
        double base = damage.getSkillBaseValue() * share;
        damage.addReduction(share);
        for (CanHit receiver : ctx.targets()) {
            if (receiver == null || receiver.isDeath()) {
                continue;
            }
            ctx.battle().deferSharedDamage(damage.getAttacker(), receiver, base, damage.getElement());
        }
    }
}
