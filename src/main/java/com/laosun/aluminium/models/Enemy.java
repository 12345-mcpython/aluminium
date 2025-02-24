package com.laosun.aluminium.models;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
public class Enemy extends CanHit {
    public Enemy(String name, double health, double defense, double attack, double speed) {
        super(name, health, defense, attack, speed);
    }
}
