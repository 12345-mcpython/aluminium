package com.laosun.aluminium.models.buffs;

import com.laosun.aluminium.models.AbstractBuff;
import com.laosun.aluminium.models.CanHit;

/**
 * 嘲讽（P5-2）：**纯标记，没有数值**。
 *
 * <p>语义（作者口径，不是"按百分比提高仇恨值"）：
 * <blockquote>
 * 嘲讽 buff 只要被附加，攻击方的**单体攻击**与**扩散攻击的中心**
 * 就只能选中被附加嘲讽的那个个体。**双向生效**——我方单体/扩散打敌方时同理。
 * </blockquote>
 *
 * <p>所以它**不是** {@code aggroOf} 里的乘法（乘法只能提高概率，做不到"只能选中"），
 * 而是目标选择阶段的**硬约束**，落在 {@code TargetSelector} 里：
 * <ul>
 *   <li>候选集里存在"身上挂着本 buff 的存活个体" → 直接返回它，跳过仇恨加权；</li>
 *   <li>群攻（AOE）本来就打全体，不受影响；</li>
 *   <li>嘲讽者已死亡 / 不在被打的那一方 → 约束失效，退回仇恨加权（不能强制选中尸体）。</li>
 * </ul>
 *
 * <p>模板同 {@link VulnerabilityBuff}：后置 buff（随 {@code afterMove} 递减时长），
 * 不改属性、无持久状态。
 */
public class TauntBuff extends AbstractBuff {

    /**
     * @param duration 持续回合数
     */
    public TauntBuff(int duration) {
        super(duration, false);
    }

    @Override
    public boolean canAct() {
        return true;
    }

    @Override
    public void applyEffect(CanHit target) {
        // 纯标记：不改属性
    }

    @Override
    public void removeBuff(CanHit target) {
        // 同上：没有持久状态可清
    }

    @Override
    public void tickEffect(CanHit target) {
        decreaseDuration();
    }
}
