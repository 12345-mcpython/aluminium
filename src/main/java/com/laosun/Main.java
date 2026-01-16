package com.laosun;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.utils.LevelPromotionCalc;

import java.nio.charset.Charset;

public class Main {
    static void main() {
        IO.println(Relic.createRandomLevelZero(Relic.Type.BODY, 5));
        String hyaBodyJson = "{ \"main_attribute\": { \"outgoing_healing_boost\": 15 }, \"sub_attributes\": { \"health_percent\": { \"promote_level\": 1, \"attribute_level\": 3 }, \"defence_percent\": { \"promote_level\": 0, \"attribute_level\": 0 }, \"speed\": { \"promote_level\": 0, \"attribute_level\": 1 }, \"crit_attack\": { \"promote_level\": 3, \"attribute_level\": 4 } } }";
        String hyaLineJson = "{ \"main_attribute\": { \"energy_regeneration_rate\": 15 }, \"sub_attributes\": { \"defence\": { \"promote_level\": 1, \"attribute_level\": 1 }, \"health_percent\": { \"promote_level\": 1, \"attribute_level\": 3 }, \"attack_percent\": { \"promote_level\": 1, \"attribute_level\": 1 }, \"speed\": { \"promote_level\": 2, \"attribute_level\": 2 } } }";
        String hyaBallJson = "{ \"main_attribute\": { \"health_percent\": 15 }, \"sub_attributes\": { \"defence\": { \"promote_level\": 0, \"attribute_level\": 2 }, \"speed\": { \"promote_level\": 3, \"attribute_level\": 5 }, \"crit_chance\": { \"promote_level\": 2, \"attribute_level\": 3 }, \"effect_resistance\": { \"promote_level\": 0, \"attribute_level\": 2 } } }";
        String hyaBootJson = "{ \"main_attribute\": { \"speed\": 15 }, \"sub_attributes\": { \"health_percent\": { \"promote_level\": 3, \"attribute_level\": 4 }, \"defence_percent\": { \"promote_level\": 0, \"attribute_level\": 2 }, \"crit_attack\": { \"promote_level\": 1, \"attribute_level\": 1 }, \"effect_resistance\": { \"promote_level\": 0, \"attribute_level\": 0 } } }";
        String hyaHandJson = "{ \"main_attribute\": { \"attack\": 15 }, \"sub_attributes\": { \"defence\": { \"promote_level\": 0, \"attribute_level\": 1 }, \"health_percent\": { \"promote_level\": 1, \"attribute_level\": 3 }, \"speed\": { \"promote_level\": 3, \"attribute_level\": 3 }, \"effect_resistance\": { \"promote_level\": 0, \"attribute_level\": 0 } } }\n";
        String hyaHeadJson = "{ \"main_attribute\": { \"health\": 15 }, \"sub_attributes\": { \"health_percent\": { \"promote_level\": 1, \"attribute_level\": 1 }, \"speed\": { \"promote_level\": 3, \"attribute_level\": 7 }, \"crit_chance\": { \"promote_level\": 0, \"attribute_level\": 1 }, \"effect_hit_rate\": { \"promote_level\": 1, \"attribute_level\": 2 } } }";
        Relic hyaBody = Relic.createBySetting(Relic.Type.BODY, 5, 15, hyaBodyJson);
        IO.println(hyaBody);
        Relic hyaLine = Relic.createBySetting(Relic.Type.LINE, 5, 15, hyaLineJson);
        IO.println(hyaLine);
        Relic hyaBall = Relic.createBySetting(Relic.Type.BALL, 5, 15, hyaBallJson);
        IO.println(hyaBall);
        Relic hyaBoot = Relic.createBySetting(Relic.Type.BOOT, 5, 15, hyaBootJson);
        IO.println(hyaBoot);
        Relic hyaHand = Relic.createBySetting(Relic.Type.HAND, 5, 15, hyaHandJson);
        IO.println(hyaHand);
        Relic hyaHead = Relic.createBySetting(Relic.Type.HEAD, 5, 15, hyaHeadJson);
        IO.println(hyaHead);
        RelicSuit hya = new RelicSuit();
        hya.addMore(hyaBody, hyaLine, hyaBall, hyaBoot, hyaHand, hyaHead);
        IO.println(hya.calcTotalValue());
        IO.println(Constant.WEAPONS.get(23042).name().english());
        IO.println(Constant.CHARACTERS.get(1409).name().english());

        IO.println(LevelPromotionCalc.calcCharacterRate(80));
        IO.println(LevelPromotionCalc.calcWeaponRate(80));
        IO.println(Constant.CHARACTERS.get(1409).health());
        IO.println(Constant.WEAPONS.get(23042).health());
        Weapon wp = Weapon.build(23042, 80);
        IO.println(wp);
        Character character = Character.build(1409, 80, false, hya, wp, null,
                new ExtraBasicPromote(0, 0, 0, 14, 0.1, 0, 0, 0.3));
        IO.println(character);
        // IO.println(Character.build(1003, 20, true, hya, null));
        // TEST CALC
        // In game
        // HP ATK DEF SPD
        // 5223 1280 1348 219
        // CALC LOGIC
        // Base HP: 147.84 (lv1 character base value) * 7.35 (lv80 promoting rate + 52.8 (lv1 light cone base value) * 22.05 (lv80 LC promoting value) = 2250.864
        // Add skill: 10% promotion
        // Add relic: 705.56 value + 90.717% promotion (see below result)
        // formula: (147.84 * 7.35 + 52.8 * 22.05) * (1 + 0.90717 + 0.10) + 705.56
        // Equal to: 5223.42669488 equal to 5223

        // BASE SPD: 110 (character base data)
        // Add relic: 62.432 6% + 6%
        // Add skill: 4 + 3 + 3 + 2 + 2 = 14
        // Add light cone: 18%
        // formula: 110 * (1 + 0.06 + 0.06 + 0.18) + 62.432 + 14 = 219.432 equal to 219

        // BattleDemo.main();

        IO.println(Charset.defaultCharset().displayName());
    }
}