package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;

/**
 * {@code REMOVE_STACK}: take layers of one stackable modifier off the target.
 *
 * <p>{@code attribute} OR {@code buff} names the thing, exactly one: "remove 1 stack of [鸣弦号令]" is about a named
 * stackable modifier, which the attribute form cannot address. Removing nothing is not an error - the sentence that
 * says "one stack per turn" fires every turn whether or not there is one.
 */
final class RemoveStackOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.REMOVE_STACK;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        boolean byName = effect.getBuff() != null && !effect.getBuff().isBlank();
        boolean byAttribute = effect.getAttribute() != null && !effect.getAttribute().isBlank();
        if (byName == byAttribute) {
            throw new IllegalArgumentException(
                    "Op " + op + " takes layers off a stackable buff and must say WHICH one: either "
                            + "\"attribute\": \"ATTACK\" or \"buff\": \"<name>\", exactly one (source: "
                            + rule.getSource() + ")");
        }
        if (byAttribute) {
            TriggerValidator.requireAttribute(effect, op, rule);
        }
        TriggerValidator.requirePositiveAmount(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        TriggerEffects.removeStacks(ctx.battle(), ctx.effect(), ctx.firing());
    }
}
