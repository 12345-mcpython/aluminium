package com.laosun.aluminium.models;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.models.event.CanHitEvent;
import com.laosun.aluminium.models.event.MoveableEvent;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;


@Getter
@Setter
@ToString(callSuper = true)
public abstract class CanHit implements MoveableEvent, CanHitEvent {
    private String name;
    private double health;
    private double maxHealth;
    private double defence;
    private double attack;
    private double speed;
    private double effectHitRate = 0;
    private double effectResistance = 0;
    private double critChance = 0;
    private double critAttack = 0;
    private double breakEffect = 0;
    private double outgoingHealingBoost = 0;
    private boolean death = false;
    private Camp camp;

    // IMPORTANT: EDIT THIS NOT EDIT ABOVE!!!!!!!!!!!!!!
    private double inBattleHealth;
    private double inBattleMaxHealth;
    private double inBattleAttack;
    private double inBattleDefence;

    public CanHit(String name, Camp camp, double health, double defence, double attack, double speed) {
        this.name = name;
        this.camp = camp;
        this.health = health;
        this.maxHealth = health;
        this.defence = defence;
        this.attack = attack;
        this.speed = speed;
        // 初始化战斗属性（与基础属性一致，可通过buff修改）
        this.inBattleDefence = defence;
        this.inBattleHealth = health;
        this.inBattleMaxHealth = health;
        this.inBattleAttack = attack;
    }


    // ------------------- 事件默认实现（子类可重写） -------------------
    @Override
    public void onBattleStart(Battle battle, CanHit canHit) {
        System.out.printf("[%s] JOIN BATTLE（CAMP：%s）%n", getName(), getCamp().name());
    }
}