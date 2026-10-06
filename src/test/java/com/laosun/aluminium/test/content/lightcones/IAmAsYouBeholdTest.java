package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * Light cone 23062: two of its three clauses.
 *
 * <p>ATK +18..30% and energy regeneration +10..20%, both permanent. Entering battle or casting the ultimate grants the
 * state for 3 turns, and while it holds EVERY ally's crit damage is up 24..48%. The per-energy clause is REGISTERED.
 *
 * <p>Note: Two rules rather than one, because the condition DSL has no OR; and all three effects of a grant share the
 * state's own 3-turn life, so they live and die together (the engine has no "as long as the state lasts" spelling).
 */
public class IAmAsYouBeholdTest {
    private static final int CONE = 23062;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] ATK = {0.18, 0.21, 0.24, 0.27, 0.30};
    private static final double[] REGEN = {0.10, 0.125, 0.15, 0.175, 0.20};
    private static final double[] CRITDMG = {0.24, 0.30, 0.36, 0.42, 0.48};
    private static final String KING = "王之娱乐";

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesWhatTheTextStates() {
        for (int rank = 1; rank <= 5; rank++) {
            var table = wearer(rank).getTriggerTable();

            var stats = table.rulesFor(TriggerEvent.BATTLE_START).stream()
                    .filter(r -> r.id().equals("cone23062_stats")).toList();
            Assertions.assertEquals(1, stats.size(), "rank " + rank + ": the stat rule");
            var st = stats.getFirst().effects();
            Assertions.assertEquals(2, st.size(), "rank " + rank + ": ATK then regen");
            Assertions.assertEquals("ATTACK", st.get(0).getAttribute());
            Assertions.assertEquals(ATK[rank - 1], st.get(0).getPercent(), 1e-9, "rank " + rank + ": ATK");
            Assertions.assertEquals("ENERGY_REGENERATION_RATE", st.get(1).getAttribute());
            Assertions.assertEquals(REGEN[rank - 1], st.get(1).getPercent(), 1e-9, "rank " + rank + ": regen");
            Assertions.assertEquals("self", st.get(1).getTarget());

            for (var pair : java.util.List.of(
                    new Object[]{"cone23062_king_on_battle", TriggerEvent.BATTLE_START},
                    new Object[]{"cone23062_king_on_ult", TriggerEvent.ULT_CAST})) {
                String id = (String) pair[0];
                TriggerEvent event = (TriggerEvent) pair[1];
                var rules = table.rulesFor(event).stream().filter(r -> r.id().equals(id)).toList();
                Assertions.assertEquals(1, rules.size(), "rank " + rank + ": " + id);
                var k = rules.getFirst().effects();
                Assertions.assertEquals(2, k.size(), id + ": the state and the bonus");
                Assertions.assertEquals("APPLY_BUFF", k.get(0).getOp());
                Assertions.assertEquals(KING, k.get(0).getBuff(), id + ": the state's name");
                Assertions.assertEquals(3, k.get(0).getTurns(), id + ": three turns");
                Assertions.assertEquals("CRIT_ATTACK", k.get(1).getAttribute());
                Assertions.assertEquals(CRITDMG[rank - 1], k.get(1).getPercent(), 1e-9, id + ": crit damage");
                Assertions.assertEquals(3, k.get(1).getTurns(), id + ": as long as the state");
                Assertions.assertEquals("all_allies", k.get(1).getTarget(), id + ": EVERY ally");
            }
            System.out.println("[23062] rank=" + rank + " ok");
        }
    }
}
