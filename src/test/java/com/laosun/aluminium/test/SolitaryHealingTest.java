package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Light cone 24003, from the shipped file (discipline 232). */
public class SolitaryHealingTest {
    private static final int WEAPON_ID = 24003;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;

    @Test
    public void theShippedClauseCarriesTheNumbers() {
        Character c = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL));
        var rules = c.getTriggerTable().rulesFor(TriggerEvent.CAST_SETUP).stream()
                .filter(r -> r.id().startsWith("cone24003_")).toList();
        Assertions.assertEquals(1, rules.size(), "one ultimate rule");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[24003] spec attribute=" + effect.getAttribute() + " percent=" + effect.getPercent()
                + " turns=" + effect.getTurns() + " target=" + effect.getTarget());
        Assertions.assertEquals("DOT_DAMAGE_BOOST", effect.getAttribute(), "the DOT boost");
        Assertions.assertEquals(0.48, effect.getPercent(), 1e-9, "48%");
        Assertions.assertEquals(2.0, effect.getTurns(), 1e-9, "for two turns");
        Assertions.assertEquals("self", effect.getTarget(), "on the wearer");
    }
}
