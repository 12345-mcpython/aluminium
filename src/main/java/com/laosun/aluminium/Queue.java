package com.laosun.aluminium;

import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Signal;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A turn-based action queue that determines combatant turn order by speed.
 *
 * <p>Each combatant gets a {@link Signal} tracking speed, accumulated action length,
 * and predicted time until the next action. The time to next action is computed as:
 * <pre>{@code time = (ACTION_THRESHOLD - length) / speed}</pre> where {@code ACTION_THRESHOLD = 10000}.
 *
 * <p>The combatant with the smallest time value acts first. When a combatant acts
 * ({@link #move()}), time passes, all combatants advance their action length proportionally,
 * and the queue is re-sorted.
 *
 * <p>Usage:
 * <pre>{@code
 * Queue q = new Queue(List.of(char1, char2, char3));
 * q.initialize();
 * q.move(); // advance to next combatant's turn
 * q.setTopZero(); // reset the current combatant's action length
 * }</pre>
 */
@Getter
@ToString
public final class Queue {
    /** The distance value that triggers an action (set by game mechanics). */
    private static final double ACTION_THRESHOLD = 10000;

    /** All combatants participating in the action queue. */
    private final List<CanHit> combatantQueue = new ArrayList<>();

    /** Action signals ordered by predicted time to next action. */
    private final List<Signal> actionQueue = new ArrayList<>();

    /**
     * Creates a queue with an initial set of combatants.
     *
     * @param initialCombatants the starting combatants, may be empty
     */
    public Queue(List<CanHit> initialCombatants) {
        addCombatants(initialCombatants);
    }

    /**
     * Adds a single combatant to the queue and recalculates turn order.
     *
     * @param combatant the combatant to add; null or duplicates are ignored
     */
    public void addCombatant(CanHit combatant) {
        if (combatant != null && !combatantQueue.contains(combatant)) {
            combatantQueue.add(combatant);
            actionQueue.add(new Signal(combatant));
            calcTime();
        }
    }

    /**
     * Adds multiple combatants at once.
     *
     * @param combatants the combatants to add
     */
    public void addCombatants(List<CanHit> combatants) {
        combatants.forEach(this::addCombatant);
    }

    /**
     * Resets all action lengths and times to zero, then recalculates.
     */
    public void initialize() {
        actionQueue.forEach(m -> {
            m.setLength(0);
            m.setTime(0);
        });
        calcTime();
    }

    /**
     * Recomputes predicted times for all signals and sorts by time (ascending).
     *
     * <p>Time = {@code (ACTION_THRESHOLD - length) / speed}. Combatants with zero
     * or negative speed are pushed to the end of the queue.
     */
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

    /**
     * Advances to the next combatant's action.
     *
     * <p>Time passes equal to the first combatant's remaining time. All combatants
     * advance their action length accordingly. The queue is then re-sorted.
     *
     * @return the amount of time that passed
     */
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

    /**
     * Resets the current (first) combatant's action length, making them
     * need a full cycle before acting again. Re-sorts the queue.
     */
    public void setTopZero() {
        actionQueue.getFirst().setLength(0);
        actionQueue.getFirst().setTime(0);
        calcTime();
    }

    /**
     * Prints the current action queue status to stdout.
     */
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
