package com.laosun.aluminium;

import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Signal;
import lombok.Getter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A battle queue system that implements an Active Time Battle (ATB) mechanism.
 * <p>
 * This class manages combatants in a turn-based system where each character's
 * action time is determined by their speed attribute. The system maintains
 * two separate lists for different purposes:
 * <ul>
 *   <li>{@code combatantQueue} - An unsorted list of all combatants for reference</li>
 *   <li>{@code actionQueue} - A sorted list of moveable entities by their next action time</li>
 * </ul>
 * </p>
 * <p>
 * The ATB system works by having each combatant accumulate "length" (progress toward action)
 * based on their speed. When a combatant's length reaches the threshold (10000),
 * they are ready to act.
 * </p>
 *
 * @see CanHit
 * @see Signal
 * @see Camp
 */
@Getter
@ToString
public final class Queue {
    /**
     * The action threshold value when a combatant can perform an action.
     */
    private static final double ACTION_THRESHOLD = 10000.0;

    /**
     * An unsorted list containing all combatants in the battle.
     * Used for quick reference and lookup operations.
     */
    private final List<CanHit> combatantQueue = new ArrayList<>();

    /**
     * A sorted {@code Signal} list containing all moveable entities ordered by their next action time.
     * Used to determine turn order in the battle.
     */
    private final List<Signal> actionQueue = new ArrayList<>();

    /**
     * Constructs an empty battle queue.
     */
    public Queue() {
    }

    /**
     * Constructs a battle queue with initial combatants.
     *
     * @param initialCombatants the initial list of combatants to add to the queue
     */
    public Queue(List<CanHit> initialCombatants) {
        addCombatants(initialCombatants);
    }

    /**
     * Adds a single combatant to the battle queue.
     * <p>
     * The combatant will be added to both {@code combatantQueue} and {@code actionQueue}.
     * After addition, the action times are recalculated for all combatants.
     * </p>
     *
     * @param combatant the combatant to add to the queue
     * @throws NullPointerException if the combatant is null
     */
    public void addCombatant(CanHit combatant) {
        if (combatant != null && !combatantQueue.contains(combatant)) {
            combatantQueue.add(combatant);
            actionQueue.add(new Signal(combatant, combatant.getSpeed()));
            calcActionTimes();
        }
    }

    /**
     * Adds multiple combatants to the battle queue.
     * <p>
     * Each combatant will be added individually using {@link #addCombatant(CanHit)}.
     * </p>
     *
     * @param combatants the list of combatants to add to the queue
     */
    public void addCombatants(List<CanHit> combatants) {
        combatants.forEach(this::addCombatant);
    }

    /**
     * Removes a combatant from the battle queue.
     * <p>
     * The combatant will be removed from both {@code combatantQueue} and {@code actionQueue}.
     * </p>
     *
     * @param combatant the combatant to remove from the queue
     */
    public void removeCombatant(CanHit combatant) {
        combatantQueue.remove(combatant);
        removeSignalByCanHit(combatant);
    }

    private void removeSignalByCanHit(CanHit canHit) {
        actionQueue.removeIf(signal -> signal.getCanHit() == canHit);
    }

    public void addSignal(Signal signal) {
        actionQueue.add(signal);
    }

    /**
     * Initializes the battle queue by resetting all moveable entities.
     * <p>
     * This method sets the {@code length} and {@code time} of all moveable entities to 0,
     * then recalculates their action times. This is typically called at the start of a battle
     * or when resetting the battle state.
     * </p>
     */
    public void initialize() {
        actionQueue.forEach(m -> {
            m.setLength(0);
            m.setTime(0);
        });
        calcActionTimes();
    }

    /**
     * Calculates the next action time for all moveable entities in the queue.
     * <p>
     * The action time is calculated using the formula:
     * {@code time = (ACTION_THRESHOLD - length) / speed}
     * </p>
     * <p>
     * Special cases:
     * <ul>
     *   <li>If speed ≤ 0, the combatant's time is set to {@link Double#MAX_VALUE}
     *       (effectively never acting)</li>
     * </ul>
     * After calculation, the {@code actionQueue} is sorted in ascending order by action time.
     * </p>
     */
    public void calcActionTimes() {
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
     * Advances the battle timeline to allow the next combatant to act.
     * <p>
     * This method progresses time by the amount needed for the first combatant in the
     * {@code actionQueue} to reach the action threshold. All combatants accumulate
     * action length based on their speed during this time progression.
     * </p>
     *
     * @return the amount of time that has passed in the battle timeline
     */
    public double progressTime() {
        if (actionQueue.isEmpty()) return 0;
        Signal next = actionQueue.getFirst();
        double timePassed = next.getTime();

        // All combatants accumulate action length = time * speed
        actionQueue.forEach(m -> {
            if (m.getSpeed() > 0) {
                m.setLength(Math.min(ACTION_THRESHOLD, m.getLength() + timePassed * m.getSpeed()));
            }
        });
        calcActionTimes();
        return timePassed;
    }

    /**
     * Retrieves the next combatant who is ready to act.
     *
     * @return the next combatant in line to act, or {@code null} if no combatant is ready
     */
    public Signal getNextCombatant() {
        return actionQueue.stream()
                .findFirst()
                .orElse(null);
    }

    /**
     * Resets the action length of a specific combatant to 0.
     * <p>
     * This is typically called after a combatant has performed an action,
     * resetting their progress toward the next action.
     * </p>
     *
     * @param combatant the combatant whose action length should be reset
     */
    public void resetCombatantLength(Signal combatant) {
        if (combatant != null && actionQueue.contains(combatant)) {
            combatant.setLength(0);
            calcActionTimes();
        }
    }

    /**
     * Retrieves all alive combatants belonging to a specific camp.
     *
     * @param camp the camp to filter combatants by
     * @return a list of alive combatants in the specified camp
     */
    public List<CanHit> getAliveByCamp(Camp camp) {
        return combatantQueue.stream()
                .filter(c -> c.getHealth() != 0)
                .filter(c -> c.getCamp() == camp)
                .collect(Collectors.toList());
    }

    /**
     * Checks if all combatants in a specific camp have been defeated (health = 0).
     *
     * @param camp the camp to check
     * @return {@code true} if no alive combatants remain in the camp, {@code false} otherwise
     */
    public boolean isCampWiped(Camp camp) {
        return getAliveByCamp(camp).isEmpty();
    }

    /**
     * Adjusts the speed of a combatant and recalculates action times.
     * <p>
     * This method is used to apply speed buffs or debuffs to combatants.
     * After adjusting the speed, the action times are recalculated for all combatants.
     * </p>
     *
     * @param combatant the combatant whose speed should be adjusted
     * @param value     the new speed value
     */
    public void adjustSpeed(Signal combatant, double value) {
        if (combatant != null) {
            combatant.setSpeed(value);
            combatant.getCanHit().setSpeed(value);
            calcActionTimes();
        }
    }

    /**
     * Prints the current status of all combatants in the battle queue.
     * <p>
     * This method displays a formatted table showing each combatant's name, camp,
     * health, action length, and next action time. Useful for debugging and
     * monitoring battle state.
     * </p>
     */
    public void printStatus() {
        System.out.println("\n=== STATUS ===");
        System.out.printf("%-10s %-8s %-8s %-8s %-8s %-8s%n",
                "C NAME", "CAMP", "HEALTH", "LENGTH", "TIME", "ID");
        actionQueue.forEach(c -> {
            System.out.printf("%-10s %-8s %-8.0f %-8.0f %-8.1f %d %n",
                    c.getCanHit().getName(), c.getCanHit().getCamp().name(), c.getCanHit().getInBattleHealth(),
                    c.getLength(), c.getTime(), c.getId());

        });
        System.out.println("====================\n");
    }
}