package com.laosun.aluminium;

import com.laosun.aluminium.models.Moveable;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@Getter
@ToString
public class Queue {
    private final ArrayList<Moveable> queue = new ArrayList<>();

    public Queue() {
    }

    public <T extends Moveable> void add(List<T> list) {
        queue.addAll(list);
    }

    public void initialize() {
        for (Moveable moveable : queue) {
            moveable.setTime(10000.0 / moveable.getSpeed());
        }
    }

    public void move() {
    }

    public Moveable getTop() {
        return Collections.max(queue, Comparator.comparingDouble(Moveable::getLength));
    }

    public Moveable getFastest() {
        return Collections.min(queue, Comparator.comparingDouble(Moveable::getTime));
    }
}
