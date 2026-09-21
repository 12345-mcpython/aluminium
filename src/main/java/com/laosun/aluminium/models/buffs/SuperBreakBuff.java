package com.laosun.aluminium.models.buffs;

import com.laosun.aluminium.models.AbstractBuff;
import com.laosun.aluminium.models.CanHit;

/**
 * 超击破（P4-6）的触发标记：**纯标记，没有数值**。
 *
 * <p>语义（对应开拓者·同谐终结技【伴舞】："攻击处于弱点击破状态的敌方目标后，会将本次攻击的削韧值
 * 转化为 1 次超击破伤害"）——挂在**攻击方**身上；由它决定"这一发攻击能不能产生超击破段"，
 * 至于产生多少，由 {@code SkillExecutor} 按下面的口径算。
 *
 * <h2>削韧值的分配口径（别写错）</h2>
 * 设敌人剩余韧性 {@code T}、技能标称削韧 {@code S}：
 * <pre>
 *   情形                    击破伤害用          超击破伤害用
 *   T &gt; S（没打空）        无                  无
 *   S &gt;= T（这一发打破）    min(S, T) 实际值    max(0, S - T) 超出部分
 *   敌人已 broken（T = 0）  无                  S（整发都算超出）
 * </pre>
 * 两条链分的是同一个 {@code S}，相加恒等于 {@code S}，不重不漏。
 * 所以"破韧的那一发"会**同时**产生击破伤害与超击破伤害（例：技能 60、怪物 30 韧性
 * → 技能伤害 + 30 击破伤害 + 30 超击破伤害）。
 *
 * <p>注意超击破仍然**只对弱点属性生效** —— 它沿用"只有命中弱点才削韧"这条前提
 * （见 {@code Battle.reduceToughness}），非弱点攻击打已击破的敌人不会产生超击破段。
 *
 * <p>模板同 {@link VulnerabilityBuff}：不改属性、没有需要清理的持久状态，
 * 所以 {@code applyEffect} / {@code removeBuff} 都是空的，只有 {@code tickEffect} 减时长。
 * 与它的区别是：本类连 {@code DamageEvent} 都不实现 —— 它不在结算时注入乘区，
 * 而是由 {@code SkillExecutor} 主动额外构造一段 {@code DamageType.SUPER_BREAK} 伤害。
 *
 * @see com.laosun.aluminium.enums.DamageType#SUPER_BREAK
 */
public class SuperBreakBuff extends AbstractBuff {

    /**
     * @param duration 持续回合数（模板同 {@link VulnerabilityBuff}：后置 buff，随 {@code afterMove} 递减）
     */
    public SuperBreakBuff(int duration) {
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
