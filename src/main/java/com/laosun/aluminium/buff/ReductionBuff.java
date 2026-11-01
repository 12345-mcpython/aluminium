package com.laosun.aluminium.buff;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue.Modifier.ModifierSource;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.event.DamageEvent;

/**
 * Reduction: the damage the target takes is lowered by {@code ratio} (0.3 = -30%, entering the
 * multiplicative reduction zone). Attached to the defending side.
 *
 * <p>Like {@link VulnerabilityBuff}, it is a buff that "injects a damage zone at settlement time":
 * it changes no attributes and holds no persistent state.
 * Its source tag is {@link ModifierSource#BUFF} (reduction = a buff on the defending side,
 * HSR.md §2.2).
 *
 * <p><b>Applies per side (C-1)</b>: {@code Battle.assemble} broadcasts the {@code DamageEvent} to
 * both the attacking and defending sides, so {@link AbstractBuff#owner} MUST be used to decide
 * "am I the defending side of this instance" - otherwise the reduction buff would also multiply the
 * holder's own output by 0..
 */
public class ReductionBuff extends AbstractBuff implements DamageEvent {

    /**
     * The one damage type this reduction is about, or {@code null} for every type (see {@link VulnerabilityBuff}).
     */
    private final com.laosun.aluminium.enums.DamageType scope;

    private final double ratio;

    /**
     * The share asked for at EVERY settlement, or {@code null} for a fixed {@link #ratio}.
     */
    private java.util.function.DoubleSupplier liveRatio;

    /**
     * @param share the live share, read at every settlement
     */
    public void makeLive(java.util.function.DoubleSupplier share) {
        this.liveRatio = share;
    }

    /**
     * @return the share to inject NOW
     */
    private double share() {
        return liveRatio == null ? ratio : liveRatio.getAsDouble();
    }

    public ReductionBuff(int duration, double ratio) {
        this(duration, ratio, false);
    }

    /**
     * @param permanent {@code true} = the reduction lasts until the battle ends and is never counted down
     *                  ({@link AbstractBuff#isPermanent()}); {@code duration} is then a placeholder. This is
     *                  what a relic set's "Reduces DMG taken by 8%" needs: a passive with no turn count
     * @throws IllegalArgumentException when {@code ratio} is not positive (see {@link VulnerabilityBuff})
     */
    public ReductionBuff(int duration, double ratio, boolean permanent) {
        this(duration, ratio, permanent, null);
    }

    /**
     * @param scope the only damage type this reduction applies to ({@code null} = all of them)
     * @throws IllegalArgumentException when {@code ratio} is not positive (see {@link VulnerabilityBuff})
     */
    public ReductionBuff(int duration, double ratio, boolean permanent,
                         com.laosun.aluminium.enums.DamageType scope) {
        super(duration, false, permanent);
        if (!(ratio > 0)) {
            throw new IllegalArgumentException(
                    "ReductionBuff needs a positive ratio, got " + ratio
                            + " (an increase in damage taken is VulnerabilityBuff)");
        }
        this.ratio = ratio;
        this.scope = scope;
    }

    /**
     * Whether two reductions are the same effect: only when they come from the <b>same rule</b> and cover the same
     * damage type. Two independent sentences (Clara's talent 「受到的伤害降低 10%」 and her Eidolon 4 「降低 30%」) are two
     * zones and multiply together per the canon's 减伤区 = Π(1 - 减伤); the class-level default would let the second
     * evict the first, which silently loses a sentence. Re-applying the SAME rule still refreshes rather than stacks.
     */
    @Override
    public boolean isSameKind(AbstractBuff other) {
        return other instanceof ReductionBuff reduction
                && reduction.scope == this.scope
                && java.util.Objects.equals(reduction.ruleId, this.ruleId);
    }

    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public void applyEffect(CanHit target) {
        // changes no attributes
    }

    @Override
    public void removeBuff(CanHit target) {
        // no persistent state
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }

    @Override
    public void onDamage(Battle battle, Damage damage) {
        if (!damage.isOnDefenderSide(owner)) {
            return;                                  // C-1: reduction only blocks "the hit I take", it does not weaken "the hit I deal"
        }
        damage.addReduction(share(), ModifierSource.BUFF, id);
    }
}
