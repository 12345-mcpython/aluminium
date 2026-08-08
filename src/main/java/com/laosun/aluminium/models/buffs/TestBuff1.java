package com.laosun.aluminium.models.buffs;

import com.laosun.aluminium.models.AbstractBuff;
import com.laosun.aluminium.models.CanHit;

public class TestBuff1 extends AbstractBuff {
    public TestBuff1(int duration) {
        super(duration, true);
    }

    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public void applyEffect(CanHit target) {
        IO.println("TestBuff1: applyEffect");
    }

    @Override
    public void removeBuff(CanHit target) {
        IO.println("TestBuff1: removeBuff");
    }

    @Override
    public void tickEffect(CanHit target) {
        IO.println("TestBuff1: tickEffect");
        decreaseDuration();
    }
}
