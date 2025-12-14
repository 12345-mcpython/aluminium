package com.laosun;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.Queue;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.utils.LevelPromotionCalc;

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
        Summon summon = new Summon("test summon 1", 100, 100, 100, 132);
        Queue q = new Queue(List.of(c1, c2, c3, c4, c5), List.of(c6, c7, c8, c9, c10));
        q.add(List.of(summon));
        Battle battle = new Battle(q);
        battle.startBattle();
        battle.getQueue().print();
        System.out.println(Relic.createRandomLevelZero(Relic.Type.BODY, 5));
        String hyaBodyJson = "{ \"main_attribute\": { \"crit_attack\": 15 }, \"sub_attributes\": { \"health_percent\": { \"promote_level\": 3, \"attribute_level\": 5 }, \"speed\": { \"promote_level\": 0, \"attribute_level\": 1 }, \"effect_hit_rate\": { \"promote_level\": 1, \"attribute_level\": 2 }, \"breaking_effect\": { \"promote_level\": 0, \"attribute_level\": 0 } } }";
        String hyaLineJson = "{ \"main_attribute\": { \"energy_regeneration_rate\": 15 }, \"sub_attributes\": { \"defence\": { \"promote_level\": 1, \"attribute_level\": 0 }, \"attack_percent\": { \"promote_level\": 1, \"attribute_level\": 2 }, \"defence_percent\": { \"promote_level\": 1, \"attribute_level\": 2 }, \"effect_resistance\": { \"promote_level\": 1, \"attribute_level\": 0 } } }";
        String hyaBallJson = "{ \"main_attribute\": { \"health_percent\": 15 }, \"sub_attributes\": { \"health\": { \"promote_level\": 0, \"attribute_level\": 0 }, \"defence\": { \"promote_level\": 2, \"attribute_level\": 2 }, \"crit_chance\": { \"promote_level\": 1, \"attribute_level\": 2 }, \"crit_attack\": { \"promote_level\": 2, \"attribute_level\": 4 } } }";
        String hyaBootJson = "{ \"main_attribute\": { \"speed\": 15 }, \"sub_attributes\": { \"attack\": { \"promote_level\": 0, \"attribute_level\": 1 }, \"attack_percent\": { \"promote_level\": 1, \"attribute_level\": 2 }, \"effect_resistance\": { \"promote_level\": 1, \"attribute_level\": 1 }, \"breaking_effect\": { \"promote_level\": 2, \"attribute_level\": 1 } } }";
        String hyaHandJson = "{ \"main_attribute\": { \"attack\": 15 }, \"sub_attributes\": { \"defence\": { \"promote_level\": 1, \"attribute_level\": 1 }, \"health_percent\": { \"promote_level\": 2, \"attribute_level\": 0 }, \"defence_percent\": { \"promote_level\": 0, \"attribute_level\": 2 }, \"speed\": { \"promote_level\": 1, \"attribute_level\": 1 } } }";
        String hyaHeadJson = "{ \"main_attribute\": { \"health\": 15 }, \"sub_attributes\": { \"attack\": { \"promote_level\": 0, \"attribute_level\": 0 }, \"health_percent\": { \"promote_level\": 1, \"attribute_level\": 3 }, \"defence_percent\": { \"promote_level\": 1, \"attribute_level\": 0 }, \"speed\": { \"promote_level\": 2, \"attribute_level\": 2 } } }";
        Relic hyaBody = Relic.createBySetting(Relic.Type.BODY, 5, 15, hyaBodyJson);
        System.out.println(hyaBody);
        Relic hyaLine = Relic.createBySetting(Relic.Type.LINE, 5, 15, hyaLineJson);
        System.out.println(hyaLine);
        Relic hyaBall = Relic.createBySetting(Relic.Type.BALL, 5, 15, hyaBallJson);
        System.out.println(hyaBall);
        Relic hyaBoot = Relic.createBySetting(Relic.Type.BOOT, 5, 15, hyaBootJson);
        System.out.println(hyaBoot);
        Relic hyaHand = Relic.createBySetting(Relic.Type.HAND, 5, 15, hyaHandJson);
        System.out.println(hyaHand);
        Relic hyaHead = Relic.createBySetting(Relic.Type.HEAD, 5, 15, hyaHeadJson);
        System.out.println(hyaHead);
        RelicSuit hya = new RelicSuit();
        hya.addMore(hyaBody, hyaLine, hyaBall, hyaBoot, hyaHand, hyaHead);
        System.out.println(hya.calcTotalValue());
        System.out.println(Constant.WEAPONS.get(23042).name().english());
        System.out.println(Constant.CHARACTERS.get(1409).name().english());

        System.out.println(LevelPromotionCalc.calcCharacterRate(80));
        System.out.println(LevelPromotionCalc.calcAttackerRate(80));
        System.out.println(Constant.CHARACTERS.get(1409).health());
        System.out.println(Constant.WEAPONS.get(23042).health());
    }
}