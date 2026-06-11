package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.models.*;
import com.laosun.aluminium.models.Character;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CharacterTest {
    @Test
    public void testAttributeValue() {
        Relic hyaBody = Relic.builder()
                .type(RelicType.BODY)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.OUTGOING_HEALING_BOOST)
                .subAttribute(AttributeType.HEALTH_PERCENT, 1, 3)
                .subAttribute(AttributeType.DEFENCE_PERCENT, 0, 0)
                .subAttribute(AttributeType.SPEED, 1, 1)
                .subAttribute(AttributeType.CRIT_ATTACK, 2, 5)
                .build();

        Relic hyaLine = Relic.builder()
                .type(RelicType.LINE)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.ENERGY_REGENERATION_RATE)
                .subAttribute(AttributeType.HEALTH_PERCENT, 3, 3)
                .subAttribute(AttributeType.SPEED, 1, 4)
                .subAttribute(AttributeType.EFFECT_RESISTANCE, 1, 3)
                .subAttribute(AttributeType.BREAKING_EFFECT, 0, 2)
                .build();

        Relic hyaBall = Relic.builder()
                .type(RelicType.BALL)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.HEALTH_PERCENT)
                .subAttribute(AttributeType.DEFENCE, 0, 2)
                .subAttribute(AttributeType.SPEED, 3, 5)
                .subAttribute(AttributeType.CRIT_CHANCE, 2, 3)
                .subAttribute(AttributeType.EFFECT_RESISTANCE, 0, 2)
                .build();

        Relic hyaBoot = Relic.builder()
                .type(RelicType.BOOT)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.SPEED)
                .subAttribute(AttributeType.HEALTH_PERCENT, 3, 4)
                .subAttribute(AttributeType.DEFENCE_PERCENT, 0, 2)
                .subAttribute(AttributeType.CRIT_ATTACK, 1, 1)
                .subAttribute(AttributeType.EFFECT_RESISTANCE, 0, 0)
                .build();

        Relic hyaHand = Relic.builder()
                .type(RelicType.HAND)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.ATTACK)
                .subAttribute(AttributeType.DEFENCE, 0, 1)
                .subAttribute(AttributeType.HEALTH_PERCENT, 1, 3)
                .subAttribute(AttributeType.SPEED, 3, 3)
                .subAttribute(AttributeType.EFFECT_RESISTANCE, 0, 0)
                .build();

        Relic hyaHead = Relic.builder()
                .type(RelicType.HEAD)
                .star(5)
                .level(15)
                .mainAttribute(AttributeType.HEALTH)
                .subAttribute(AttributeType.HEALTH_PERCENT, 1, 1)
                .subAttribute(AttributeType.SPEED, 3, 7)
                .subAttribute(AttributeType.CRIT_CHANCE, 0, 1)
                .subAttribute(AttributeType.EFFECT_HIT_RATE, 1, 2)
                .build();
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
