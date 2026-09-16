package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.utils.AttributeBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

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
}
