package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.entity.Character;
import com.laosun.aluminium.models.equipment.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Light cone 23029, its expressible clause, read off the shipped file (discipline 232).
 */
public class ThoseManySpringsTest {
    private static final int WEAPON_ID = 23029;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;

    @Test
    public void theShippedClauseCarriesTheNumbers() {
        Character c = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL));
        // 2026-10-19: the armour-break clause is a BASIC_ATTACK rule now (it carries the 60% chance and the state).
        var rules = c.getTriggerTable().rulesFor(TriggerEvent.BASIC_ATTACK).stream()
                .filter(r -> r.id().startsWith("cone23029_")).toList();
        System.out.println("[23029] rules=" + rules.size());
        Assertions.assertEquals(1, rules.size(), "the armour-break clause");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[23029] spec op=" + effect.getOp() + " percent=" + effect.getPercent()
                + " turns=" + effect.getTurns() + " target=" + effect.getTarget());
        // 2026-10-19: the rule now carries the state and the chance; the vulnerability share is the SECOND effect, and it
        // is the #3 value (10% at rank 1) - the old 0.18 was #6, the wrong number.
        var hit = rules.getFirst().effects().get(1);
        Assertions.assertEquals(0.1, hit.getPercent(), 1e-9, "10% more damage taken at rank 1");
        Assertions.assertEquals(2, hit.getTurns(), 1e-9, "for two turns");
        Assertions.assertEquals(0.6, hit.getBaseChance(), 1e-9, "a 60% base chance");
        Assertions.assertEquals("target", hit.getTarget(), "on the enemy it hit");
    }
}
