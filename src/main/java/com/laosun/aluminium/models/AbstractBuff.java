package com.laosun.aluminium.models;

import lombok.Setter;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractBuff implements Buff {
    protected CanHit source;
    protected int remainingDuration;
    protected final int id;
    protected final boolean isEarlyBuff;

    /**
     * Who this buff is currently attached to (written by {@link BuffManager#addBuff(AbstractBuff)} at
     * attach time).
     *
     * <p>Why it exists: {@code Battle.assemble} broadcasts {@code DamageEvent} to **both the attacker and
     * the target**, and buffs like "vulnerability (易伤, a debuff on the target)" / "reduction (减伤, a buff
     * on the target)" that only inject a damage zone during settlement MUST know whether they are the target
     * of this instance — otherwise vulnerability attached to an enemy would also boost the damage when that
     * enemy itself attacks.
     *
     * <p>It is not the same thing as {@link #source} (the applier), do not confuse them: {@code source} is
     * who applied it, {@code owner} is who it is attached to.
     */
    @Setter
    protected CanHit owner;


    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);

    public AbstractBuff(int duration, boolean isEarlyBuff) {
        this.remainingDuration = duration;
        this.isEarlyBuff = isEarlyBuff;
        this.id = ID_GENERATOR.getAndIncrement();
    }

    @Override
    public void setSource(CanHit h) {
        source = h;
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

    public boolean isSameKind(AbstractBuff other) {
        return this.getClass() == other.getClass();
    }

    protected void decreaseDuration() {
        remainingDuration--;
    }
}