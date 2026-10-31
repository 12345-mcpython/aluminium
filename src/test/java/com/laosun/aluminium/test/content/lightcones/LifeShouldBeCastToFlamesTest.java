package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Light cone 23041, read off the shipped file (discipline 232).
 */
public class LifeShouldBeCastToFlamesTest {
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
        Assertions.assertEquals(2, hits.size(), "the defence-down clause and the added-weakness boost");
        var energy = turns.getFirst().effects().getFirst();
        var down = hits.stream().filter(r -> r.id().equals("cone23041_defence_down")).findFirst().orElseThrow()
                .effects().getFirst();
        System.out.println("[23041] spec energy op=" + energy.getOp() + " amount=" + energy.getAmount()
                + " target=" + energy.getTarget()
                + " ; def attr=" + down.getAttribute() + " percent=" + down.getPercent()
                + " turns=" + down.getTurns() + " target=" + down.getTarget());
        Assertions.assertEquals(10.0, energy.getAmount(), 1e-9, "ten energy");
        Assertions.assertEquals("self", energy.getTarget(), "for the wearer");
        Assertions.assertEquals("GAIN_ENERGY", energy.getOp(), "energy");
        // 2026-10-31: the data row for rank 1 is 0.12 (12/15/18/21/24%), and the file used to carry the RANK-5 number.
        Assertions.assertEquals(-0.12, down.getPercent(), 1e-9, "12% less defence at rank 1");
        Assertions.assertEquals(2.0, down.getTurns(), 1e-9, "for two turns");
        Assertions.assertEquals("target", down.getTarget(), "on the enemy it hit");
    }
}
