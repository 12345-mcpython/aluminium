package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;

/**
 * {@code SUMMON}: bring the rule owner's memosprite (or a panel-less entity) onto the field.
 *
 * <p>No arguments at all: the memosprite belongs to the rule's owner, and everything about it (name, panel derivation)
 * lives in {@code resources/game/memosprites/<cid>.json}. A {@code target} here would be silently ignored, so it is
 * refused rather than dropped.
 *
 * <p><b>The three shapes are decided once, in {@link SummonArgs}.</b> They used to be re-derived from three fields on
 * every firing; they are a property of the sentence, not of the hit.
 */
final class SummonOp implements OpHandler<SummonArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.SUMMON;
    }

    @Override
    public SummonArgs parse(EffectSpec effect) {
        String name = effect.getMemospriteName() == null || effect.getMemospriteName().isBlank()
                ? null
                : effect.getMemospriteName().trim();
        return new SummonArgs(Boolean.TRUE.equals(effect.getAnotherInstance()),
                Boolean.TRUE.equals(effect.getPanelLess()),
                name,
                effect.getSummonSpeed());
    }

    @Override
    public void validate(SummonArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        TriggerValidator.requireNoTarget(effect, op, rule);
        if (args.panelLess() && args.name() == null) {
            throw new IllegalArgumentException(
                    "Op " + op + " states panel_less but names no entity: say which one (神君 / 阿哈 / 浮元)"
                            + " (source: " + rule.getSource() + ")");
        }
    }

    @Override
    public void apply(OpContext ctx, SummonArgs args) {
        Character owner = TriggerMagnitudes.requireCharacterOwner(ctx.effect(), ctx.firing());
        if (args.anotherInstance()) {
            ctx.battle().summonAnotherMemosprite(owner);
            return;
        }
        if (args.panelLess()) {
            ctx.battle().placePanelLessSummon(owner, args.name(), args.speed());
            return;
        }
        if (args.name() == null) {
            ctx.battle().summonMemosprite(owner);
        } else {
            ctx.battle().summonMemosprite(owner, args.name());
        }
    }
}
