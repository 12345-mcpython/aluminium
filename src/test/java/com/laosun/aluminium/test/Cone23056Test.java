package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Light cone 23056 (2026-09-30): both shippable clauses.
 *
 * <p>Crit rate +18..30%; at battle start the wearer gains the shadow for 3 turns, and while it holds the wearer's ATK is
 * up 40..80% and EVERY enemy takes 20..30% more damage. The other half of that sentence -- "or every 4 follow-up
 * attacks" -- is REGISTERED: the engine can stack a counter but has no "fire when the counter reaches N" spelling.
 *
 * <p>\u26a0 The state name is built from codepoints on the generator side (round 307's lesson); and the "while holding"
 * wording is expressed by giving all three effects the state's own 3-turn life, so they live and die together.
 */
public class Cone23056Test {
    private static final int CONE = 23056;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] CRIT = {0.18, 0.21, 0.24, 0.27, 0.30};
    private static final double[] ATK = {0.40, 0.50, 0.60, 0.70, 0.80};
    private static final double[] VULN = {0.20, 0.225, 0.25, 0.275, 0.30};
    private static final String SHADOW = "影噬";

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesWhatTheTextStates() {
        for (int rank = 1; rank <= 5; rank++) {
            var table = wearer(rank).getTriggerTable();

            var crit = table.rulesFor(TriggerEvent.BATTLE_START).stream()
                    .filter(r -> r.id().equals("cone23056_crit")).toList();
            Assertions.assertEquals(1, crit.size(), "rank " + rank + ": the crit rule");
            var c = crit.getFirst().effects().getFirst();
            Assertions.assertEquals("MODIFY_ATTR", c.getOp());
            Assertions.assertEquals("CRIT_CHANCE", c.getAttribute());
            Assertions.assertEquals(CRIT[rank - 1], c.getPercent(), 1e-9, "rank " + rank + ": crit rate");
            Assertions.assertEquals("self", c.getTarget());

            var shadow = table.rulesFor(TriggerEvent.BATTLE_START).stream()
                    .filter(r -> r.id().equals("cone23056_shadow")).toList();
            Assertions.assertEquals(1, shadow.size(), "rank " + rank + ": the shadow rule");
            var e = shadow.getFirst().effects();
            Assertions.assertEquals(3, e.size(), "rank " + rank + ": state, ATK, enemy vulnerability");
            Assertions.assertEquals("APPLY_BUFF", e.get(0).getOp());
            Assertions.assertEquals(SHADOW, e.get(0).getBuff(), "rank " + rank + ": the state's name");
            Assertions.assertEquals(3, e.get(0).getTurns(), "rank " + rank + ": three turns");
            Assertions.assertEquals("MODIFY_ATTR", e.get(1).getOp());
            Assertions.assertEquals("ATTACK", e.get(1).getAttribute());
            Assertions.assertEquals(ATK[rank - 1], e.get(1).getPercent(), 1e-9, "rank " + rank + ": ATK");
            Assertions.assertEquals(3, e.get(1).getTurns(), "rank " + rank + ": as long as the state");
            Assertions.assertEquals("self", e.get(1).getTarget());
            Assertions.assertEquals("MODIFY_DAMAGE_TAKEN", e.get(2).getOp());
            Assertions.assertEquals(VULN[rank - 1], e.get(2).getPercent(), 1e-9, "rank " + rank + ": vulnerability");
            Assertions.assertEquals(3, e.get(2).getTurns(), "rank " + rank + ": three turns");
            Assertions.assertEquals("all_enemies", e.get(2).getTarget(), "rank " + rank + ": EVERY enemy");
            System.out.println("[23056] rank=" + rank + " ok");
        }
    }
}
