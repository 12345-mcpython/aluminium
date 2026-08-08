package com.laosun.aluminium.models.buffs;

import com.laosun.aluminium.models.AbstractBuff;
import com.laosun.aluminium.models.CanHit;

public class TestBuff extends AbstractBuff {
    public TestBuff(int duration) {
        super(duration, false);
    }

    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public void applyEffect(CanHit target) {
        IO.println("TestBuff: applyEffect");
    }

    @Override
    public void removeBuff(CanHit target) {
        IO.println("TestBuff: removeBuff");
    }

    @Override
    public void tickEffect(CanHit target) {
        IO.println("TestBuff: tickEffect");
        decreaseDuration();
    }
}
