package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.buff.AbsorbBuff;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code ABSORB_DAMAGE}: install a counter-backed absorber on the protected unit.
 *
 * <p>The grant rides on the unit the hit lands on, while the counter belongs to whoever pays - and {@code payer}
 * states that, defaulting to the rule's owner. The three arguments are validated at load time, so the reads here are
 * reads, not checks. Registered here so the op has one dispatch path.
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
    public void apply(OpContext ctx, OpArgs args) {
        EffectSpec effect = ctx.effect();
        String resource = effect.getResource().trim();
        double unitAmount = effect.getUnitAmount();
        double share = TriggerMagnitudes.statesShare(effect)
                ? ctx.share()
                : effect.getPercent();
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
