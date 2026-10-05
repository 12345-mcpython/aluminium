package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.CanHit;
import lombok.Getter;

/**
 * Damage over time attached to a weakness break: burn (Fire) / shock (Lightning) /
 * bleed (Physical) / wind shear (Wind).
 *
 * <p><b>It is an ordinary buff, and that is the point.</b> A parallel model
 * ({@code models.Dot} + {@code Enemy.dots} + {@code Battle.tickDots(Enemy)}) is the very
 * "second mechanism" this project refuses. Looking at the two side by side, every row is the same
 * idea spelled twice:
 *
 * <table border="1">
 *   <caption>the parallel model against the buff system</caption>
 *   <tr><th></th><th>a separate DOT model</th><th>buff system</th></tr>
 *   <tr><td>storage</td><td>{@code Enemy.dots}</td><td>{@code BuffManager.buffs}</td></tr>
 *   <tr><td>duration</td><td>{@code Dot.remainingTurns}</td><td>{@code remainingDuration}</td></tr>
 *   <tr><td>countdown</td><td>{@code Dot.tick()}</td><td>{@code tickEffect} + {@code processBuffTick}</td></tr>
 *   <tr><td>expiry</td><td>{@code enemy.removeDot(dot)}</td><td>{@code processBuffTick} removing it</td></tr>
 * </table>
 *
 * <p>The difference is not cosmetic. The other shape is <b>type-impossible to put a DOT on a
 * character</b> - {@code tickDots} takes an {@code Enemy} and the list lives on {@code Enemy}, so
 * "the boss burns us" (which HSR does constantly) cannot be expressed at all. That shape also loses
 * dispel, {@code hasBuff} queries, the shared refresh / stacking rules, and visibility to any
 * buff-driven mechanic. A DOT on a {@code Character} is the same code path as a DOT on an
 * {@code Enemy}, and that is a tested behaviour rather than an aspiration.
 *
 * <h2>Why this class holds no damage logic</h2>
 * {@code AbstractBuff.tickEffect(CanHit)} is <b>not</b> given a {@code Battle}, so this buff could not
 * call {@code applyDamage} even if it wanted to. Rather than widen that signature for one buff (which
 * would leak {@code Battle} into every buff), the split is:
 * <ul>
 *   <li><b>lifecycle</b> (duration, expiry, dispel) = this buff, driven by {@link
 *       BuffManager};</li>
 *   <li><b>settlement</b> (the damage itself, which needs the full zone set) = {@code
 *       Battle.tickDots(CanHit)}, which queries this class through {@code BuffManager.allBuffsOf}.</li>
 * </ul>
 * The two halves are kept in step by <b>where</b> they are called, not by shared state:
 * {@code Battle.beforeMove()} settles first and then lets the buff manager tick, so a DOT created
 * with N turns settles exactly N times - the same count a {@code Dot.tick()} produces.
 *
 * <h2>Why it is an early buff</h2>
 * {@code isEarlyBuff = true} means the countdown happens in {@code BuffManager.beforeMove()}, i.e. at
 * the start of its owner's turn - which is what "damage over time" means. A late buff would settle on
 * the turn <i>after</i> the one it was meant to burn, off by one turn at both ends.
 *
 * <h2>Stacking: instances, not a kind</h2>
 * {@link #isSameKind} always returns {@code false}, so {@code addBuff} never evicts an existing DOT.
 * The default buff rule is "casting the same buff again refreshes it", and applying that here would
 * make a second break <b>replace</b> the first burn and silently drop its remaining settlements. The
 * engine's DOT rule has always been the opposite - "first applied, first settled", the same element
 * stacking as separate copies - so a DOT's identity is <b>the instance</b>. If content ever wants
 * "re-breaking refreshes the burn instead", this one method is the only place to change.
 */
@Getter
public class DotBuff extends AbstractBuff {

    /**
     * DoT element: Fire / Lightning / Physical / Wind (Ice = freeze, Quantum = entanglement,
     * Imaginary = imprisonment, unified into one table).
     *
     * <p>Read by {@code Battle.tickDots} to build the {@code Damage}, so it decides which RES zone
     * applies and how the hit is reported.
     */
    private final DamageElement element;

    /**
     * Base damage per settlement (break base  x  {@code BreakEffect.dotRatio()}, which the four DOT
     * elements of {@code Constant.BREAK_EFFECTS} currently all fill with {@code Constant.DOT_RATIO}).
     *
     * <p>"Base" is literal: it has not been through the zones yet. It takes DMG boost and the
     * DEF / RES / vulnerability zones when settled, and it can never crit - expressed by
     * {@link com.laosun.aluminium.enums.DamageType#DOT}'s own {@code (crittable=false,
     * boostable=true)}, not by anything in this class.
     */
    private final double baseDamage;

    /**
     * @param source     the applier ({@code Damage}'s attacker is not allowed to be null, so it MUST
     *                   be recorded; it decides who is credited with a DOT kill)
     * @param element    the DoT element
     * @param baseDamage base damage per settlement (has not been through the zones yet)
     * @param turns      how many turns it burns for, i.e. how many settlements it gets
     * @throws IllegalArgumentException any of the four is missing or outside its legal range. All four
     *                                  come from data ({@code Battle.attachBreakDot} takes the element
     *                                  and the turn count from {@code Constant.BREAK_EFFECTS}), and each
     *                                  one has a wrong answer behind it that is otherwise <b>silent</b>:
     *                                  {@code turns <= 0} still settles once (the manager expires it
     *                                  only after {@code Battle.tickDots} ran), a negative
     *                                  {@code baseDamage} is swallowed by {@code Battle.assemble}'s
     *                                  {@code Math.max(1, …)} floor into a <b>silent 1 damage per
     *                                  turn</b>, NaN/infinity poisons every later zone,
     *                                  a null element is only caught at settlement time by
     *                                  {@code Damage}, and a null source is caught nowhere at all.
     */
    /**
     * The layer ceiling this application was authored with, or {@code 0} for "no ceiling".
     *
     * <p>"the windshear state <b>stacks at most 5 layers</b>": applications may exceed it (a DOT is never evicted -- {@link #isSameKind} says
     * so), but at most this many of them may <b>deal damage</b>. Note: {@code Battle.tickDots} applies it per element, i.e.
     * per DOCUMENT STATE (two windshear (风化) applications are the same state; windshear (风化) and burn (灼烧) are not).
     */
    private final int maxStacks;

    public DotBuff(CanHit source, DamageElement element, double baseDamage, int turns) {
        this(source, element, baseDamage, turns, 0);
    }

    /**
     * A DOT with the <b>document's own name</b> for it (cone 23006's [游丝]).
     *
     * <p>Why a name and not just an element: the corpus has states that ARE damage over time and are asked about by
     * name ("if that target is not in the [游丝] state"), while the element alone only answers burn (灼烧) / shock (触电) / bleed (裂伤) / windshear (风化).
     * Naming one is also how "[游丝] is also considered to be in the shocked state" comes out right for free: a named THUNDER
     * DOT still answers shock (触电) through the element table.
     */
    public DotBuff(CanHit source, DamageElement element, double baseDamage, int turns, int maxStacks, String name) {
        this(source, element, baseDamage, turns, maxStacks);
        if (name != null && !name.isBlank()) {
            this.buffName = name.trim();
        }
    }

    /** @param maxStacks the layer ceiling (0 = none); see {@link #getMaxStacks()} */
    public DotBuff(CanHit source, DamageElement element, double baseDamage, int turns, int maxStacks) {
        super(turns, true);
        this.maxStacks = maxStacks;
        if (source == null) {
            throw new IllegalArgumentException("DotBuff needs a source: the applier is who a DOT kill is credited to");
        }
        if (element == null) {
            throw new IllegalArgumentException("DotBuff needs an element: it decides which RES zone applies");
        }
        if (turns < 1) {
            throw new IllegalArgumentException(
                    "DotBuff turns must be >= 1, got " + turns + " (0 would still settle once)");
        }
        // `!(baseDamage >= 0)` rather than `baseDamage < 0` on purpose: it also rejects NaN.
        if (!(baseDamage >= 0) || !Double.isFinite(baseDamage)) {
            throw new IllegalArgumentException(
                    "DotBuff baseDamage must be finite and >= 0, got " + baseDamage
                            + " (a negative value would settle a silent 1 damage per turn)");
        }
        this.element = element;
        this.baseDamage = baseDamage;
        setSource(source);
    }

    /**
     * A DOT never stops its owner acting. {@code true} is not a placeholder: {@code
     * BuffManager.processBuffTick} turns "expired while unable to act" into {@code blocked}, so
     * returning {@code false} here would freeze the victim for one extra turn every time a burn
     * wears off.
     */
    @Override
    public boolean canAct() {
        return true;
    }

    /**
     * A DOT is a damage-over-time class negative state (DoT Debuff, 持续伤害类负面状态) - a negative effect that "dispel 1 negative effect" may remove.
     */
    @Override
    public boolean isDebuff() {
        return true;
    }

    /**
     * Damage-over-time class (DoT Debuff, 持续伤害类): "resistance to damage-over-time class negative states increased by 50%" (1008 Integrity (坚韧)) is an answer about this family - and because it is
     * the <b>element</b> that makes a DOT a DOT, a fifth element would be covered the day it exists.
     */
    @Override
    public com.laosun.aluminium.enums.DebuffClass debuffClass() {
        return com.laosun.aluminium.enums.DebuffClass.DOT;
    }

    /**
     * Nothing to attach: a DOT changes no attribute, it only deals damage when it settles.
     */
    @Override
    public void applyEffect(CanHit target) {
    }

    /**
     * Nothing to take off, for the same reason as {@link #applyEffect}.
     */
    @Override
    public void removeBuff(CanHit target) {
    }

    /**
     * The whole of this buff's own behaviour: burn one turn off.
     *
     * <p>The countdown lives here rather than in the manager because {@code processBuffTick} only
     * asks "is it over yet" ({@code duration() <= 0}) after calling this - so a buff decides its own
     * duration semantics. {@code Battle.tickDots} has already settled the damage by this point.
     */
    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }

    /**
     * Always {@code false}: two DOTs are never "the same buff, re-applied" (see the class Javadoc).
     *
     * @param other the buff being attached
     * @return {@code false}, so {@code BuffManager.addBuff} appends instead of replacing
     */
    /** The authored layer ceiling ({@code 0} = uncapped); read by {@code Battle.tickDots}. */
    public int getMaxStacks() {
        return maxStacks;
    }

    @Override
    public boolean isSameKind(AbstractBuff other) {
        return false;
    }
}
