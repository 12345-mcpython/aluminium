package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
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


    // ------------------- 事件默认实现（子类可重写） -------------------
    @Override
    public void onBattleStart(Battle battle, Moveable moveable) {
        System.out.printf("[%s] JOIN BATTLE（CAMP：%s）%n", getName(), getCamp().name());
    }

    @Override
    public void onBeforeAction(Battle battle, Moveable moveable) {}

    @Override
    public void onAfterAction(Battle battle, Moveable moveable) {}

    @Override
    public void onBeAttacked(Battle battle, Moveable attacker) {}

    @Override
    public void onTakeDamage(double damage) {}

    @Override
    public void onDeath() {}
}