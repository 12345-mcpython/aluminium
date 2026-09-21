package com.laosun.aluminium.models;

import lombok.Setter;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractBuff implements Buff {
    protected CanHit source;
    protected int remainingDuration;
    protected final int id;
    protected final boolean isEarlyBuff;

    /**
     * 这个 buff 当前挂在谁身上（由 {@link BuffManager#addBuff(AbstractBuff)} 在挂载时写入）。
     *
     * <p>存在的理由：{@code Battle.assemble} 会把 {@code DamageEvent} 广播给**攻击方与受击方双方**，
     * 而"易伤（受击方负面）"/"减伤（受击方增益）"这类只在结算时注入乘区的 buff 必须知道
     * 自己是不是本段的受击方——否则挂在敌人身上的易伤会连它自己打人也一起加伤。
     *
     * <p>与 {@link #source}（施加者）不是一回事，别混：{@code source} 是谁挂的，{@code owner} 是挂在谁身上。
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