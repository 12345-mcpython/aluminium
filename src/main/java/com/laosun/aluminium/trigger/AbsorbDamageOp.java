package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.buff.AbsorbBuff;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code ABSORB_DAMAGE}: install a counter-backed absorber on the protected unit.
 *
 * <p>The grant rides on the unit the hit lands on, while the counter belongs to whoever pays - and {@code payer}
 * states that, defaulting to the rule's owner. Three things must be stated, because each one is a silent no-op when
 * missing: an absorber with no pool blocks nothing, one with no rate cannot turn points into damage, and one with no
 * share would block the whole hit.
 */
final class AbsorbDamageOp implements OpHandler<AbsorbDamageArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.ABSORB_DAMAGE;
    }

    @Override
    public AbsorbDamageArgs parse(EffectSpec effect) {
        boolean permanent = TriggerLifetimes.unticked(effect);
        return new AbsorbDamageArgs(
                effect.getResource() == null ? "" : effect.getResource().trim(),
                effect.getUnitAmount() == null ? 0 : effect.getUnitAmount(),
                effect,
                effect.getPayer(),
                permanent ? TriggerEffects.UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns(),
                permanent);
    }

    @Override
    public void validate(AbsorbDamageArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (args.resource().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"resource\" (which counter pays for the blocked damage)"
                            + " (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireResource(effect, op, rule);
        if (args.unitAmount() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires a positive \"unit_amount\" (how much damage one point of '"
                            + args.resource() + "' is worth), but has " + effect.getUnitAmount()
                            + " (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requirePercent(effect, op, rule);
        TriggerValidator.requireValueFraction(effect, op, rule);
        Double percent = args.magnitude().getPercent();
        if (percent != null && percent > 1) {
            throw new IllegalArgumentException(
                    "Op " + op + " takes \"percent\" as the SHARE of the hit that may be blocked, in (0, 1]"
                            + " - not a number out of 100; got " + percent
                            + " (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, AbsorbDamageArgs args) {
        double share = TriggerMagnitudes.statesShare(args.magnitude())
                ? TriggerMagnitudes.shareOf(args.magnitude(), ctx.firing())
                : args.magnitude().getPercent();
        CanHit payer = args.payer() == null || args.payer().isBlank()
                ? ctx.firing().owner()
                : ctx.requireTarget("target");
        for (CanHit target : ctx.targets()) {
            if (target == null || target.isDeath()) {
                continue;
            }
            target.getBuffManager().addBuff(TriggerInterpreter.withSource(
                    TriggerLifetimes.withLifetime(
                            new AbsorbBuff(args.turns(), args.permanent(), args.resource(),
                                    args.unitAmount(), share, payer),
                            ctx.effect()),
                    ctx.firing()));
        }
    }
}
