package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.Character;

/**
 * {@code SUMMON}: bring the rule owner's memosprite (or a panel-less entity) onto the field.
 *
 * <p>Three shapes, decided by the data: another <b>instance</b> of one already out; a {@code panel_less} entity that has
 * no spec and is placed with the summoner's own panel (神君 / 阿哈 / 浮元); or the ordinary memosprite, which may be named
 * because a kit can own several. Registered here so the op has one dispatch path; its load-time checks are still in
 * {@code TriggerValidator}.
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
