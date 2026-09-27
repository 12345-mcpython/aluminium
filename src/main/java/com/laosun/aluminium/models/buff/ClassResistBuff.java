package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.enums.DebuffClass;
import com.laosun.aluminium.models.CanHit;

/**
 * 「抵抗<b>控制类</b>负面状态的概率提高 35%」 / 「免疫<b>控制类</b>负面状态」 — a <b>class</b> resistance, carried by the
 * unit that resists.
 *
 * <p><b>Why a buff and not a field.</b> The sentences come in two shapes: an unconditional trace (克拉拉's 守护) and an
 * ability that protects for a while (「【防火墙】状态下，我方目标免疫敌方目标施加的控制类负面状态」, 1 回合). A raw number on
 * the combatant can only express the first; a buff expires, can be dispelled and can be removed by name through the
 * same machinery every other state uses.
 *
 * <p><b>What it does.</b> Nothing by itself — {@code Battle.tryApplyDebuff} asks the victim how much it resists the
 * <b>class</b> of the state being applied, and multiplies the remaining chance by {@code 1 − that}. So:
 * <ul>
 *   <li>35% resistance means "35% of the chances that would have landed do not" — 「概率提高35%」 read literally;</li>
 *   <li>{@code percent: 1.0} is <b>immunity</b>, the same mechanism at its limit (长夜月's 「免疫控制类负面状态」 needs no
 *       second vocabulary);</li>
 *   <li>two appliers <b>add</b> ({@code BuffManager.debuffResistOf} sums one contribution per applier and clamps to
 *       1), because they are two boosts to one probability rather than two independent rolls.</li>
 * </ul>
 *
 * <p>⚠ It is a <b>positive</b> effect on its bearer ({@link #isDebuff()} is {@code false}), exactly like 减伤: being
 * harder to control is good for the unit carrying it, and 「解除 N 个负面效果」 must not take it off.
 */
public class ClassResistBuff extends AbstractBuff {

    private final DebuffClass kind;

    /**
     * The resistance this buff contributes, as a fraction of 1 ({@code 0.35} = 「提高35%」, {@code 1.0} = 免疫).
     */
    private final double percent;

    /**
     * @param kind      which family of states it protects against (控制类 / 持续伤害类)
     * @param percent   the resistance fraction, in {@code (0, 1]}
     * @param turns     how many of the carrier's turns it lasts
     * @param permanent {@code true} for 「整场战斗」 — the shape every 行迹 with this sentence has
     */
    public ClassResistBuff(DebuffClass kind, double percent, int turns, boolean permanent) {
        super(turns, false, permanent);
        if (kind == null) {
            throw new IllegalArgumentException(
                    "ClassResistBuff needs a class: it says WHICH family of states it resists");
        }
        // `!(percent > 0)` rather than `percent <= 0`: it also rejects NaN, and a resistance of exactly 0 is a buff
        // that provably does nothing (the rule that stated it would be a rule with no effect).
        if (!(percent > 0) || percent > 1) {
            throw new IllegalArgumentException(
                    "ClassResistBuff percent must be in (0, 1], got " + percent
                            + " (1.0 = 免疫, 0.35 = 「抵抗…的概率提高35%」)");
        }
        if (turns < 1) {
            throw new IllegalArgumentException("ClassResistBuff turns must be >= 1, got " + turns);
        }
        this.kind = kind;
        this.percent = percent;
    }

    /**
     * A timed one (the ordinary case: the op passes the rule's own {@code turns}).
     */
    public ClassResistBuff(DebuffClass kind, double percent, int turns) {
        this(kind, percent, turns, false);
    }

    public DebuffClass getKind() {
        return kind;
    }

    public double getPercent() {
        return percent;
    }

    /**
     * A resistance never stops its carrier acting. {@code true} is not a placeholder: {@code false} would make the
     * manager treat "expired while unable to act" as a blocked turn — the same trap {@code DotBuff.canAct} documents.
     */
    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public void applyEffect(CanHit target) {
        // Nothing to install: the resist check reads this buff.
    }

    @Override
    public void removeBuff(CanHit target) {
        // Nothing to take off, for the same reason.
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }

    /**
     * Identity is <b>(class, applier)</b>, not class alone.
     *
     * <p>Two different appliers are two <b>contributions</b> to one probability, and they must coexist: 克拉拉's 守护
     * is a permanent 35%, while a 「【防火墙】…免疫控制类负面状态」 is a one-turn 100%. Were kind alone the identity, the
     * second would <b>replace</b> the first and the trace would be gone for good the moment the timed immunity
     * expired — a wrong number with nothing to report.
     *
     * <p>Re-applying from the <b>same</b> applier is still a refresh, which is this engine's ordinary rule for every
     * other buff ({@code BuffManagerTest.sameKindBuffRefreshesInsteadOfStacking}) and the reason a re-cast ability
     * does not double its own resistance. The two <b>classes</b> coexist for the third reason: being harder to
     * control says nothing about being harder to burn.
     */
    @Override
    public boolean isSameKind(AbstractBuff other) {
        return other instanceof ClassResistBuff buff && buff.kind == kind && buff.source == source;
    }

    @Override
    public String toString() {
        return "ClassResistBuff[" + kind.value() + " " + (percent * 100) + "%, " + remainingDuration + "t]";
    }
}
