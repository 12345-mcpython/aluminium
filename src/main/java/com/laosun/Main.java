package com.laosun;

import com.laosun.aluminium.Queue;
import com.laosun.aluminium.models.Character;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Character c1 = new Character("test 1", 100, 100, 100, 100, 100);
        Character c2 = new Character("test 2", 200, 200, 200, 100, 150);
        Queue q = new Queue();
        q.add(List.of(c1, c2));
        q.initialize();
        System.out.println(q);
        System.out.println(c1);
        System.out.println(c2);
    }
}