package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Light cone 23051, from the shipped file (discipline 232).
 */
public class ThoughWorldsApartTest {
    private static final int WEAPON_ID = 23051;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;

    @Test
    public void theShippedRuleCarriesItsFourEffects() {
        Character c = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL));
        var rules = c.getTriggerTable().rulesFor(TriggerEvent.CAST_SETUP).stream()
                .filter(r -> r.id().startsWith("cone23051_")).toList();
        Assertions.assertEquals(1, rules.size(), "one ultimate rule");
        var effects = rules.getFirst().effects();
        System.out.println("[23051] effects=" + effects.size()
                + " ops=" + effects.stream().map(e -> e.getOp() + "@" + e.getTarget()).toList());
        Assertions.assertEquals(4, effects.size(),
                "two heals, the damage boost, and the extra share for a summon owner");
        // me: assert the SELECTORS too -- a mutation of lowest_hp_ally to self stayed green without this.
        var healTargets = effects.stream().filter(e -> "HEAL".equals(e.getOp()))
                .map(e -> e.getTarget()).toList();
        System.out.println("[23051] heal targets=" + healTargets);
        Assertions.assertEquals(java.util.List.of("all_allies", "lowest_hp_ally"), healTargets,
                "the party heal and the extra one for the lowest-HP ally");
        var boost = effects.stream().filter(e -> "MODIFY_ATTR".equals(e.getOp())).findFirst().orElseThrow();
        System.out.println("[23051] spec boost percent=" + boost.getPercent() + " turns=" + boost.getTurns()
                + " target=" + boost.getTarget());
        Assertions.assertEquals(0.24, boost.getPercent(), 1e-9, "48% damage");
        Assertions.assertEquals(3.0, boost.getTurns(), 1e-9, "for three turns");
        Assertions.assertEquals("all_allies", boost.getTarget(), "for the party");
        var extra = effects.stream().filter(e -> "MODIFY_ATTR".equals(e.getOp())).toList().get(1);
        System.out.println("[23051] extra boost percent=" + extra.getPercent() + " target=" + extra.getTarget());
        Assertions.assertEquals(0.12, extra.getPercent(), 1e-9, "「若目标拥有召唤物则额外提高 12%」");
        Assertions.assertEquals("allies_with_summons", extra.getTarget(), "only the allies that own a memosprite");
    }
}
