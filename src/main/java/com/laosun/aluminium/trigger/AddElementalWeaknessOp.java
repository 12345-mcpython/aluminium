package com.laosun.aluminium.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code ADD_ELEMENTAL_WEAKNESS}: give an enemy a weakness it did not have.
 *
 * <p>Two named sources sit beside the real element names: {@code party_first} (the party's first element) and
 * {@code random_absent} (an element the target does not already have). Neither is an error when it yields nothing -
 * the clause simply does nothing. A repeat insertion is <b>not</b> an insertion, so the check happens before the change
 * and the event fires only for a real one.
 */
final class AddElementalWeaknessOp implements OpHandler<OpArgs> {

    @Override
    public TriggerOperator op() {
        return TriggerOperator.ADD_ELEMENTAL_WEAKNESS;
    }

    @Override
    public OpArgs parse(EffectSpec effect) {
        return OpArgs.NONE;
    }

    @Override
    public void validate(OpArgs args, EffectSpec effect, TriggerSpec rule) {
        if (effect.getElement() == null || effect.getElement().isBlank()) {
            throw new IllegalArgumentException("Op " + op().name() + " requires element (source: " + rule.getSource() + ")");
        }
        // Refused HERE, not at run time: an unknown element that only blows up when the rule finally fires is exactly
        // the silent mistake this library keeps closing.
        String named = effect.getElement().trim();
        if (!TriggerVocabulary.SPECIAL_ELEMENTS.contains(named) && DamageElement.fromString(named) == null) {
            throw new IllegalArgumentException("Op " + op().name() + " names element \"" + named
                    + "\", which is neither a DamageElement nor one of " + TriggerVocabulary.SPECIAL_ELEMENTS
                    + " (source: " + rule.getSource() + ")");
        }
        TriggerValidator.requireNoStackArguments(effect, op().name(), rule);
    }

    @Override
    public void apply(OpContext ctx, OpArgs args) {
        EffectSpec effect = ctx.effect();
        String named = effect.getElement().trim();
        DamageElement weakness;
        if ("party_first".equals(named)) {
            weakness = ctx.battle() == null || ctx.battle().characters().isEmpty()
                    ? null : ctx.battle().characters().getFirst().getElement();
        } else if ("random_absent".equals(named)) {
            weakness = null;
            for (CanHit victim : ctx.targets()) {
                if (victim instanceof Enemy enemy) {
                    List<DamageElement> absent = new ArrayList<>();
                    for (DamageElement candidate : DamageElement.values()) {
                        if (!enemy.isWeakTo(candidate)) {
                            absent.add(candidate);
                        }
                    }
                    weakness = absent.isEmpty() ? null : absent.get(ctx.rng().nextInt(absent.size()));
                }
            }
        } else {
            weakness = DamageElement.fromString(named);
        }
        if (weakness == null) {
            if (TriggerVocabulary.SPECIAL_ELEMENTS.contains(named)) {
                return;
            }
            throw new IllegalStateException("Op ADD_ELEMENTAL_WEAKNESS names an element that is not a DamageElement");
        }
        for (CanHit victim : ctx.targets()) {
            if (victim instanceof Enemy enemy && !enemy.isWeakTo(weakness)) {
                if (effect.getTurns() != null && effect.getTurns() > 0) {
                    enemy.addWeakness(weakness, effect.getTurns());
                } else {
                    enemy.addWeakness(weakness);
                }
                ctx.battle().fire(TriggerPayload.Simple.of(TriggerEvent.WEAKNESS_ADDED,
                        ctx.firing().owner(), enemy, 0, 0));
            }
        }
    }
}
