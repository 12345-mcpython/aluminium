package com.laosun.aluminium.models;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
public class Character extends CanHit {
    public Character(String name, double health, double maxHealth, double defence, double attack, double speed) {
        super(name, health, maxHealth, defence, attack, speed);
    }
}
