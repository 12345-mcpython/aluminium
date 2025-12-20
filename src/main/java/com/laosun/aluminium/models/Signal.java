package com.laosun.aluminium.models;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
public final class Signal implements Comparable<Signal>, Cloneable {
    private CanHit canHit;
    private double speed;
    private double time = 0;
    private double length = 0;
    private int id = 0;

    public Signal(CanHit moveable, double speed) {
        this.canHit = moveable;
        this.speed = speed;
    }

    @Override
    public int compareTo(@NotNull Signal o) {
        return Double.compare(this.time, o.getTime());
    }

    public Signal clone() throws CloneNotSupportedException {
        return (Signal) super.clone();
    }
}
