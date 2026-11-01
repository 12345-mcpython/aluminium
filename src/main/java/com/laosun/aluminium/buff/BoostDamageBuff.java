package com.laosun.aluminium.buff;

import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.enums.AttributeType;

public class BoostDamageBuff extends AbstractBuff {
    private final double rate;

    public BoostDamageBuff(int duration, double rate) {
        super(duration, false);
        this.rate = rate;
    }

    /**
     * Whether two damage boosts are the same effect: only from the <b>same rule</b>. The canon's boost zone is ADDITIVE
     * (1 + Σ 增伤), and each instance carries its own modifier id, so two independent sentences must coexist; the
     * class-level default let the second evict the first and silently drop a sentence.
     */
    @Override
    public boolean isSameKind(AbstractBuff other) {
        return other instanceof BoostDamageBuff boost && java.util.Objects.equals(boost.ruleId, this.ruleId);
    }

    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public void applyEffect(CanHit target) {
        target.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).addModifier(DoubleValue.Modifier.pure(rate, DoubleValue.Modifier.ModifierSource.BUFF, id));
    }

    @Override
    public void removeBuff(CanHit target) {
        DoubleValue attribute = target.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST);
        DoubleValue.Modifier targetMod = attribute.findFirstBySourceAndId(DoubleValue.Modifier.ModifierSource.BUFF, id);
        if (targetMod != null) attribute.removeModifier(targetMod);
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }
}
