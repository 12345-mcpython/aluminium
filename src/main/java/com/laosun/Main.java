package com.laosun;

import com.laosun.aluminium.BattleDemo;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.utils.LevelPromotionCalc;

public class Main {
    static void main() {
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

        BattleDemo.main();

        System.out.println(System.getProperty("file.encoding"));
    }
}