package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 23056 (2026-09-30): every clause, after the "every four follow-ups" half was reclaimed.
 *
 * <p>Crit rate +18..30%; at battle start the shadow arrives for 3 turns and while it holds the wearer's ATK is up
 * 40..80% and every enemy takes 20..30% more damage; and every FOUR follow-up attacks grant the same shadow, expressed
 * the way the engine expresses thresholds -- a counter read as a condition on the same event, reset by REMOVE_STACK.
 *
 * <p>⚠ Literals carry the {@code .0} the parser writes for an integral threshold (four bites so far).
 */
public class Cone23056Test {
    private static final int CONE = 23056;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] CRIT = {0.18, 0.21, 0.24, 0.27, 0.30};
    private static final double[] ATK = {0.40, 0.50, 0.60, 0.70, 0.80};
    private static final double[] VULN = {0.20, 0.225, 0.25, 0.275, 0.30};
    private static final String SHADOW = "影噬";
    private static final String COUNTER = "累计追击";

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
            Assertions.assertEquals("CRIT_CHANCE", crit.getFirst().effects().getFirst().getAttribute());
            Assertions.assertEquals(CRIT[rank - 1], crit.getFirst().effects().getFirst().getPercent(), 1e-9,
                    "rank " + rank + ": crit rate");

            var shadow = table.rulesFor(TriggerEvent.BATTLE_START).stream()
                    .filter(r -> r.id().equals("cone23056_shadow")).toList();
            Assertions.assertEquals(1, shadow.size(), "rank " + rank + ": the battle-start half");
            var e = shadow.getFirst().effects();
            Assertions.assertEquals(3, e.size(), "rank " + rank + ": state, ATK, vulnerability");
            Assertions.assertEquals(SHADOW, e.get(0).getBuff());
            Assertions.assertEquals(3, e.get(0).getTurns());
            Assertions.assertEquals(ATK[rank - 1], e.get(1).getPercent(), 1e-9, "rank " + rank + ": ATK");
            Assertions.assertEquals(VULN[rank - 1], e.get(2).getPercent(), 1e-9, "rank " + rank + ": vulnerability");
            Assertions.assertEquals("all_enemies", e.get(2).getTarget());

            var count = table.rulesFor(TriggerEvent.FOLLOW_UP).stream()
                    .filter(r -> r.id().equals("cone23056_shadow_every4_count")).toList();
            Assertions.assertEquals(1, count.size(), "rank " + rank + ": the counting rule");
            Assertions.assertEquals(List.of("self_stacks:" + COUNTER + " < 4.0"),
                    count.getFirst().conditions().stream().map(c -> c.source()).toList(),
                    "rank " + rank + ": count while under four");
            var add = count.getFirst().effects().getFirst();
            Assertions.assertEquals("ADD_STACK", add.getOp());
            Assertions.assertEquals(COUNTER, add.getBuff(), "rank " + rank + ": the counter");
            Assertions.assertEquals(1.0, add.getAmount(), 1e-9, "rank " + rank + ": one per follow-up");
            Assertions.assertEquals(4, add.getMaxStacks(), "rank " + rank + ": capped at four");

            var pay = table.rulesFor(TriggerEvent.FOLLOW_UP).stream()
                    .filter(r -> r.id().equals("cone23056_shadow_every4")).toList();
            Assertions.assertEquals(1, pay.size(), "rank " + rank + ": the paying rule");
            Assertions.assertEquals(List.of("self_stacks:" + COUNTER + " >= 4.0"),
                    pay.getFirst().conditions().stream().map(c -> c.source()).toList(),
                    "rank " + rank + ": pay out at four");
            var p = pay.getFirst().effects();
            Assertions.assertEquals(4, p.size(), "rank " + rank + ": reset plus the same three effects");
            Assertions.assertEquals("REMOVE_STACK", p.get(0).getOp());
            Assertions.assertEquals(4.0, p.get(0).getAmount(), 1e-9, "rank " + rank + ": every four, not once");
            Assertions.assertEquals(SHADOW, p.get(1).getBuff(), "rank " + rank + ": the same shadow");
            Assertions.assertEquals(ATK[rank - 1], p.get(2).getPercent(), 1e-9, "rank " + rank + ": ATK");
            Assertions.assertEquals(VULN[rank - 1], p.get(3).getPercent(), 1e-9, "rank " + rank + ": vulnerability");
            Assertions.assertEquals("all_enemies", p.get(3).getTarget());
            System.out.println("[23056] rank=" + rank + " ok");
        }
    }
}
