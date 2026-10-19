package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Light cone 23046, from the shipped file (discipline 232).
 */
public class TheHellWhereIdealsBurnTest {
    private static final int WEAPON_ID = 23046;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;

    @Test
    public void theShippedRuleCarriesItsStacking() {
        Character c = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(WEAPON_ID, LEVEL));
        var rules = c.getTriggerTable().rulesFor(TriggerEvent.CAST_SETUP).stream()
                .filter(r -> r.id().startsWith("cone23046_")).toList();
        Assertions.assertEquals(1, rules.size(), "one skill rule");
        var effect = rules.getFirst().effects().getFirst();
        // 2026-10-19: the per-skill attack is a stack buffer plus a per_stack share now (it used to be a single modifier
        // with a max_stacks that nothing honoured).
        var stack = rules.getFirst().effects().get(0);
        var share = rules.getFirst().effects().get(1);
        System.out.println("[23046] spec stack=\"" + stack.getBuff() + "\" cap=" + stack.stackCap()
                + " ; share per_stack=\"" + share.getPerStack() + "\" percent=" + share.getPercent());
        Assertions.assertEquals("ADD_STACK", stack.getOp(), "the buffer");
        Assertions.assertEquals(4, stack.stackCap(), "up to four stacks");
        Assertions.assertEquals("ATTACK", share.getAttribute(), "attack");
        Assertions.assertEquals(0.1, share.getPercent(), 1e-9, "10% per layer at rank 1");
        Assertions.assertEquals("技能层", share.getPerStack(), "per layer");
}
    }
