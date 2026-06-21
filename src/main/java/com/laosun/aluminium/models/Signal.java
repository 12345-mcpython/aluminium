package com.laosun.aluminium.models;

import com.laosun.aluminium.enums.AttributeType;
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

    public Signal(CanHit moveable) {
        this.canHit = moveable;
        this.speed = moveable.getAttribute(AttributeType.SPEED).get();
    }

    @Override
    public int compareTo(@NotNull Signal o) {
        return Double.compare(this.time, o.getTime());
    }

    public Signal clone() throws CloneNotSupportedException {
        return (Signal) super.clone();
    }
}
