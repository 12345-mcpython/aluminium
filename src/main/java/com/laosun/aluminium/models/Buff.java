package com.laosun.aluminium.models;

/**
 * 增益/减益的契约：{@link com.laosun.aluminium.models.BuffManager} 只负责**管理**
 * （挂载 / 移除 / 每回合递减），**改属性是 buff 自己的事** ——
 * {@link #applyEffect} 挂 modifier、{@link #removeBuff} 摘掉。
 *
 * <p>⚠ 这条分工是**已经成立**的设计，不是待办：{@link AbstractBuff} 实现本接口，
 * 属性型 buff（`SpeedBoostBuff` / `BoostDamageBuff` / `TauntBuff` …）都在
 * `applyEffect` / `removeBuff` 里改属性。原先这里是三行 `// TODO`，容易被误读成
 * "架构还没做对"，所以改成说明。
 *
 * <p>另一个容易混的点：**注入型** buff（`VulnerabilityBuff` / `ReductionBuff`）没有持久状态，
 * 它们的 `applyEffect` / `removeBuff` 是空的，只在每次结算时把修正注入到**那一段**
 * `Damage` 的乘区上。两种的区分见 {@code engine.md} §10.2。
 */
public interface Buff {
    CanHit getSource();
    void setSource(CanHit source);

    // true can continue move
    // false can ignore the move
    boolean canAct();

    // apply attribute boost at the buff append
    void applyEffect(CanHit target);

    // remove attribute boost at the buff over
    void removeBuff(CanHit target);

    // tick it
    void tickEffect(CanHit target);

    int duration();
}
