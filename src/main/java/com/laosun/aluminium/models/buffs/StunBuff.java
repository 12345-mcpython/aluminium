package com.laosun.aluminium.models.buffs;

import com.laosun.aluminium.models.AbstractBuff;
import com.laosun.aluminium.models.CanHit;

public class StunBuff extends AbstractBuff {
    public StunBuff(CanHit source, int duration) {
        super(source, duration, true);
    }

    @Override
    public boolean canAct() {
        return false;
    }

    @Override
    public void applyEffect(CanHit target) {

    }

    @Override
    public void removeBuff(CanHit target) {
        IO.println("Stun buff has been removed");
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }
}
