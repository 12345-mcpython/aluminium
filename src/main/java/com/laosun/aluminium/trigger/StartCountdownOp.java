package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code START_COUNTDOWN}: put a countdown unit on the action bar.
 *
 * <p>A countdown is a combatant whose only job is to have a turn, so its {@code speed} decides when that turn is and a
 * countdown that never acts is refused as "a duration that never ends".
 */
final class StartCountdownOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.START_COUNTDOWN;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        if (effect.getSpeed() == null || effect.getSpeed() <= 0) {
            throw new IllegalArgumentException(
                    "Op " + op + " needs a positive \"speed\": the countdown's turn is decided by it, and a "
                            + "countdown that never acts is a duration that never ends "
                            + "(source: " + rule.getSource() + ")");
        }
        TriggerValidator.rejectCountdownExtras(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerInterpreter.startCountdown(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
