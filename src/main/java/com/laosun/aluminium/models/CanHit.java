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

    public CanHit(String name, double health, double maxHealth, double defense, double attack, double speed) {
        super(speed);
        this.name = name;
        this.health = health;
        this.maxHealth = maxHealth;
        this.defence = defense;
        this.attack = attack;
    }
}
