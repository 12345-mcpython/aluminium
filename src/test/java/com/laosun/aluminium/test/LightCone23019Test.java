package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Light cone 23019, read off the shipped file (discipline 232). */
public class LightCone23019Test {
    private static final int WEAPON_ID = 23019;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;

    private Character wearer() {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL));
    }

    @Test
    public void theShippedRulesCarryTheNumbers() {
        Character c = wearer();
        var casts = c.getTriggerTable().rulesFor(TriggerEvent.CAST_SETUP).stream()
                .filter(r -> r.id().startsWith("cone23019_")).toList();
        var waves = c.getTriggerTable().rulesFor(TriggerEvent.WAVE_START).stream()
                .filter(r -> r.id().startsWith("cone23019_")).toList();
        System.out.println("[23019] cast rules=" + casts.size() + " wave rules=" + waves.size());
        Assertions.assertEquals(2, casts.size(), "the ultimate's damage and its skill point");
        Assertions.assertEquals(1, waves.size(), "the wave-start energy");
        var dmg = casts.stream().filter(r -> r.id().endsWith("ult_damage")).findFirst().orElseThrow();
        var sp = casts.stream().filter(r -> r.id().endsWith("ult_skill_point")).findFirst().orElseThrow();
        var energy = waves.getFirst();
        System.out.println("[23019] spec damage percent=" + dmg.effects().getFirst().getPercent()
                + " turns=" + dmg.effects().getFirst().getTurns()
                + " target=" + dmg.effects().getFirst().getTarget()
                + " ; sp op=" + sp.effects().getFirst().getOp() + " amount=" + sp.effects().getFirst().getAmount()
                + " ; energy op=" + energy.effects().getFirst().getOp()
                + " amount=" + energy.effects().getFirst().getAmount()
                + " target=" + energy.effects().getFirst().getTarget());
        Assertions.assertEquals(0.4, dmg.effects().getFirst().getPercent(), 1e-9, "40% damage");
        Assertions.assertEquals(3.0, dmg.effects().getFirst().getTurns(), 1e-9, "for three turns");
        Assertions.assertEquals("all_allies", dmg.effects().getFirst().getTarget(), "for the party");
        Assertions.assertEquals(1.0, sp.effects().getFirst().getAmount(), 1e-9, "one skill point");
        Assertions.assertEquals("self", sp.effects().getFirst().getTarget(), "for the wearer");
        Assertions.assertEquals("GAIN_SKILL_POINT", sp.effects().getFirst().getOp(), "a skill point");
        Assertions.assertEquals(20.0, energy.effects().getFirst().getAmount(), 1e-9, "twenty energy");
        Assertions.assertEquals("all_allies", energy.effects().getFirst().getTarget(), "for the party");
        Assertions.assertEquals("GAIN_ENERGY", energy.effects().getFirst().getOp(), "energy");
    }
}
