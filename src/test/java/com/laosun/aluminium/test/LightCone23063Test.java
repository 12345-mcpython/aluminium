package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Light cone 23063, from the shipped file (discipline 232). */
public class LightCone23063Test {
    private static final int WEAPON_ID = 23063;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;

    @Test
    public void theShippedRulesCarryTheNumbers() {
        Character c = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL));
        var casts = c.getTriggerTable().rulesFor(TriggerEvent.CAST_SETUP).stream()
                .filter(r -> r.id().startsWith("cone23063_")).toList();
        var starts = c.getTriggerTable().rulesFor(TriggerEvent.BATTLE_START).stream()
                .filter(r -> r.id().startsWith("cone23063_")).toList();
        System.out.println("[23063] cast rules=" + casts.size() + " battle rules=" + starts.size());
        Assertions.assertEquals(1, casts.size(), "the ultimate's skill point");
        Assertions.assertEquals(1, starts.size(), "the battle-start rule");
        var sp = casts.getFirst().effects().getFirst();
        var adv = starts.getFirst().effects().getFirst();
        System.out.println("[23063] spec sp op=" + sp.getOp() + " amount=" + sp.getAmount()
                + " ; advance op=" + adv.getOp() + " percent=" + adv.getPercent() + " target=" + adv.getTarget());
        Assertions.assertEquals(1.0, sp.getAmount(), 1e-9, "one skill point");
        Assertions.assertEquals(0.4, adv.getPercent(), 1e-9, "40% action advance");
        Assertions.assertEquals("self", adv.getTarget(), "for the wearer");
        var effects = starts.getFirst().effects();
        System.out.println("[23063] battle effects=" + effects.stream()
                .map(e -> e.getOp() + "@" + e.getTarget()).toList());
        Assertions.assertEquals(3, effects.size(), "advance, the named state, and the speed boost");
        var state = effects.stream().filter(e -> "APPLY_BUFF".equals(e.getOp()))
                .findFirst().orElseThrow();
        System.out.println("[23063] the state is " + state.getBuff());
        Assertions.assertEquals("\u65b0\u58f0", state.getBuff(), "the state name");
        var speed = effects.stream().filter(e -> "MODIFY_ATTR".equals(e.getOp()))
                .findFirst().orElseThrow();
        System.out.println("[23063] speed percent=" + speed.getPercent() + " target=" + speed.getTarget());
        Assertions.assertEquals(0.4, speed.getPercent(), 1e-9, "40% speed");
        Assertions.assertEquals("all_allies", speed.getTarget(), "for the party");
    }
}
