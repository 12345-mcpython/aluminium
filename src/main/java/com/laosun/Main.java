package com.laosun;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.utils.LevelPromotionCalc;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;

import java.nio.charset.Charset;

public class Main {
    static void main() {
        IO.println(Relic.createRandomLevelZero(Relic.Type.BODY, 5));
        String hyaBodyJson = "{ \"main_attribute\": { \"outgoing_healing_boost\": 15 }, \"sub_attributes\": { \"health_percent\": { \"promote_level\": 1, \"attribute_level\": 3 }, \"defence_percent\": { \"promote_level\": 0, \"attribute_level\": 0 }, \"speed\": { \"promote_level\": 1, \"attribute_level\": 1 }, \"crit_attack\": { \"promote_level\": 2, \"attribute_level\": 5 } } }";
        String hyaLineJson = "{ \"main_attribute\": { \"energy_regeneration_rate\": 15 }, \"sub_attributes\": { \"health_percent\": { \"promote_level\": 3, \"attribute_level\": 3 }, \"speed\": { \"promote_level\": 1, \"attribute_level\": 4 }, \"effect_resistance\": { \"promote_level\": 1, \"attribute_level\": 3 }, \"breaking_effect\": { \"promote_level\": 0, \"attribute_level\": 2 } } }";
        String hyaBallJson = "{ \"main_attribute\": { \"health_percent\": 15 }, \"sub_attributes\": { \"defence\": { \"promote_level\": 0, \"attribute_level\": 2 }, \"speed\": { \"promote_level\": 3, \"attribute_level\": 5 }, \"crit_chance\": { \"promote_level\": 2, \"attribute_level\": 3 }, \"effect_resistance\": { \"promote_level\": 0, \"attribute_level\": 2 } } }";
        String hyaBootJson = "{ \"main_attribute\": { \"speed\": 15 }, \"sub_attributes\": { \"health_percent\": { \"promote_level\": 3, \"attribute_level\": 4 }, \"defence_percent\": { \"promote_level\": 0, \"attribute_level\": 2 }, \"crit_attack\": { \"promote_level\": 1, \"attribute_level\": 1 }, \"effect_resistance\": { \"promote_level\": 0, \"attribute_level\": 0 } } }";
        String hyaHandJson = "{ \"main_attribute\": { \"attack\": 15 }, \"sub_attributes\": { \"defence\": { \"promote_level\": 0, \"attribute_level\": 1 }, \"health_percent\": { \"promote_level\": 1, \"attribute_level\": 3 }, \"speed\": { \"promote_level\": 3, \"attribute_level\": 3 }, \"effect_resistance\": { \"promote_level\": 0, \"attribute_level\": 0 } } }";
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
        Object2DoubleOpenHashMap<String> relicValue = new Object2DoubleOpenHashMap<>();
        hya.calcTotalValue(relicValue);
        IO.println(relicValue);
        IO.println(Constant.WEAPONS.get(23042).name().english());
        IO.println(Constant.CHARACTERS.get(1409).name().english());

        IO.println(LevelPromotionCalc.calcCharacterRate(80));
        IO.println(LevelPromotionCalc.calcWeaponRate(80));
        IO.println(Constant.CHARACTERS.get(1409).health());
        IO.println(Constant.WEAPONS.get(23042).health());
        IO.println(Constant.WEAPONS.get(23042).weaponSkillData().getFirst().abilityProperties());
        Weapon wp = Weapon.build(23042, 80);
        Character character = new Character.Builder()
                .cid(1409)
                .level(80)
                .relicSuit(hya)
                .weapon(wp)
                .extraValue(new ExtraBasicPromote(0, 0, 0, 14, 0.1, 0, 0, 0.3))
                .build();
        IO.println(character.getHealth());
        IO.println(character.getDefence());
        IO.println(character.getSpeed());
        DoubleValue dp = character.getHealth().clone();
        dp.addModifier(DoubleValue.Modifier.addPercent(0.20));
        IO.println(dp);
        IO.println(Charset.defaultCharset().displayName());
    }
}