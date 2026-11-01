package com.laosun.aluminium.test.content.characters.gepard;

import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 1104 杰帕德's FLAT trace bonuses (冰伤 3.2/3.2/4.8/4.8/6.4%、效果抵抗 4/6/8%、防御 5/7.5%) need no rule: the character
 * factory applies the data's attribute rows. This judge pins that so the old "未写" registration cannot come back.
 */
public class GepardFlatTracesTest {

    private static final int GEPARD = 1104;
    private static final int LEVEL = 80;

    @Test
    public void theFlatTraceBonusesAreAlreadyOnThePanel() {
        Character gepard = CharacterFactory.create(GEPARD, LEVEL);
        Assertions.assertEquals(0.224, gepard.getAttribute(AttributeType.ICE_DAMAGE_BOOST).get(), 1e-9,
                "冰伤行迹 3.2+3.2+4.8+4.8+6.4 = 22.4%");
        Assertions.assertEquals(0.18, gepard.getAttribute(AttributeType.EFFECT_RESISTANCE).get(), 1e-9,
                "效果抵抗行迹 4+6+8 = 18%");
        Assertions.assertEquals(1.125, gepard.getAttribute(AttributeType.DEFENCE).get()
                / gepard.getAttribute(AttributeType.DEFENCE).baseValue(), 1e-9, "防御行迹 5+7.5 = 12.5%");
    }
}