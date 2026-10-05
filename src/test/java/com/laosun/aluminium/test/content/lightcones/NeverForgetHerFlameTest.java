package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Light cone 23050 (2026-09-30): clause 1 only -- the other two are registered, not approximated.
 *
 * <p>Break effect +60..120% permanently. The property name is the one shipped content already uses (BREAKING_EFFECT),
 * which is the same route that caught 23059's invented "HP".
 */
public class NeverForgetHerFlameTest {
    private static final int CONE = 23050;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] BREAK = {0.60, 0.75, 0.90, 1.05, 1.20};

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesWhatTheTextStates() {
        for (int rank = 1; rank <= 5; rank++) {
            var table = wearer(rank).getTriggerTable();
            var rules = table.rulesFor(TriggerEvent.BATTLE_START).stream()
                    .filter(r -> r.id().equals("cone23050_breaking_effect")).toList();
            Assertions.assertEquals(1, rules.size(), "rank " + rank + ": the break-effect rule");
            Assertions.assertTrue(rules.getFirst().conditions().isEmpty(),
                    "rank " + rank + ": unconditional");
            var e = rules.getFirst().effects().getFirst();
            Assertions.assertEquals("MODIFY_ATTR", e.getOp());
            Assertions.assertEquals("BREAKING_EFFECT", e.getAttribute(), "rank " + rank + ": the shipped name");
            Assertions.assertEquals(BREAK[rank - 1], e.getPercent(), 1e-9, "rank " + rank + ": break effect");
            Assertions.assertEquals("self", e.getTarget(), "rank " + rank + ": the wearer");
            System.out.println("[23050] rank=" + rank + " ok");
        }
    }
}
