package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.buff.ShieldBuff;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.enums.TriggerEvent;

/**
 * {@code SHIELD}: install a shield, replacing or stacking as the effect states.
 *
 * <p>A shield normally <b>overwrites</b>, so "the shield amount can stack, up to 300% of the current Skill's" has to be
 * stated: {@code add_to_shield} turns it on and {@code cap_*} bounds the total. A non-positive {@code turns} is refused
 * - a timed shield that expires before it can be used is "no shield" written as a mechanic, and omitting {@code turns}
 * is how "no limit" is spelled.
 */
final class ShieldOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.SHIELD;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireAmountOrScale(effect, op, rule, TriggerValidator.SCALES, "Max HP");
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        if (effect.getTurns() != null && effect.getTurns() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " needs a positive \"turns\" when it states one (how many of the "
                            + "shielded unit's turns the shield lasts), but has " + effect.getTurns()
                            + "; omit the field for a shield that is only removed by being used up "
                            + "(source: " + rule.getSource() + ")");
        }
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        EffectSpec effect = ctx.effect();
        CanHit owner = ctx.firing().owner();
        for (CanHit target : ctx.targets()) {
            double amount = TriggerInterpreter.grantAmount(effect, target, ctx.firing());
            double installed = amount;
            if (Boolean.TRUE.equals(effect.getAddToShield()) && target != null && !target.isDeath()
                    && (target.getShield() <= 0 || target.getShieldProvider() == owner)) {
                double standing = target.getShield();
                double wanted = standing + amount;
                if (effect.getCapScale() != null || effect.getCapPercent() != null || effect.getCapAmount() != null) {
                    double ceiling = TriggerMagnitudes.resolveScale(effect.getCapScale(), effect.getCapPercent(),
                            ctx.firing()) + (effect.getCapAmount() == null ? 0 : effect.getCapAmount());
                    if (ceiling > 0) {
                        wanted = Math.min(wanted, ceiling);
                    }
                }
                if (wanted <= standing) {
                    continue;   // already at the ceiling: no grant, and no fresh timer to reset
                }
                amount = wanted - standing;   // what this grant ANNOUNCES ...
                installed = wanted;           // ... while the shield itself becomes the sum
            }
            if (effect.getTurns() == null) {
                ctx.battle().grantShield(owner, target, installed, ctx.firing().ruleId());
            } else if (target != null && !target.isDeath()) {
                target.getBuffManager().addBuff(TriggerInterpreter.withSource(
                        new ShieldBuff(owner, installed, effect.getTurns()), ctx.firing()));
                // The timed path installs its shield through a buff, which has no Battle handle, so this is the only
                // place that can announce it. Fired AFTER the buff is on, so a rule that answers "when a shield is
                // gained" sees the shield it is reacting to.
                if (amount > 0) {
                    ctx.battle().fireForAlly(TriggerPayload.AllySubject.of(
                            TriggerEvent.SHIELD_GRANTED, owner, target, amount));
                }
            }
        }
    }
}
