package com.laosun;

import com.laosun.aluminium.Queue;
import com.laosun.aluminium.beans.Model;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Moveable;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Character c1 = new Character("test 1", 100, 100, 100, 100, 110);
        Character c2 = new Character("test 2", 200, 200, 200, 100, 150);
        Character c3 = new Character("test 3", 100, 100, 100, 100, 130);
        Character c4 = new Character("test 4", 200, 200, 200, 100, 140);
        Character c5 = new Character("test 5", 100, 100, 100, 100, 120);
        Character c6 = new Character("test 6", 200, 200, 200, 100, 150);
        Character c7 = new Character("test 7", 100, 100, 100, 100, 115);
        Character c8 = new Character("test 8", 200, 200, 200, 100, 135);
        Character c9 = new Character("test 9", 100, 100, 100, 100, 132);
        Character c10 = new Character("test 10", 200, 200, 200, 100, 143);
        Queue q = new Queue();
        q.add(List.of(c1, c2, c3, c4, c5, c6, c7, c8, c9, c10));
        System.out.println("Init");
        q.initialize();
        q.print();
        System.out.println("Start move");
        q.move();
        q.print();
        System.out.println("Set top zero");
        q.setTopZero();
        q.print();
        System.out.println("Start move");
        q.move();
        q.print();
        System.out.println("Set top zero");
        q.setTopZero();
        q.print();
        System.out.println("Start move");
        q.move();
        q.print();
        System.out.println("Set top zero");
        q.setTopZero();
        q.print();
        System.out.println("Start move");
        q.move();
        q.print();
        System.out.println("Set top zero");
        q.setTopZero();
        q.print();
        System.out.println("Start move");
        q.move();
        q.print();
        System.out.println("Set top zero");
        q.setTopZero();
        q.print();
        System.out.println("Start move");
        q.move();
        q.print();
//        System.out.println(q);
//        System.out.println(c1);
//        System.out.println(c2);
//        System.out.println(Model.TEST);
    }
}