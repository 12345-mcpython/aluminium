package com.laosun.aluminium.models.buffs;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.AbstractBuff;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.DoubleValue;

/**
 * 速度增益（P7-3 之前的测试替身 / 也是 P10-4 的一个最小样本）：
 * {@code SPEED × (1 + rate)}。
 *
 * <p>它存在的意义是**触发速度变化**，好验证 {@code Battle.onSpeedChanged} →
 * {@code Queue.refreshSpeed} 那条重排链路真的接通了（P7 修正 E2）。
 * 生产用的速度 buff 等 P10-4 一起做。
 *
 * <p>属性型 buff 的模板：{@code applyEffect} 挂 {@link DoubleValue.Modifier}（带自己的 id），
 * {@code removeBuff} 按 id 精确摘除，{@code tickEffect} 只减时长。
 */
public class SpeedBoostBuff extends AbstractBuff {
    private final double rate;

    public SpeedBoostBuff(int duration, double rate) {
        super(duration, false);          // 后置 buff：随 afterMove 递减
        this.rate = rate;
    }

    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public void applyEffect(CanHit target) {
        target.getAttribute(AttributeType.SPEED)
                .addModifier(DoubleValue.Modifier.multiplyPercent(rate,
                        DoubleValue.Modifier.ModifierSource.BUFF, id));
        target.notifySpeedChanged();
    }

    @Override
    public void removeBuff(CanHit target) {
        DoubleValue speed = target.getAttribute(AttributeType.SPEED);
        DoubleValue.Modifier modifier =
                speed.findFirstBySourceAndId(DoubleValue.Modifier.ModifierSource.BUFF, id);
        if (modifier != null) {
            speed.removeModifier(modifier);
        }
        target.notifySpeedChanged();
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }
}
