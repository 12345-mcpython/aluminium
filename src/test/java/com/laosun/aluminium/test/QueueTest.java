package com.laosun.aluminium.test;

import com.laosun.aluminium.Queue;
import com.laosun.aluminium.models.Character;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * 行动条基础行为。
 *
 * <p>⚠ 数值锚点含 **P7-1 的首轮系数 1.5**：速度 100 的首轮行动值 = {@code 10000/100 × 1.5 = 150}，
 * 速度 200 = {@code 50 × 1.5 = 75}；第二圈起回到正常周期（100 / 50）。
 */
public class QueueTest {
    @Test
    public void initialOrderBySpeed() {
        Character slow = Character.fromAttributes("slow", 100, 100, 100, 100);
        Character fast = Character.fromAttributes("fast", 100, 100, 100, 200);
        Queue q = new Queue(List.of(slow, fast));

        Assertions.assertEquals(fast, q.peekNext(), "速度高的先动（首轮系数对所有人一致，不改顺序）");
        Assertions.assertEquals(75, q.timeUntilNext(), 1e-9, "首轮：10000/200 × 1.5 = 75");
    }

    @Test
    public void moveAdvancesElapsedToNextActor() {
        Character slow = Character.fromAttributes("slow", 100, 100, 100, 100);
        Character fast = Character.fromAttributes("fast", 100, 100, 100, 200);
        Queue q = new Queue(List.of(slow, fast));

        double timePassed = q.move();

        Assertions.assertEquals(75, timePassed, 1e-9, "首轮：速度 200 等 75");
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
