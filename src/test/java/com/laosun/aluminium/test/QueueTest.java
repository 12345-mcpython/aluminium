package com.laosun.aluminium.test;

import com.laosun.aluminium.Queue;
import com.laosun.aluminium.models.Moveable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import com.laosun.aluminium.models.Character;

public class QueueTest {
    @Test
    public void testQueue() {
        Character c1 = new Character("test 1", 100, 100, 100, 110);
        Character c2 = new Character("test 2", 200, 200, 100, 150);
        Character c3 = new Character("test 3", 100, 100, 100, 130);
        Character c4 = new Character("test 4", 200, 200, 100, 140);
        Character c5 = new Character("test 5", 100, 100, 100, 120);
        Character c6 = new Character("test 6", 200, 200, 100, 150);
        Character c7 = new Character("test 7", 100, 100, 100, 115);
        Character c8 = new Character("test 8", 200, 200, 100, 135);
        Character c9 = new Character("test 9", 100, 100, 100, 132);
        Character c10 = new Character("test 10", 200, 200, 100, 143);
        Queue q = new Queue(List.of(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10), List.of());
        for (Moveable moveable : q.getQueue()) {
            assertEquals(moveable.getTime(), 10000.0 / moveable.getSpeed());
        }
        q.move();
        for (Moveable moveable : q.getQueue()) {
            assertEquals(moveable.getTime(), (10000.0 - moveable.getLength()) / moveable.getSpeed());
            assertEquals(moveable.getLength(), (10000.0 / q.getQueue().getFirst().getSpeed()) * moveable.getSpeed());
        }
        q.setTopZero();
        for (Moveable moveable : q.getQueue()) {
            assertEquals(moveable.getTime(), (10000.0 - moveable.getLength()) / moveable.getSpeed());
        }
    }

    @Test
    public void testMove() {

    }
}
