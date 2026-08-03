package com.laosun.aluminium.models.buffs;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.AbstractBuff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.DoubleValue;

public class BoostDamageBuff extends AbstractBuff {
    private final double rate;

    public BoostDamageBuff(CanHit source, int duration, double rate) {
        super(source, duration);
        this.rate = rate;
    }

    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public boolean applyEffect(CanHit target) {
        target.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).addModifier(DoubleValue.Modifier.pure(rate, DoubleValue.Modifier.ModifierSource.BUFF, id));
        return true;
    }

    @Override
    public void removeBuff(CanHit target) {
        DoubleValue attribute = target.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST);
        DoubleValue.Modifier targetMod = attribute.findFirstBySourceAndId(DoubleValue.Modifier.ModifierSource.BUFF, id);
        if (targetMod != null) attribute.removeModifier(targetMod);
    }

    @Override
    public void tickEffect(CanHit target) {
        remainingDuration--;
    }
}
