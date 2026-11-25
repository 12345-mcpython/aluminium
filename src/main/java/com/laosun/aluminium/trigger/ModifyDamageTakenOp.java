package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code MODIFY_DAMAGE_TAKEN}: the damage-taken zone - vulnerability when positive, reduction when negative.
 *
 * <p>Neither direction is an attribute, which is why {@code MODIFY_ATTR} cannot express it. A one-shot effect on the
 * instance being settled ({@code instance}) needs no lifetime; every persistent zone still requires one.
 */
final class ModifyDamageTakenOp implements OpHandler<ModifyDamageTakenArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.MODIFY_DAMAGE_TAKEN;
    }

    @Override
    public ModifyDamageTakenArgs parse(EffectSpec effect) {
        boolean permanent = TriggerLifetimes.unticked(effect);
        // ⚠ `Integer.valueOf` on the placeholder: a `?:` mixing the primitive with the boxed `getTurns()` would unbox a
        // null and NPE before this op's own validator could refuse a missing duration.
        Integer turns = permanent
                ? Integer.valueOf(TriggerEffects.UNBOUNDED_DURATION_PLACEHOLDER)
                : effect.getTurns();
        return new ModifyDamageTakenArgs(
                effect,
                Boolean.TRUE.equals(effect.getInstance()),
                turns,
                permanent,
                effect.getDamageType(),
                effect.getScopeElement(),
                Boolean.TRUE.equals(effect.getFromSelf()),
                effect.getMaxStacks(),
                effect.getPerStack(),
                effect.getPerStackCap(),
                effect.getBuff(),
                Boolean.TRUE.equals(effect.getPerStackLive()),
                effect.getWhileState(),
                effect.getWhileStacks(),
                effect.getWhileStacksMin(),
                effect.getWhileHolderState(),
                Boolean.TRUE.equals(effect.getCoexist()));
    }

    @Override
    public void validate(ModifyDamageTakenArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireScopeElement(effect, op, rule);
        TriggerValidator.requireBaseChance(effect, op, rule);
        TriggerValidator.requirePercent(effect, op, rule);
        TriggerValidator.requireNonZeroPercent(effect, op, rule);
        if (!Boolean.TRUE.equals(effect.getInstance())) {
            TriggerValidator.requireDuration(effect, op, rule);
        }
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, ModifyDamageTakenArgs args) {
        TriggerInterpreter.modifyDamageTaken(ctx, args);
    }
}
