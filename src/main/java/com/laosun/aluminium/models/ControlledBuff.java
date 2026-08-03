package com.laosun.aluminium.models;

public class ControlledBuff extends AbstractBuff {
    public ControlledBuff(CanHit source, int duration) {
        super(source, duration);
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
        IO.println("Controlled buff has been removed");
    }

    @Override
    public void tickEffect(CanHit target) {

    }
}
