package com.laosun.aluminium.models;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
public abstract class CanHit extends Moveable {
    private String name;
    private double health;
    private double maxHealth;
    private double defence;
    private double attack;
    private boolean death = false;

    private double inBattleHealth;
    private double inBattleMaxHealth;
    private double inBattleAttack;
    private double inBattleDefence;

    public CanHit(String name, double health, double defense, double attack, double speed) {
        super(speed);
        this.name = name;
        this.health = health;
        this.maxHealth = health;
        this.defence = defense;
        this.attack = attack;
        this.inBattleDefence = defense;
        this.inBattleHealth = health;
        this.inBattleMaxHealth = health;
        this.inBattleAttack = attack;
    }
}
