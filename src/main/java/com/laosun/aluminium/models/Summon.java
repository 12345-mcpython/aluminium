package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.Camp;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
public class Summon extends CanHit {
    public Summon(String name, DoubleValue health, DoubleValue defence, DoubleValue attack, DoubleValue speed) {
        super(name, Camp.PLAYER, health, defence, attack, speed);
    }
}