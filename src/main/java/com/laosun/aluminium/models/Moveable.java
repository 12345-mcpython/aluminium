package com.laosun.aluminium.models;

import com.laosun.aluminium.models.event.MoveableEvent;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public abstract class Moveable implements MoveableEvent, Comparable<Moveable> {
    private double length;
    private double time;
    private double speed;

    public Moveable(double speed) {
        this.length = 0;
        this.time = 0;
        this.speed = speed;
    }

    public void move(double length) {
        this.length += length;
    }

    public int compareTo(Moveable moveable) {
        return Double.compare(this.length, moveable.getLength());
    }
}
