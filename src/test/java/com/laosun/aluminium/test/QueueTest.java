package com.laosun.aluminium.test;

import com.laosun.aluminium.Queue;
import com.laosun.aluminium.models.Character;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class QueueTest {
    @Test
    public void initialOrderBySpeed() {
        Character slow = Character.fromAttributes("slow", 100, 100, 100, 100);
        Character fast = Character.fromAttributes("fast", 100, 100, 100, 200);
        Queue q = new Queue(List.of(slow, fast));

        Assertions.assertEquals(fast, q.peekNext());
        Assertions.assertEquals(50, q.timeUntilNext(), 1e-9);
    }

    @Test
    public void moveAdvancesElapsedToNextActor() {
        Character slow = Character.fromAttributes("slow", 100, 100, 100, 100);
        Character fast = Character.fromAttributes("fast", 100, 100, 100, 200);
        Queue q = new Queue(List.of(slow, fast));

        double timePassed = q.move();

        Assertions.assertEquals(50, timePassed, 1e-9);
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
