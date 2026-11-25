package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code RAISE_ENERGY_TO}: a <b>floor</b> on the energy bar, not a gain.
 *
 * <p>Energy is raised to the stated share of the maximum and left alone when it is already at or above it; adding a
 * share instead would overshoot. A flat {@code amount} is the other legal spelling (the trace that states the floor in
 * points), and the check accepts those two spellings only - a share carried in a cast parameter or a resource would be
 * unresolved by the time this op reads it.
 */
final class RaiseEnergyToOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.RAISE_ENERGY_TO;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (effect.getAmount() == null) {
            TriggerValidator.requirePercent(effect, op, rule);
        }
        if (effect.getPercent() == null && effect.getValue() == null && effect.getAmount() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " names the level to raise energy TO, so it needs a plain \"percent\", "
                            + "\"amount\" (points) or \"value\": got a share from a cast parameter or a resource "
                            + "instead (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        for (CanHit target : ctx.targets()) {
            double floor = ctx.effect().getAmount() != null
                    ? ctx.effect().getAmount()
                    : target.getMaxEnergy() * ctx.share();
            if (target.getCurrentEnergy() < floor) {
                target.setCurrentEnergy(Math.min(floor, target.getMaxEnergy()));
            }
        }
    }
}
