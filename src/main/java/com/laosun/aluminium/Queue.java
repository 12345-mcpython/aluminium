package com.laosun.aluminium;

import lombok.Getter;
import lombok.ToString;

import java.util.*;

import com.laosun.aluminium.models.Moveable;
import com.laosun.aluminium.models.CanHit;

@Getter
@ToString
public final class Queue {
    private final ArrayList<Moveable> queue = new ArrayList<>();

    public <T extends Moveable> void add(List<T> list) {
        queue.addAll(list);
    }

    public void initialize() {
        calcTime();
    }

    public void reset() {
        for (Moveable m : queue) {
            m.setLength(0);
            m.setTime(0);
        }
        calcTime();
    }

    public void calcTime() {
        for (Moveable moveable : queue) {
            moveable.setTime((10000.0 - moveable.getLength()) / moveable.getSpeed());
        }
        queue.sort(Comparator.comparingDouble(Moveable::getTime));
    }

    public void move() {
        double time = getFastest().getTime();
        for (Moveable moveable : queue) {
            moveable.setLength(moveable.getLength() + time * moveable.getSpeed());
            if (moveable.getLength() > 10000.0) {
                moveable.setLength(10000.0);
            }
        }
        calcTime();
    }

    public Moveable getTop() {
        return Collections.max(queue, Comparator.comparingDouble(Moveable::getLength));
    }

    public Moveable getFastest() {
        return Collections.min(queue, Comparator.comparingDouble(Moveable::getTime));
    }

    public void print() {
        System.out.println("name\tlength\ttime\tspeed");
        for (Moveable m : queue) {
            System.out.println(((CanHit) m).getName() + "\t" + Math.round(m.getLength()) + "\t\t" + Math.round(m.getTime()) + "\t\t" + m.getSpeed());
        }
        System.out.println();
    }

    public void setTopZero() {
        queue.getFirst().setLength(0);
        calcTime();
    }
}
