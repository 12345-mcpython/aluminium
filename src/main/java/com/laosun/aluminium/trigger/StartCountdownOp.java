package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code START_COUNTDOWN}: put a countdown unit on the action bar.
 *
 * <p>A countdown is a combatant whose only job is to have a turn, so its {@code speed} decides when that turn is and a
 * countdown that never acts is refused as "a duration that never ends". The name may be omitted, in which case it is
 * named after whoever put it there - a decision the parse records as "no name" rather than a sentence the run-time half
 * has to re-read.
 */
final class StartCountdownOp implements OpHandler<StartCountdownArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.START_COUNTDOWN;
    }

    @Override
    public StartCountdownArgs parse(EffectSpec effect) {
        String stated = effect.getBuff() == null || effect.getBuff().isBlank() ? null : effect.getBuff().trim();
        return new StartCountdownArgs(stated, effect.getSpeed());
    }

    @Override
    public void validate(StartCountdownArgs args, EffectSpec effect, TriggerSpec rule) {
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
    public void apply(OpContext ctx, StartCountdownArgs args) {
        String name = args.statedName() != null
                ? args.statedName()
                : (ctx.firing().owner() == null ? "countdown" : ctx.firing().owner().getName() + " 倒计时");
        ctx.battle().startCountdown(ctx.firing().owner(), name, args.speed());
    }
}
