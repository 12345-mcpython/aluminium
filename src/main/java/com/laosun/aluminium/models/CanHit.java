package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.Camp;
import lombok.Getter;
import lombok.ToString;


@Getter
@ToString(callSuper = true)
public abstract class CanHit {
    private final String name;
    private final DoubleValue health;
    private final DoubleValue maxHealth;
    private final DoubleValue defence;
    private final DoubleValue attack;
    private final DoubleValue speed;
    private final DoubleValue effectHitRate = DoubleValue.zero();
    private final DoubleValue effectResistance = DoubleValue.zero();
    private final DoubleValue critChance = DoubleValue.zero();
    private final DoubleValue critAttack = DoubleValue.zero();
    private final DoubleValue breakEffect = DoubleValue.zero();
    private final DoubleValue outgoingHealingBoost = DoubleValue.zero();
    private boolean death = false;
    private final Camp camp;

    // IMPORTANT: EDIT THIS NOT EDIT ABOVE!!!!!!!!!!!!!!
    private final DoubleValue inBattleHealth;
    private final DoubleValue inBattleMaxHealth;
    private final DoubleValue inBattleAttack;
    private final DoubleValue inBattleDefence;

    public CanHit(String name, Camp camp, DoubleValue health, DoubleValue defence, DoubleValue attack, DoubleValue speed) {
        this.name = name;
        this.camp = camp;
        this.health = health;
        this.maxHealth = health;
        this.defence = defence;
        this.attack = attack;
        this.speed = speed;
        this.inBattleDefence = defence.clone();
        this.inBattleHealth = health.clone();
        this.inBattleMaxHealth = health.clone();
        this.inBattleAttack = attack.clone();
    }
}