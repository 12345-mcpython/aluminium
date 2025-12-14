package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.Camp;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
public class Enemy extends CanHit {
    public Enemy(String name, Camp camp, double health, double defence, double attack, double speed) {
        super(name, camp, health, defence, attack, speed);
    }
}