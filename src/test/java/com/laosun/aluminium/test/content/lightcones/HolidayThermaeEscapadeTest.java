package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Light cone 21061, from the shipped file (discipline 232).
 */
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
        // 2026-10-19: one rule, not two - the damage share is the cone's own property, so its duplicated rule was removed.
        Assertions.assertEquals(1, rules.size(), "only the vulnerability is a rule now");
        // 2026-10-19: only the vulnerability rule is left; the damage share is the cone's own property (pinned above).
        var hit = rules.stream().filter(r -> r.id().endsWith("vulnerability")).findFirst().orElseThrow();
        System.out.println("[21061] vulnerability percent=" + hit.effects().getFirst().getPercent()
                + " turns=" + hit.effects().getFirst().getTurns());
        Assertions.assertEquals(0.1, hit.effects().getFirst().getPercent(), 1e-9, "10% vulnerability at rank 1");
        Assertions.assertEquals(2, hit.effects().getFirst().getTurns(), "for two turns");
        Assertions.assertEquals("target", hit.effects().getFirst().getTarget(), "on the enemy it hit");
    }
}
