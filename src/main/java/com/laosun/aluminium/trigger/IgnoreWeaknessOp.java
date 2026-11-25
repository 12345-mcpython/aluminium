package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;

/**
 * {@code IGNORE_WEAKNESS}: the next N casts do not need a weakness to reduce toughness.
 *
 * <p>The cast still reports through; only the weakness gate stops refusing it, which is why the count lives on the unit
 * and is read by {@code SkillExecutor} rather than here.
 *
 * <p><b>The first op whose arguments are parsed rather than re-read.</b> {@code amount} used to be read out of the bean
 * on every firing; it is a literal count, so it is read once while the table compiles and the run-time half uses
 * {@link IgnoreWeaknessArgs}. The unstated case ({@code amount} absent) means "one cast", which is a decision the parse
 * makes once instead of on every hit.
 */
final class IgnoreWeaknessOp implements OpHandler<IgnoreWeaknessArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.IGNORE_WEAKNESS;
    }

    @Override
    public IgnoreWeaknessArgs parse(EffectSpec effect) {
        int casts = effect.getAmount() == null ? 1 : (int) Math.round(effect.getAmount());
        return new IgnoreWeaknessArgs(casts);
    }

    @Override
    public void validate(IgnoreWeaknessArgs args, EffectSpec effect, TriggerSpec rule) {
        // No arm of its own in TriggerValidator: this was the `default` check it used to get there.
        TriggerValidator.requireNoStackArguments(effect, op().name(), rule);
    }

    @Override
    public void apply(OpContext ctx, IgnoreWeaknessArgs args) {
        for (CanHit target : ctx.targets()) {
            target.markIgnoreWeaknessCasts(args.casts());
        }
    }
}
