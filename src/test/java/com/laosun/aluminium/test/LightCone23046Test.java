package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Light cone 23046, from the shipped file (discipline 232). */
public class LightCone23046Test {
    private static final int WEAPON_ID = 23046;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;

    @Test
    public void theShippedRuleCarriesItsStacking() {
        Character c = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL));
        var rules = c.getTriggerTable().rulesFor(TriggerEvent.CAST_SETUP).stream()
                .filter(r -> r.id().startsWith("cone23046_")).toList();
        Assertions.assertEquals(1, rules.size(), "one skill rule");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[23046] spec attribute=" + effect.getAttribute() + " percent=" + effect.getPercent()
                 + " target=" + effect.getTarget());
        Assertions.assertEquals("ATTACK", effect.getAttribute(), "attack");
        Assertions.assertEquals(0.2, effect.getPercent(), 1e-9, "20% per cast");
        Assertions.assertEquals("self", effect.getTarget(), "on the wearer");
    }
}
