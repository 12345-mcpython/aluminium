package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CharacterTest {
    @Test
    public void test() {
        String hyaBodyJson = "{ \"main_attribute\": { \"outgoing_healing_boost\": 15 }, \"sub_attributes\": { \"health_percent\": { \"promote_level\": 1, \"attribute_level\": 3 }, \"defence_percent\": { \"promote_level\": 0, \"attribute_level\": 0 }, \"speed\": { \"promote_level\": 1, \"attribute_level\": 1 }, \"crit_attack\": { \"promote_level\": 2, \"attribute_level\": 5 } } }";
        String hyaLineJson = "{ \"main_attribute\": { \"energy_regeneration_rate\": 15 }, \"sub_attributes\": { \"health_percent\": { \"promote_level\": 3, \"attribute_level\": 3 }, \"speed\": { \"promote_level\": 1, \"attribute_level\": 4 }, \"effect_resistance\": { \"promote_level\": 1, \"attribute_level\": 3 }, \"breaking_effect\": { \"promote_level\": 0, \"attribute_level\": 2 } } }";
        String hyaBallJson = "{ \"main_attribute\": { \"health_percent\": 15 }, \"sub_attributes\": { \"defence\": { \"promote_level\": 0, \"attribute_level\": 2 }, \"speed\": { \"promote_level\": 3, \"attribute_level\": 5 }, \"crit_chance\": { \"promote_level\": 2, \"attribute_level\": 3 }, \"effect_resistance\": { \"promote_level\": 0, \"attribute_level\": 2 } } }";
        String hyaBootJson = "{ \"main_attribute\": { \"speed\": 15 }, \"sub_attributes\": { \"health_percent\": { \"promote_level\": 3, \"attribute_level\": 4 }, \"defence_percent\": { \"promote_level\": 0, \"attribute_level\": 2 }, \"crit_attack\": { \"promote_level\": 1, \"attribute_level\": 1 }, \"effect_resistance\": { \"promote_level\": 0, \"attribute_level\": 0 } } }";
        String hyaHandJson = "{ \"main_attribute\": { \"attack\": 15 }, \"sub_attributes\": { \"defence\": { \"promote_level\": 0, \"attribute_level\": 1 }, \"health_percent\": { \"promote_level\": 1, \"attribute_level\": 3 }, \"speed\": { \"promote_level\": 3, \"attribute_level\": 3 }, \"effect_resistance\": { \"promote_level\": 0, \"attribute_level\": 0 } } }";
        String hyaHeadJson = "{ \"main_attribute\": { \"health\": 15 }, \"sub_attributes\": { \"health_percent\": { \"promote_level\": 1, \"attribute_level\": 1 }, \"speed\": { \"promote_level\": 3, \"attribute_level\": 7 }, \"crit_chance\": { \"promote_level\": 0, \"attribute_level\": 1 }, \"effect_hit_rate\": { \"promote_level\": 1, \"attribute_level\": 2 } } }";
        Relic hyaBody = Relic.createBySetting(Relic.Type.BODY, 5, 15, hyaBodyJson);
        Relic hyaLine = Relic.createBySetting(Relic.Type.LINE, 5, 15, hyaLineJson);
        Relic hyaBall = Relic.createBySetting(Relic.Type.BALL, 5, 15, hyaBallJson);
        Relic hyaBoot = Relic.createBySetting(Relic.Type.BOOT, 5, 15, hyaBootJson);
        Relic hyaHand = Relic.createBySetting(Relic.Type.HAND, 5, 15, hyaHandJson);
        Relic hyaHead = Relic.createBySetting(Relic.Type.HEAD, 5, 15, hyaHeadJson);
        RelicSuit hya = new RelicSuit();
        hya.addMore(hyaBody, hyaLine, hyaBall, hyaBoot, hyaHand, hyaHead);
        Weapon wp = Weapon.build(23042, 80);
        Character character = new Character.Builder()
                .cid(1409)
                .level(80)
                .relicSuit(hya)
                .weapon(wp)
                .extraValue(new ExtraBasicPromote(0, 0, 0, 14, 0.1, 0, 0, 0.12))
                .build();
        // Game Data
        Assertions.assertEquals(5379, character.getAttribute(AttributeType.HEALTH).get(), 1.0);
        Assertions.assertEquals(1312, character.getAttribute(AttributeType.DEFENCE).get(), 1.0);
        Assertions.assertEquals(1217, character.getAttribute(AttributeType.ATTACK).get(), 1.0);
        Assertions.assertEquals(220, character.getAttribute(AttributeType.SPEED).get(), 1.0);
    }
}
