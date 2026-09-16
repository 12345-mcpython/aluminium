package com.laosun.aluminium.models.buffs;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.AbstractBuff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue.Modifier.ModifierSource;
import com.laosun.aluminium.models.event.DamageEvent;

/**
 * 减伤：目标受到的伤害降低 {@code ratio}（0.3 = -30%，进入乘算的减伤区）。挂在**受击方**身上。
 *
 * <p>和 {@link VulnerabilityBuff} 一样是"结算时注入乘区"的 buff：不改属性、无持久状态。
 * 来源标记用 {@link ModifierSource#BUFF}（减伤 = 受击方增益，HSR.md §2.2）。
 */
public class ReductionBuff extends AbstractBuff implements DamageEvent {
    private final double ratio;

    public ReductionBuff(int duration, double ratio) {
        super(duration, false);
        this.ratio = ratio;
    }

    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public void applyEffect(CanHit target) {
        // 不改属性
    }

    @Override
    public void removeBuff(CanHit target) {
        // 无持久状态
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }

    @Override
    public void onDamage(Battle battle, Damage damage) {
        damage.addReduction(ratio, ModifierSource.BUFF, id);
    }
}
