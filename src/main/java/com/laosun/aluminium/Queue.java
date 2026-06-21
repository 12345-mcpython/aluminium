package com.laosun.aluminium;

import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Signal;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Getter
@ToString
public final class Queue {
    private static final double ACTION_THRESHOLD = 10000;

    private final List<CanHit> combatantQueue = new ArrayList<>();

    private final List<Signal> actionQueue = new ArrayList<>();

    public Queue(List<CanHit> initialCombatants) {
        addCombatants(initialCombatants);
    }

    public void addCombatant(CanHit combatant) {
        if (combatant != null && !combatantQueue.contains(combatant)) {
            combatantQueue.add(combatant);
            actionQueue.add(new Signal(combatant));
            calcTime();
        }
    }

    public void addCombatants(List<CanHit> combatants) {
        combatants.forEach(this::addCombatant);
    }

    public void initialize() {
        actionQueue.forEach(m -> {
            m.setLength(0);
            m.setTime(0);
        });
        calcTime();
    }

    public void calcTime() {
        actionQueue.forEach(moveable -> {
            if (moveable.getSpeed() <= 0) {
                moveable.setTime(Double.MAX_VALUE);
                return;
            }
            double remaining = ACTION_THRESHOLD - moveable.getLength();
            moveable.setTime(remaining / moveable.getSpeed());
        });
        actionQueue.sort(Comparator.comparingDouble(Signal::getTime));
    }

    public double move() {
        if (actionQueue.isEmpty()) return 0;
        Signal next = actionQueue.getFirst();
        double timePassed = next.getTime();

        actionQueue.forEach(m -> {
            if (m.getSpeed() > 0) {
                m.setLength(Math.min(ACTION_THRESHOLD, m.getLength() + timePassed * m.getSpeed()));
            }
        });
        calcTime();
        return timePassed;
    }

    public void setTopZero() {
        actionQueue.getFirst().setLength(0);
        actionQueue.getFirst().setTime(0);
        calcTime();
    }

    public void printActionQueue() {
        if (actionQueue.isEmpty()) {
            System.out.println("Action queue is empty.");
            return;
        }
        System.out.println("=== Action Queue Status ===");
        for (int i = 0; i < actionQueue.size(); i++) {
            Signal s = actionQueue.get(i);
            System.out.printf("[%d] name = %s, time=%.2f, length=%.2f, speed=%.2f%n",
                    i, s.getCanHit().getName(), s.getTime(), s.getLength(), s.getSpeed());
        }
        System.out.println("===========================");
    }
}