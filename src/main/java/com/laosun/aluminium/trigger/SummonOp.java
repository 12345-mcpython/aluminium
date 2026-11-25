package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.Character;

/**
 * {@code SUMMON}: bring the rule owner's memosprite (or a panel-less entity) onto the field.
 *
 * <p>No arguments at all: the memosprite belongs to the rule's owner, and everything about it (name, panel derivation)
 * lives in {@code resources/game/memosprites/<cid>.json}. A {@code target} here would be silently ignored, so it is
 * refused rather than dropped. Three shapes are decided by the data: another instance, a {@code panel_less} entity
 * placed with the summoner's own panel (神君 / 阿哈 / 浮元), or the ordinary memosprite - which may be named, because a
 * kit can own several.
 */
final class SummonOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.SUMMON;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        String op = op().name();
        TriggerValidator.requireNoDuration(effect, op, rule);
        TriggerValidator.requireNoStackArguments(effect, op, rule);
        TriggerValidator.requireNoTarget(effect, op, rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        EffectSpec effect = ctx.effect();
        Character owner = TriggerMagnitudes.requireCharacterOwner(effect, ctx.firing());
        if (Boolean.TRUE.equals(effect.getAnotherInstance())) {
            ctx.battle().summonAnotherMemosprite(owner);
            return;
        }
        if (Boolean.TRUE.equals(effect.getPanelLess())) {
            if (effect.getMemospriteName() == null || effect.getMemospriteName().isBlank()) {
                throw new IllegalStateException(
                        "Op SUMMON states panel_less but names no entity: say which one (神君 / 阿哈 / 浮元)");
            }
            ctx.battle().placePanelLessSummon(owner, effect.getMemospriteName().trim(), effect.getSummonSpeed());
            return;
        }
        if (effect.getMemospriteName() == null || effect.getMemospriteName().isBlank()) {
            ctx.battle().summonMemosprite(owner);
        } else {
            ctx.battle().summonMemosprite(owner, effect.getMemospriteName().trim());
        }
    }
}
