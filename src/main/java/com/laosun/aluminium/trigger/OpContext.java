package com.laosun.aluminium.trigger;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.entity.CanHit;

import java.util.List;
import java.util.Random;

/**
 * Everything one op's {@link OpHandler#apply} is allowed to see.
 *
 * <p><b>Why a context and not four parameters.</b> An effect's execution needs the battlefield, the firing context,
 * the effect itself, and the magnitude the previous effect credited ({@code amount_from_previous}). Those used to be
 * threaded positionally through every helper in {@code TriggerInterpreter}; a record makes the list explicit and lets
 * a handler be called with no rule in sight.
 *
 * <p><b>What is deliberately not here.</b> The target helpers below are how a handler should reach a unit; the raw
 * {@link Battle} stays visible because most ops still call it directly. The {@code engine.port} extraction replaces
 * that field with a narrow interface, and every handler written against these helpers survives that change.
 *
 * @param battle           the battlefield, possibly {@code null} for a single-target selector call from a unit test
 * @param firing           the firing context (owner, actor, target, event payload)
 * @param effect           the effect being executed
 * @param previousCredited how much the previous effect of the same rule actually moved
 */
record OpContext(Battle battle, TriggerTable.TriggerContext firing, EffectSpec effect, double previousCredited) {

    /**
     * The units this effect reaches, in the order the selector produced them, with the effect's own
     * {@code target_when} filter applied.
     *
     * @return the resolved targets, never {@code null}
     */
    List<CanHit> targets() {
        return TriggerTargets.resolveTargets(battle, effect, firing);
    }

    /**
     * The single unit this effect names, for the ops that take exactly one.
     *
     * @return the resolved unit, or {@code null} when the selector matched nothing
     */
    CanHit target() {
        return TriggerTargets.resolveTarget(effect, firing);
    }

    /**
     * The same, refusing to be {@code null}.
     *
     * @param what how the op refers to it, for the message
     * @return the resolved unit
     */
    CanHit requireTarget(String what) {
        return TriggerTargets.require(target(), what, firing);
    }

    /**
     * The battlefield's RNG, so every roll a handler makes is reproducible from a seed.
     *
     * @return the RNG
     */
    Random rng() {
        return battle.getRng();
    }

    /**
     * The magnitude this effect states, with its spellings ({@code amount}, a skill row, a counter) already resolved.
     *
     * @return the value
     */
    double amount() {
        return TriggerInterpreter.scaledAmount(effect, firing);
    }

    /**
     * The share this effect states, for the ops that move an action bar or a resource by a fraction.
     *
     * @return the fraction
     */
    double share() {
        return TriggerMagnitudes.shareOf(effect, firing);
    }
}
