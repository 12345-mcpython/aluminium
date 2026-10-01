package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Light cone 23029, its expressible clause, read off the shipped file (discipline 232). */
public class LightCone23029Test {
    private static final int WEAPON_ID = 23029;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;

    @Test
    public void theShippedClauseCarriesTheNumbers() {
        Character c = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL));
        var rules = c.getTriggerTable().rulesFor(TriggerEvent.DEALING_DAMAGE).stream()
                .filter(r -> r.id().startsWith("cone23029_")).toList();
        System.out.println("[23029] rules=" + rules.size());
        Assertions.assertEquals(1, rules.size(), "the armour-break clause");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[23029] spec op=" + effect.getOp() + " percent=" + effect.getPercent()
                + " turns=" + effect.getTurns() + " target=" + effect.getTarget());
        Assertions.assertEquals(0.18, effect.getPercent(), 1e-9, "18% more damage taken");
        Assertions.assertEquals(2.0, effect.getTurns(), 1e-9, "for two turns");
        Assertions.assertEquals("target", effect.getTarget(), "on the enemy it hit");
    }
}
