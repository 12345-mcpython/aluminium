package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.CanHit;
import lombok.Getter;

/**
 * Damage over time attached to a weakness break (P4-5): burn (Fire) / shock (Lightning) /
 * bleed (Physical) / wind shear (Wind).
 *
 * <p><b>It is an ordinary buff, and that is the point.</b> It used to be a parallel model
 * ({@code models.Dot} + {@code Enemy.dots} + {@code Battle.tickDots(Enemy)}), which is the very
 * "second mechanism" P8-0 exists to prevent. Looking at the two side by side, every row is the same
 * idea spelled twice:
 *
 * <table border="1">
 *   <caption>before / after the migration</caption>
 *   <tr><th></th><th>old DOT</th><th>buff system</th></tr>
 *   <tr><td>storage</td><td>{@code Enemy.dots}</td><td>{@code BuffManager.buffs}</td></tr>
 *   <tr><td>duration</td><td>{@code Dot.remainingTurns}</td><td>{@code remainingDuration}</td></tr>
 *   <tr><td>countdown</td><td>{@code Dot.tick()}</td><td>{@code tickEffect} + {@code processBuffTick}</td></tr>
 *   <tr><td>expiry</td><td>{@code enemy.removeDot(dot)}</td><td>{@code processBuffTick} removing it</td></tr>
 * </table>
 *
 * <p>The migration is not cosmetic. The old shape was <b>type-impossible to put a DOT on a
 * character</b> — {@code tickDots} took an {@code Enemy} and the list lived on {@code Enemy}, so
 * "the boss burns us" (which HSR does constantly) could not be expressed at all. It also lost
 * dispel, {@code hasBuff} queries, the shared refresh / stacking rules, and visibility to any
 * buff-driven mechanic. Now a DOT on a {@code Character} is the same code path as a DOT on an
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
 * with N turns settles exactly N times — the same count the old {@code Dot.tick()} produced.
 *
 * <h2>Why it is an early buff</h2>
 * {@code isEarlyBuff = true} means the countdown happens in {@code BuffManager.beforeMove()}, i.e. at
 * the start of its owner's turn — which is what "damage over time" means. A late buff would settle on
 * the turn <i>after</i> the one it was meant to burn, off by one turn at both ends.
 *
 * <h2>Stacking: instances, not a kind</h2>
 * {@link #isSameKind} always returns {@code false}, so {@code addBuff} never evicts an existing DOT.
 * The default buff rule is "casting the same buff again refreshes it", and applying that here would
 * make a second break <b>replace</b> the first burn and silently drop its remaining settlements. The
 * engine's DOT rule has always been the opposite — "first applied, first settled", the same element
 * stacking as separate copies — so a DOT's identity is <b>the instance</b>. If content ever wants
 * "re-breaking refreshes the burn instead", this one method is the only place to change.
 */
@Getter
public class DotBuff extends AbstractBuff {

    /**
     * DoT element: Fire / Lightning / Physical / Wind (Ice = freeze, Quantum = entanglement,
     * Imaginary = imprisonment, unified into a table in P10-1).
     *
     * <p>Read by {@code Battle.tickDots} to build the {@code Damage}, so it decides which RES zone
     * applies and how the hit is reported.
     */
    private final DamageElement element;

    /**
     * Base damage per settlement (break base × {@code BreakEffect.dotRatio()}, which the four DOT
     * elements of {@code Constant.BREAK_EFFECTS} currently all fill with {@code Constant.DOT_RATIO}).
     *
     * <p>"Base" is literal: it has not been through the zones yet. It takes DMG boost and the
     * DEF / RES / vulnerability zones when settled, and it can never crit — expressed by
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
    public DotBuff(CanHit source, DamageElement element, double baseDamage, int turns) {
        super(turns, true);
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
     * A DOT is a 持续伤害类负面状态 — a negative effect that 「解除 1 个负面效果」 may remove.
     */
    @Override
    public boolean isDebuff() {
        return true;
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
     * asks "is it over yet" ({@code duration() <= 0}) after calling this — so a buff decides its own
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
    @Override
    public boolean isSameKind(AbstractBuff other) {
        return false;
    }
}
