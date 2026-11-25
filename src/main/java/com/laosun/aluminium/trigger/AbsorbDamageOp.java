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
final class AbsorbDamageOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.ABSORB_DAMAGE;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (effect.getResource() == null || effect.getResource().isBlank()) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires \"resource\" (which counter pays for the blocked damage)"
                            + " (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireResource(effect, op, rule);
        if (effect.getUnitAmount() == null || effect.getUnitAmount() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires a positive \"unit_amount\" (how much damage one point of '"
                            + effect.getResource().trim() + "' is worth), but has "
                            + effect.getUnitAmount() + " (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requirePercent(effect, op, rule);
        TriggerValidator.requireValueFraction(effect, op, rule);
        if (effect.getPercent() != null && effect.getPercent() > 1) {
            throw new IllegalArgumentException(
                    "Op " + op + " takes \"percent\" as the SHARE of the hit that may be blocked, in (0, 1]"
                            + " - not a number out of 100; got " + effect.getPercent()
                            + " (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        EffectSpec effect = ctx.effect();
        String resource = effect.getResource().trim();
        double unitAmount = effect.getUnitAmount();
        double share = TriggerMagnitudes.statesShare(effect) ? ctx.share() : effect.getPercent();
        CanHit payer = effect.getPayer() == null || effect.getPayer().isBlank()
                ? ctx.firing().owner()
                : ctx.requireTarget("target");
        boolean permanent = TriggerLifetimes.unticked(effect);
        int turns = permanent ? TriggerEffects.UNBOUNDED_DURATION_PLACEHOLDER : effect.getTurns();
        for (CanHit target : ctx.targets()) {
            if (target == null || target.isDeath()) {
                continue;
            }
            target.getBuffManager().addBuff(TriggerInterpreter.withSource(
                    TriggerLifetimes.withLifetime(
                            new AbsorbBuff(turns, permanent, resource, unitAmount, share, payer), effect),
                    ctx.firing()));
        }
    }
}
