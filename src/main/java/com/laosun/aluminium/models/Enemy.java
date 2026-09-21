package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.utils.AttributeBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.laosun.aluminium.enums.AttributeType.*;

/**
 * An enemy combatant in the action queue.
 *
 * <p>Enemies are typically aligned with {@link Camp#ENEMY}; their attributes are scaled by
 * level / instance / elite-group multipliers in {@code EnemyScaler} and assembled by
 * {@code EnemyFactory}.
 */
@Getter
@Setter
@ToString(callSuper = true)
public class Enemy extends CanHit {

    /**
     * Per-element resistance (0.2 = 20% RES). An element missing from the table has no
     * resistance. Filled from {@code monster_config.json}'s {@code damage_resistance}.
     */
    private Map<DamageElement, Double> damageResist = Map.of();

    /**
     * 弱点元素（来自 {@code monster_config.json} 的 {@code stance_weak}）。默认空集合 = 无弱点
     * （数据里有 102 个条目没有这一项）。
     *
     * <p>这是**数据**：命中弱点元素可以削韧，判定与削韧机制在 P4-2 实现。
     */
    private Set<DamageElement> stanceWeak = Set.of();

    /**
     * 韧性当前值（P4 削韧会减它；数值由 {@code EnemyScaler} 给出：模板 × 等级组 × 实例系数）。
     */
    private double stance;

    /**
     * 韧性上限（= 初始 {@link #stance}）。
     */
    private double maxStance;

    /**
     * 韧性条数（多段韧性条，来自模板 {@code stance_count}）。
     */
    private int stanceCount;

    /**
     * 该怪自身的韧性属性（模板 {@code stance_type}）。
     */
    private DamageElement stanceType;

    /**
     * 是否处于击破状态（P4-2 判定、P4-4 恢复）。
     */
    private boolean broken;

    /**
     * 本次击破的元素（P4-3 击破伤害 / P4-5 DOT 类型用）。
     */
    private DamageElement brokenElement;

    /**
     * 击破状态剩余回合数（跳回合/推条由 P4-4 维护，P4-1 只留字段）。
     */
    private int brokenRemainTurns;

    /**
     * 身上的持续伤害（P4-5）。**按施加顺序结算**（HSR.md §7「先上先结算」），所以用 List 不用 Set。
     */
    private final List<Dot> dots = new ArrayList<>();

    public Enemy(String name, Camp camp, DoubleValue[] attributes) {
        super(name, camp, attributes);
    }

    public Enemy(String name, DoubleValue[] attributes) {
        super(name, Camp.ENEMY, attributes);
    }

    public static Enemy fromAttributes(String name, double health, double defence, double attack, double speed) {
        AttributeBuilder attributeBuilder = new AttributeBuilder();
        attributeBuilder.setBase(HEALTH, health);
        attributeBuilder.setBase(DEFENCE, defence);
        attributeBuilder.setBase(ATTACK, attack);
        attributeBuilder.setBase(SPEED, speed);
        return new Enemy(name, attributeBuilder.build());
    }

    /**
     * Whether the given element is one of this enemy's weaknesses.
     *
     * <p>P4-2 uses this as the single judgement point for「弱点削韧」——别再直接摸
     * {@link #stanceWeak}，否则以后改判定规则会漏掉调用点。
     *
     * @param element the damage element of an incoming hit（{@code null} → {@code false}）
     * @return {@code true} if the element is a weakness
     */
    public boolean isWeakTo(DamageElement element) {
        return element != null && stanceWeak.contains(element);
    }

    /**
     * 是否有韧性条（{@code maxStance > 0}）。
     *
     * <p>P4-2 的削韧/击破判定统一走这里，别各自去摸 {@link #maxStance}——数据里确实有韧性为 0 的怪。
     *
     * @return {@code true} if this enemy can be broken at all
     */
    public boolean hasToughnessBar() {
        return maxStance > 0;
    }

    /**
     * 削韧（P4-2 每段伤害调用一次）。
     *
     * <p><b>归零不自动击破</b>——击破判定要区分弱点击破/非弱点削韧（P4-2 的口径），
     * 所以这里只负责扣数并夹到 0。已击破的目标在恢复前不再削韧（韧性条是空的）。
     *
     * <p><b>返回实际消耗值（H-4）</b>：击破伤害必须按"这一段真的削掉了多少"结算，
     * 而不是按技能的标称削韧值——剩 10 点韧性挨一发 30 点技能，只有 10 点算数。
     * 调用方另需注意：超击破（P4-6）用的是**超出部分** {@code amount - consumed}，
     * 所以本方法的返回值与调用方手里的标称值要一起用，别只留一个。
     *
     * @param amount 削韧点数（技能 {@code stance_list} 的值 × 弱点/非弱点系数）
     * @return 实际从韧性条上扣掉的点数（0 = 没削动：已击破 / 非正数 / 条已空）
     */
    public double reduceStance(double amount) {
        if (broken || amount <= 0 || stance <= 0) {
            // stance <= 0：韧性条已经空了（正常情况下会同时 broken，但本方法的守卫不应假设调用方
            // 一定按顺序走）。显式挡掉才能保证返回值语义是 min(amount, 剩余韧性)，
            // 否则超击破会算出"超出部分 = amount - 0 = 整发"，对一条空的韧性条凭空产生超击破。
            return 0;
        }
        double consumed = Math.min(stance, amount);
        stance -= consumed;
        return consumed;
    }

    /**
     * 进入击破状态（P4-2 在韧性归零时调用）。
     *
     * @param element 造成击破的元素（{@code null} = 未知，不断言）
     */
    public void breakEnemy(DamageElement element) {
        broken = true;
        brokenElement = element;
        stance = 0;
    }

    /**
     * 退出击破状态并把韧性条填满（P4-4：击破持续回合结束时调用）。
     *
     * <p>多韧性条（{@link #stanceCount} {@code > 1}）的逐条消耗留给 P4-4，本任务只恢复满值。
     */
    public void recoverFromBroken() {
        broken = false;
        brokenElement = null;
        brokenRemainTurns = 0;
        stance = maxStance;
    }

    /**
     * 挂上一个持续伤害（P4-5）。同一元素可以叠多个（"先上先结算"，不做同类刷新）。
     *
     * @param dot 持续伤害
     */
    public void addDot(Dot dot) {
        if (dot != null) {
            dots.add(dot);
        }
    }

    /**
     * 移除一个持续伤害（结算完最后一次时由 {@code Battle.tickDots} 调用）。
     *
     * @param dot 持续伤害
     */
    public void removeDot(Dot dot) {
        dots.remove(dot);
    }
}
