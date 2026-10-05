package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Light cone 21061, from the shipped file (discipline 232). */
public class HolidayThermaeEscapadeTest {
    private static final int WEAPON_ID = 21061;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;

    private Character wearer(boolean withCone) {
        Weapon weapon = Weapon.build(WEAPON_ID, LEVEL);
        return withCone ? CharacterFactory.create(WEARER, LEVEL, true, weapon)
                : CharacterFactory.create(WEARER, LEVEL);
    }

    /**
     * Note: The +32% lives on `DEALING_DAMAGE`, i.e. it applies INSIDE a damage instance (measured: reading the attribute outside
     * one shows only the cone stat line, 0.16). So the sentence is pinned by the spec half below, which reads the shipped file.
     */
    @Test
    public void theConeStatLineIsTheOnlyThingVisibleOutsideADamageInstance() {
        double plain = wearer(false).getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        double withCone = wearer(true).getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[21061] ALL_DAMAGE_TYPE_BOOST " + plain + " -> " + withCone
                + " (the 32% is instance-scoped, so it is not visible here)");
        Assertions.assertEquals(plain + 0.16, withCone, 1e-9, "the cone stat line");
    }

    @Test
    public void theShippedRulesCarryTheNumbers() {
        Character c = wearer(true);
        var rules = c.getTriggerTable().rulesFor(TriggerEvent.DEALING_DAMAGE).stream()
                .filter(r -> r.id().startsWith("cone21061_")).toList();
        System.out.println("[21061] rules=" + rules.size());
        Assertions.assertEquals(2, rules.size(), "the damage sentence and the vulnerability");
        var boost = rules.stream().filter(r -> r.id().endsWith("damage_up")).findFirst().orElseThrow();
        var hit = rules.stream().filter(r -> r.id().endsWith("vulnerability")).findFirst().orElseThrow();
        System.out.println("[21061] spec damage_up percent=" + boost.effects().getFirst().getPercent()
                + " attribute=" + boost.effects().getFirst().getAttribute()
                + " ; vulnerability percent=" + hit.effects().getFirst().getPercent()
                + " turns=" + hit.effects().getFirst().getTurns());
        Assertions.assertEquals(0.32, boost.effects().getFirst().getPercent(), 1e-9, "32% damage");
        Assertions.assertEquals("self", boost.effects().getFirst().getTarget(), "on the wearer");
        Assertions.assertEquals(0.16, hit.effects().getFirst().getPercent(), 1e-9, "16% more damage taken");
        Assertions.assertEquals(2.0, hit.effects().getFirst().getTurns(), 1e-9, "two turns");
        Assertions.assertEquals("target", hit.effects().getFirst().getTarget(), "on the enemy it hit");
    }
}
