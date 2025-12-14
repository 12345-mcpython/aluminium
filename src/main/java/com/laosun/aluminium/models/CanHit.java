package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.action.Action;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.models.event.MoveableEvent;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.function.Function;

@Getter
@Setter
@ToString(callSuper = true)
public abstract class CanHit extends Moveable implements MoveableEvent {
    private String name;
    private double health;
    private double maxHealth;
    private double defence;
    private double attack;
    private boolean death = false;
    private Camp camp; // 阵营（关键：统一管理所有战斗角色）

    private double inBattleHealth;
    private double inBattleMaxHealth;
    private double inBattleAttack;
    private double inBattleDefence;

    // 行动选择器：外部自定义角色行动逻辑（核心灵活点）
    private Function<Battle, Action> actionSelector;

    public CanHit(String name, Camp camp, double health, double defence, double attack, double speed) {
        super(speed);
        this.name = name;
        this.camp = camp;
        this.health = health;
        this.maxHealth = health;
        this.defence = defence;
        this.attack = attack;
        // 初始化战斗属性（与基础属性一致，可通过buff修改）
        this.inBattleDefence = defence;
        this.inBattleHealth = health;
        this.inBattleMaxHealth = health;
        this.inBattleAttack = attack;
    }

    /**
     * 执行行动（调用行动选择器获取行动并执行）
     */
    public void performAction(Battle battle) {
        if (!isAlive()) {
            System.out.printf("[%s] DEATH，CAN'T MOVE %n", getName());
            return;
        }
        if (actionSelector == null) {
            System.out.printf("[%s] NO MOVE LOGIC，IGNORE MOVE %n", getName());
            return;
        }

        // 触发行动前事件
        onBeforeAction(battle, this);
        // 获取并执行行动
        Action action = actionSelector.apply(battle);
        if (action != null) {
            action.execute(battle, this);
        }
        // 触发行动后事件
        onAfterAction(battle, this, action);
    }

    /**
     * 受到伤害处理
     */
    public void takeDamage(double damage) {
        if (!isAlive()) return;
        this.inBattleHealth = Math.max(0, this.inBattleHealth - damage);
        // 触发受伤害事件
        onTakeDamage(damage);
        // 检查是否死亡
        if (this.inBattleHealth <= 0) {
            this.death = true;
            onDeath();
            System.out.printf("[%s] DIED！%n", getName());
        }
    }

    /**
     * 判断角色是否存活
     */
    public boolean isAlive() {
        return !death && inBattleHealth > 0;
    }

    // ------------------- 事件默认实现（子类可重写） -------------------
    @Override
    public void onBattleStart(Battle battle, Moveable moveable) {
        System.out.printf("[%s] JOIN BATTLE（CAMP：%s）%n", getName(), getCamp().name());
    }

    @Override
    public void onBeforeAction(Battle battle, Moveable moveable) {}

    @Override
    public void onAfterAction(Battle battle, Moveable moveable, Action action) {}

    @Override
    public void onBeAttacked(Battle battle, Moveable attacker, Action action) {}

    @Override
    public void onTakeDamage(double damage) {}

    @Override
    public void onDeath() {}
}