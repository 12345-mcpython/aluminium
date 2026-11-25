package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;

/**
 * {@code TAUNT}: make an enemy aim at the named unit.
 *
 * <p>A state on the <b>enemy</b>, read by the targeting policy, not a stat. A turn count is required and
 * {@code permanent} / {@code until} are refused: a marker with no clock would never come off, and no document writes it
 * that way.
 */
final class TauntOp implements OpHandler<TauntArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.TAUNT;
    }

    @Override
    public TauntArgs parse(EffectSpec effect) {
        return new TauntArgs(effect.getTurns());
    }

    @Override
    public void validate(TauntArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        Integer turns = args.turns();
        if (turns == null || turns <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " requires a positive \"turns\" (how long the taunt lasts); the documents "
                            + "write \"make the target enter the taunt state, lasting 1 turn\" (source: "
                            + rule.getSource() + ")");
        }
        if (Boolean.TRUE.equals(effect.getPermanent()) || TriggerLifetimes.eventBound(effect)) {
            throw new IllegalArgumentException(
                    "Op " + op + " takes a turn count and nothing else; \"permanent\" / \"until\" would be "
                            + "a taunt that never ends (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, TauntArgs args) {
        TriggerEffects.taunt(ctx, args);
    }
}
