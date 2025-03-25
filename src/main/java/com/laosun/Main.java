package com.laosun;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.Queue;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.Moveable;
import com.laosun.aluminium.models.Relic;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        Character c1 = new Character("test 1", 100, 100, 100, 110);
        Character c2 = new Character("test 2", 200, 200, 100, 150);
        Character c3 = new Character("test 3", 100, 100, 100, 130);
        Character c4 = new Character("test 4", 200, 200, 100, 140);
        Character c5 = new Character("test 5", 100, 100, 100, 120);
        Enemy c6 = new Enemy("test 1", 200, 200, 100, 150);
        Enemy c7 = new Enemy("test 2", 100, 100, 100, 115);
        Enemy c8 = new Enemy("test 3", 200, 200, 100, 135);
        Enemy c9 = new Enemy("test 4", 100, 100, 100, 132);
        Enemy c10 = new Enemy("test 5", 200, 200, 100, 143) {
            @Override
            public void onBattleStart(Battle battle, Moveable moveable) {
                System.out.println("Battle start");
                moveable.move(2000);
            }
        };
        Queue q = new Queue(List.of(c1, c2, c3, c4, c5), List.of(c6, c7, c8, c9, c10));
        Battle battle = new Battle(q);
        battle.startBattle();
        battle.getQueue().print();
        System.out.println(Relic.createRandomLevelZero(Relic.Type.HAND, 5));
    }
}