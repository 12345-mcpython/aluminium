package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.DamageElement;
import lombok.Getter;

/**
 * 击破附带的持续伤害（P4-5）：灼烧（火）/ 触电（雷）/ 裂伤（物理）/ 风化（风）。
 *
 * <p>独立于 Buff 体系的最简单形态——只管"每回合扣多少、还剩几回合"，
 * 每回合开始由 {@code Battle.tickDots(Enemy)} 按**施加顺序**结算（HSR.md §7「先上先结算」）。
 *
 * <p>DOT 伤害走完整乘区（吃增伤、吃防御/抗性/易伤），但**不可暴击**
 * ——由 {@link com.laosun.aluminium.enums.DamageType#DOT} 自己的
 * {@code (crittable=false, boostable=true)} 表达，不需要在这里判。
 */
@Getter
public class Dot {

    /**
     * 施加者（{@code Damage} 的 attacker 不允许为 null，所以必须记来源）。
     */
    private final CanHit source;
    /**
     * 持续伤害元素：火 / 雷 / 物理 / 风（冰=冻结、量子=纠缠、虚数=禁锢，P10-1 统一成表）。
     */
    private final DamageElement element;
    /**
     * 每次结算的基础伤害（击破基数 × {@code Constant.DOT_RATIO}）。
     */
    private final double baseDamage;
    /**
     * 剩余结算次数。
     */
    private int remainingTurns;

    /**
     * @param source         施加者
     * @param element        持续伤害元素
     * @param baseDamage     每次结算的基础伤害（还没过乘区）
     * @param remainingTurns 还能结算几次
     */
    public Dot(CanHit source, DamageElement element, double baseDamage, int remainingTurns) {
        this.source = source;
        this.element = element;
        this.baseDamage = baseDamage;
        this.remainingTurns = remainingTurns;
    }

    /**
     * 结算一次（由 {@code Battle.tickDots} 在扣完血后调用）。
     *
     * @return {@code true} = 这是最后一次，调用方应把它从目标身上移除
     */
    public boolean tick() {
        remainingTurns--;
        return remainingTurns <= 0;
    }
}
