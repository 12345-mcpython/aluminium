package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/** Light cone 23051, from the shipped file (discipline 232). */
public class LightCone23051Test {
    private static final int WEAPON_ID = 23051;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;

    @Test
    public void theShippedRuleCarriesItsThreeEffects() {
        Character c = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL));
        var rules = c.getTriggerTable().rulesFor(TriggerEvent.CAST_SETUP).stream()
                .filter(r -> r.id().startsWith("cone23051_")).toList();
        Assertions.assertEquals(1, rules.size(), "one ultimate rule");
        var effects = rules.getFirst().effects();
        System.out.println("[23051] effects=" + effects.size()
                + " ops=" + effects.stream().map(e -> e.getOp() + "@" + e.getTarget()).toList());
        Assertions.assertEquals(3, effects.size(), "two heals and the damage boost");
        var boost = effects.stream().filter(e -> "MODIFY_ATTR".equals(e.getOp())).findFirst().orElseThrow();
        System.out.println("[23051] spec boost percent=" + boost.getPercent() + " turns=" + boost.getTurns()
                + " target=" + boost.getTarget());
        Assertions.assertEquals(0.48, boost.getPercent(), 1e-9, "48% damage");
        Assertions.assertEquals(3.0, boost.getTurns(), 1e-9, "for three turns");
        Assertions.assertEquals("all_allies", boost.getTarget(), "for the party");
    }
}
