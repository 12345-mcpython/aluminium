package com.laosun.aluminium.test;

import com.laosun.aluminium.Queue;
import com.laosun.aluminium.models.Character;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Basic action bar behaviour.
 *
 * <p>⚠ The numeric anchors include **P7-1's first-round coefficient 1.5**: the first-round action
 * value for speed 100 = {@code 10000/100 × 1.5 = 150}, and for speed 200 = {@code 50 × 1.5 = 75};
 * from the second lap onwards it returns to the normal period (100 / 50).
 */
public class QueueTest {
    @Test
    public void initialOrderBySpeed() {
        Character slow = Character.fromAttributes("slow", 100, 100, 100, 100);
        Character fast = Character.fromAttributes("fast", 100, 100, 100, 200);
        Queue q = new Queue(List.of(slow, fast));

        Assertions.assertEquals(fast, q.peekNext(), "the higher speed acts first (the first-round coefficient is the same for everyone, so it does not change the order)");
        Assertions.assertEquals(75, q.timeUntilNext(), 1e-9, "first round: 10000/200 × 1.5 = 75");
    }

    @Test
    public void moveAdvancesElapsedToNextActor() {
        Character slow = Character.fromAttributes("slow", 100, 100, 100, 100);
        Character fast = Character.fromAttributes("fast", 100, 100, 100, 200);
        Queue q = new Queue(List.of(slow, fast));

        double timePassed = q.move();

        Assertions.assertEquals(75, timePassed, 1e-9, "first round: speed 200 waits 75");
        Assertions.assertEquals(fast, q.getNext());
        Assertions.assertEquals(0, q.timeUntilNext(), 1e-9);
    }

    @Test
    public void removeCombatantExcludesFromQueue() {
        Character a = Character.fromAttributes("a", 100, 100, 100, 100);
        Character b = Character.fromAttributes("b", 100, 100, 100, 200);
        Queue q = new Queue(List.of(a, b));

        Assertions.assertTrue(q.removeCombatant(b));
        Assertions.assertEquals(1, q.size());
        Assertions.assertEquals(a, q.peekNext());

        Assertions.assertFalse(q.removeCombatant(b));
    }

    @Test
    public void duplicateCombatantIsIgnored() {
        Character a = Character.fromAttributes("a", 100, 100, 100, 100);
        Queue q = new Queue(List.of(a));

        q.addCombatant(a);

        Assertions.assertEquals(1, q.size());
    }
}
