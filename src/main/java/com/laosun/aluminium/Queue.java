package com.laosun.aluminium;

import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import lombok.Getter;
import lombok.ToString;

import java.util.*;
import java.util.stream.Collectors;

import com.laosun.aluminium.models.Moveable;
import com.laosun.aluminium.models.CanHit;

/**
 * The battle queue like HSR.
 * {@link com.laosun.aluminium.models.Character} and {@link com.laosun.aluminium.models.Enemy} can move with this queue.
 * @see Moveable
 *
 * @author laosun
 * @since core version 1.0.0
 */
@Getter
@ToString
public final class Queue {
    /**
     * storage the object can move.
     */
    private final ArrayList<Moveable> queue = new ArrayList<>();

    private final ArrayList<Character> characters = new ArrayList<>();

    private final ArrayList<Enemy> enemies = new ArrayList<>();

    public <T extends Moveable> void add(List<T> list) {
        queue.addAll(list);
    }

    public void initialize() {
        this.add(characters);
        this.add(enemies);
        calcTime();
    }

    public Queue(List<Character> characters, List<Enemy> enemies) {
        this.characters.addAll(characters);
        this.enemies.addAll(enemies);
        initialize();
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
        double time = queue.getFirst().getTime();
        for (Moveable moveable : queue) {
            moveable.setLength(moveable.getLength() + time * moveable.getSpeed());
            if (moveable.getLength() > 10000.0) {
                moveable.setLength(10000.0);
            }
        }
        calcTime();
    }

    public void print() {
        System.out.println("name\tlength\ttime\tspeed");
        for (Moveable m : this.queue) {
            System.out.println(((CanHit) m).getName() + "\t" + Math.round(m.getLength()) + "\t\t" + Math.round(m.getTime()) + "\t\t" + m.getSpeed());
        }
        System.out.println();
    }

    public void setTopZero() {
        queue.getFirst().setLength(0);
        calcTime();
    }
}
