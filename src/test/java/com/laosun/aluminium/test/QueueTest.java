package com.laosun.aluminium.test;

import com.laosun.aluminium.Queue;
import com.laosun.aluminium.enums.Camp;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Moveable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import com.laosun.aluminium.models.Character;

public class QueueTest {
    @Test
    public void testQueue() {
        Character c1 = new Character("test 1", Camp.PLAYER, 100, 100, 100, 110);
        Character c2 = new Character("test 2", Camp.PLAYER, 200, 200, 100, 150);
        Character c3 = new Character("test 3", Camp.PLAYER, 100, 100, 100, 130);
        Character c4 = new Character("test 4", Camp.PLAYER, 200, 200, 100, 140);
        Character c5 = new Character("test 5", Camp.PLAYER, 100, 100, 100, 200);
        Character c6 = new Character("test 6", Camp.PLAYER, 200, 200, 100, 150);
        Character c7 = new Character("test 7", Camp.PLAYER, 100, 100, 100, 115);
        Character c8 = new Character("test 8", Camp.PLAYER, 200, 200, 100, 135);
        Character c9 = new Character("test 9", Camp.PLAYER, 100, 100, 100, 132);
        Character c10 = new Character("test 10", Camp.PLAYER, 200, 200, 100, 143);
        Queue q = new Queue(List.of(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10));
        for (Moveable moveable : q.getActionQueue()) {
            assertEquals(moveable.getTime(), 10000.0 / moveable.getSpeed());
        }
        q.progressTime();
        for (Moveable moveable : q.getActionQueue()) {
            assertEquals(moveable.getTime(), (10000.0 - moveable.getLength()) / moveable.getSpeed());
            assertEquals(moveable.getLength(), (10000.0 / q.getActionQueue().getFirst().getSpeed()) * moveable.getSpeed());
        }
        q.resetCombatantLength((CanHit) q.getActionQueue().getFirst());
        for (Moveable moveable : q.getActionQueue()) {
            assertEquals(moveable.getTime(), (10000.0 - moveable.getLength()) / moveable.getSpeed());
        }
    }

    @Test
    public void testMove() {

    }
}
