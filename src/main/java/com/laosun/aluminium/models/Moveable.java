package com.laosun.aluminium.models;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public abstract class Moveable {
    private double length;
    private double time;
    private double speed;

    public Moveable(double speed) {
        this.length = 0;
        this.time = 0;
        this.speed = speed;
    }
}
