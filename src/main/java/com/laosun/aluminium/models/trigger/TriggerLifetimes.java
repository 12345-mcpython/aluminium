package com.laosun.aluminium.models.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.models.buff.AbstractBuff;
import com.laosun.aluminium.models.trigger.TriggerTable.TriggerContext;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The lifetime vocabulary of a buff an effect creates: the event that ENDS it, and the unit whose
 * turn boundary SPENDS it.
 *
 * <p><b>Why these five declarations are their own class.</b> They are the seam between the
 * interpreter and the classes that consume it, rather than interpreter internals: TriggerEffects
 * calls {@link #withLifetime} six times, {@link #unticked} three and {@link #withTickOwner} three,
 * while TriggerValidator calls {@link #eventBound} six. They read no instance state and name no
 * private type, so they move as static helpers without widening anything.
 *
 * <p>The two halves say different things about time, which is why they are separate methods:
 * {@code until} names the EVENT that ends a buff, {@code ticks_on} names WHOSE TURN boundary spends
 * it. A rule may state either.
 */
final class TriggerLifetimes {

    private TriggerLifetimes() {
    }


    /**
     * Whether the buff this effect creates must <b>not be ticked</b>: either it has no turn limit at all
     * ({@code permanent: true}) or its end is an event ({@code until}), which no turn boundary can bring
     * forward.
     *
     * <p><b>Why an event-bound buff counts as "permanent".</b> That flag means "never ticked"
     * ({@link AbstractBuff#isPermanent()}), which is exactly right here: a buff that ends when the first attack is cast
     * must survive any number of turn boundaries, and giving it a placeholder turn count instead would make it
     * expire on the first {@code afterMove} - a wrong answer that looks like a working rule. The flag's name
     * reads as "for the rest of the battle" in the JSON (where it is what authors write), but mechanically it
     * is "no turn limit", and an event ends this one.
     *
     * <p>Note: Also the reason this helper exists: {@code until} leaves {@code turns} null, and reading
     * {@code getTurns()} into an {@code int} would throw at fire time - inside a battle, where the project
     * puts nothing.
     */
    static boolean unticked(EffectSpec effect) {
        return Boolean.TRUE.equals(effect.getPermanent()) || eventBound(effect);
    }

    static boolean eventBound(EffectSpec effect) {
        return effect.getUntil() != null && !effect.getUntil().isEmpty();
    }

    /**
     * Attaches the {@code "until": ...} a rule names to a buff it just created (see {@link EffectSpec#getUntil()}).
     *
     * <p>A no-op when the effect states no lifetime, so every buff that does not ask for one behaves exactly
     * as before - the field's default is an <b>empty</b> set of ending events.
     */
    static AbstractBuff withLifetime(AbstractBuff buff, EffectSpec effect) {
        buff.setLifetimes(lifetimesOf(effect));
        return buff;
    }

    /**
     * Anchors a fresh buff's duration to another unit's turns when the rule asks for it ({@code "ticks_on"},
     * and leaves it on its carrier otherwise.
     *
     * <p>Separate from {@link #withLifetime} because the two say different things about time: {@code until} names the
     * <b>event</b> that ends a buff, this names <b>whose turn boundary</b> spends it. A rule may state either, and
     * the state names are the same ones the rest of the op vocabulary uses.
     *
     * <p>Two spellings since, and the second one is what "... removed when it disappears" is built on:
     * <ul>
     *   <li>{@code "self"} - the rule owner's turns (星期日's [蒙福者], 1321's domain, 缇宝's zone);</li>
     *   <li>{@code "summon"} - <b>the owner's memosprite</b> (1402's [至高之姿] "<b>when the Garmentmaker (衣匠) disappears</b>, Aglaea (阿格莱雅) removes
     *       the [至高之姿] state", and 140/1415's "removed as the dead dragon disappears").</li>
     * </ul>
     * Note: <b>The anchor IS the clock</b> ({@code BuffManager.removeBuffsAnchoredTo} asks {@code buff.ticksOn(dead)}),
     * so this one field delivers both halves for free: the duration is spent on that unit's turn boundaries, and when
     * it dies the buff goes with it ({@code Battle.releaseBuffsAnchoredToTheDead}, which already sweeps both camps).
     * Note: A missing summon is a <b>loud</b> error ({@link TriggerTargets#requireSummon}) rather than a silent fall-back to the owner:
     * "my memosprite's turns" is meaningless when there is none, and a rule that quietly anchored to herself instead
     * would keep a buff alive for the rest of the battle.
     */
    static AbstractBuff withTickOwner(AbstractBuff buff, EffectSpec effect, TriggerContext ctx) {
        if (effect.getTicksOn() != null) {
            buff.setTickOwner("summon".equals(effect.getTicksOn().trim()) ? TriggerTargets.requireSummon(ctx) : ctx.owner());
        }
        return buff;
    }


    /**
     * The lifetimes a validated effect names; <b>empty</b> when it names none.
     *
     * <p>Every name has already been checked against {@link TriggerValidator} at load time, so anything else here is an
     * engine-side inconsistency rather than a content error - hence {@link IllegalStateException}, exactly as
     * before. A rule may name several ("basic attack <b>or</b> Skill"): they are collected into one set, and the buff ends at
     * the first of them.
     */
    private static Set<AbstractBuff.Lifetime> lifetimesOf(EffectSpec effect) {
        List<String> until = effect.getUntil();
        if (until == null || until.isEmpty()) {
            return Set.of();
        }
        Set<AbstractBuff.Lifetime> lifetimes = EnumSet.noneOf(AbstractBuff.Lifetime.class);
        for (String name : until) {
            lifetimes.add(switch (name.trim().toLowerCase(Locale.ROOT)) {
                case "next_attack" -> AbstractBuff.Lifetime.NEXT_ATTACK;
                case "next_skill" -> AbstractBuff.Lifetime.NEXT_SKILL;
                case "next_ultimate" -> AbstractBuff.Lifetime.NEXT_ULTIMATE;
                case "next_elation_skill" -> AbstractBuff.Lifetime.NEXT_ELATION_SKILL;
                // The cast being delivered right now: raised inside the cast window, dropped when its events are done.
                case "cast_end" -> AbstractBuff.Lifetime.CAST_END;
                case "turn_end" -> AbstractBuff.Lifetime.TURN_END;
                case "next_turn_start" -> AbstractBuff.Lifetime.NEXT_TURN_START;
                default -> throw new IllegalStateException(
                        "Lifetime '" + name + "' passed validation but has no implementation");
            });
        }
        return lifetimes;
    }
}
