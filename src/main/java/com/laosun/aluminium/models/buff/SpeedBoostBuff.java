package com.laosun.aluminium.models.buff;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.DoubleValue;

/**
 * Speed boost (a test double from before P7-3 / also a minimal sample for P10-4):
 * {@code SPEED × (1 + rate)}.
 *
 * <p>Its reason to exist is to **trigger a speed change**, so that the reordering chain
 * {@code Battle.onSpeedChanged} → {@code Queue.refreshSpeed} can be verified to be really connected
 * (P7 fix E2). The production speed buff will be made together with P10-4.
 *
 * <p>The template for attribute-type buffs: {@code applyEffect} attaches a {@link DoubleValue.Modifier}
 * (carrying its own id), {@code removeBuff} removes it precisely by id, and {@code tickEffect} only
 * decrements the duration.
 */
public class SpeedBoostBuff extends AbstractBuff {
    private final double rate;

    public SpeedBoostBuff(int duration, double rate) {
        super(duration, false);          // post-move buff: decremented with afterMove
        this.rate = rate;
    }

    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public void applyEffect(CanHit target) {
        target.getAttribute(AttributeType.SPEED)
                .addModifier(DoubleValue.Modifier.multiplyPercent(rate,
                        DoubleValue.Modifier.ModifierSource.BUFF, id));
        target.notifySpeedChanged();
    }

    @Override
    public void removeBuff(CanHit target) {
        DoubleValue speed = target.getAttribute(AttributeType.SPEED);
        DoubleValue.Modifier modifier =
                speed.findFirstBySourceAndId(DoubleValue.Modifier.ModifierSource.BUFF, id);
        if (modifier != null) {
            speed.removeModifier(modifier);
        }
        target.notifySpeedChanged();
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }
}
