package com.laosun.aluminium.models;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractBuff implements Buff {
    protected final CanHit source;
    protected int remainingDuration;
    protected final int id;
    protected final boolean isEarlyBuff;


    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);

    public AbstractBuff(CanHit source, int duration, boolean isEarlyBuff) {
        this.source = source;
        this.remainingDuration = duration;
        this.isEarlyBuff = isEarlyBuff;
        this.id = ID_GENERATOR.getAndIncrement();
    }

    @Override
    public CanHit getSource() {
        return source;
    }

    @Override
    public int duration() {
        return remainingDuration;
    }

    @Override
    public abstract boolean canAct();

    @Override
    public abstract void applyEffect(CanHit target);

    @Override
    public abstract void removeBuff(CanHit target);

    @Override
    public abstract void tickEffect(CanHit target);

    protected void decreaseDuration() {
        IO.println("Decrease buff duration!");
        remainingDuration--;
    }
}