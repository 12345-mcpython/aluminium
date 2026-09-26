package com.laosun.aluminium.models.buffs;

import com.laosun.aluminium.models.AbstractBuff;
import com.laosun.aluminium.models.CanHit;

public class StunBuff extends AbstractBuff {
    public StunBuff(int duration) {
        super(duration, true);
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
        // Deliberately silent. This used to be `IO.println("Stun buff has been removed")`, which meant the
        // engine wrote a line to stdout **every time a stun wore off or was dispelled** -- noise in any
        // caller's output, and it showed up in the middle of Main's mechanics demo, between two of its own
        // lines. Nothing asserts the print, and no buff should be writing to stdout: the caller decides what
        // to report, and a buff that wants to be observable says so through state the caller can read
        // (BuffManager.hasBuff(StunBuff.class)) rather than by printing.
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }
}
