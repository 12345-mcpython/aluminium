package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Light cone 23041, read off the shipped file (discipline 232). */
public class LightCone23041Test {
    private static final int WEAPON_ID = 23041;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;

    @Test
    public void theShippedRulesCarryTheNumbers() {
        Character c = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL));
        var turns = c.getTriggerTable().rulesFor(TriggerEvent.TURN_START).stream()
                .filter(r -> r.id().startsWith("cone23041_")).toList();
        var hits = c.getTriggerTable().rulesFor(TriggerEvent.DEALING_DAMAGE).stream()
                .filter(r -> r.id().startsWith("cone23041_")).toList();
        System.out.println("[23041] turn rules=" + turns.size() + " hit rules=" + hits.size());
        Assertions.assertEquals(1, turns.size(), "the turn-start energy");
        Assertions.assertEquals(1, hits.size(), "the defence-down clause");
        var energy = turns.getFirst().effects().getFirst();
        var down = hits.getFirst().effects().getFirst();
        System.out.println("[23041] spec energy op=" + energy.getOp() + " amount=" + energy.getAmount()
                + " target=" + energy.getTarget()
                + " ; def attr=" + down.getAttribute() + " percent=" + down.getPercent()
                + " turns=" + down.getTurns() + " target=" + down.getTarget());
        Assertions.assertEquals(10.0, energy.getAmount(), 1e-9, "ten energy");
        Assertions.assertEquals(-0.24, down.getPercent(), 1e-9, "24% less defence");
        Assertions.assertEquals(2.0, down.getTurns(), 1e-9, "for two turns");
        Assertions.assertEquals("target", down.getTarget(), "on the enemy it hit");
    }
}
