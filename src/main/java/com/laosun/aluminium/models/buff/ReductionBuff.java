package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource;
import com.laosun.aluminium.models.event.DamageEvent;

/**
 * Reduction: the damage the target takes is lowered by {@code ratio} (0.3 = -30%, entering the
 * multiplicative reduction zone). Attached to the **defending side**.
 *
 * <p>Like {@link VulnerabilityBuff}, it is a buff that "injects a damage zone at settlement time":
 * it changes no attributes and holds no persistent state.
 * Its source tag is {@link ModifierSource#BUFF} (reduction = a buff on the defending side,
 * HSR.md §2.2).
 *
 * <p><b>Applies per side (C-1)</b>: {@code Battle.assemble} broadcasts the {@code DamageEvent} to
 * both the attacking and defending sides, so {@link AbstractBuff#owner} MUST be used to decide
 * "am I the defending side of this instance" — otherwise the reduction buff would also multiply the
 * holder's **own output** by 0.7.
 */
public class ReductionBuff extends AbstractBuff implements DamageEvent {
    private final double ratio;

    public ReductionBuff(int duration, double ratio) {
        super(duration, false);
        this.ratio = ratio;
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
        damage.addReduction(ratio, ModifierSource.BUFF, id);
    }
}
