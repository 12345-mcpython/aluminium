package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;


/**
 * Light cone 23054 (2026-09-30): three of its four clauses -- and the FIRST content reader of WAVE_START.
 *
 * <p>SPD +18..30% permanently; entering battle grants the charm for 3 turns, and while it holds EVERY ally's crit rate
 * is up 10..14% and crit damage 30..60%, with the wearer's own energy regeneration up 12..20%; and at every wave start
 * the wearer recovers a flat 15 energy.
 *
 * <p>Note: The other trigger of the grant -- "or casting the ultimate ON AN ALLY" -- is REGISTERED: the engine would
 * have to judge that the ultimate landed on our side.
 */
public class WhenSheDecidedToSeeTest {
    private static final int CONE = 23054;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] SPD = {0.18, 0.21, 0.24, 0.27, 0.30};
    private static final double[] CRIT = {0.10, 0.11, 0.12, 0.13, 0.14};
    private static final double[] CDMG = {0.30, 0.375, 0.45, 0.525, 0.60};
    private static final double[] REGEN = {0.12, 0.14, 0.16, 0.18, 0.20};
    private static final String CHARM = "上上签";

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesWhatTheTextStates() {
        for (int rank = 1; rank <= 5; rank++) {
            var table = wearer(rank).getTriggerTable();

            var speed = table.rulesFor(TriggerEvent.BATTLE_START).stream()
                    .filter(r -> r.id().equals("cone23054_speed")).toList();
            Assertions.assertEquals(1, speed.size(), "rank " + rank + ": the speed rule");
            var sp = speed.getFirst().effects().getFirst();
            Assertions.assertEquals("MODIFY_ATTR", sp.getOp());
            Assertions.assertEquals("SPEED", sp.getAttribute());
            Assertions.assertEquals(SPD[rank - 1], sp.getPercent(), 1e-9, "rank " + rank + ": speed");
            Assertions.assertEquals("self", sp.getTarget());

            var charm = table.rulesFor(TriggerEvent.BATTLE_START).stream()
                    .filter(r -> r.id().equals("cone23054_divination")).toList();
            Assertions.assertEquals(1, charm.size(), "rank " + rank + ": the charm rule");
            var e = charm.getFirst().effects();
            Assertions.assertEquals(4, e.size(), "rank " + rank + ": state, crit rate, crit damage, regen");
            Assertions.assertEquals("APPLY_BUFF", e.get(0).getOp());
            Assertions.assertEquals(CHARM, e.get(0).getBuff(), "rank " + rank + ": the state's name");
            Assertions.assertEquals(3, e.get(0).getTurns(), "rank " + rank + ": three turns");
            Assertions.assertEquals("CRIT_CHANCE", e.get(1).getAttribute());
            Assertions.assertEquals(CRIT[rank - 1], e.get(1).getPercent(), 1e-9, "rank " + rank + ": crit rate");
            Assertions.assertEquals("all_allies", e.get(1).getTarget(), "rank " + rank + ": EVERY ally");
            Assertions.assertEquals("CRIT_ATTACK", e.get(2).getAttribute());
            Assertions.assertEquals(CDMG[rank - 1], e.get(2).getPercent(), 1e-9, "rank " + rank + ": crit damage");
            Assertions.assertEquals("all_allies", e.get(2).getTarget(), "rank " + rank + ": EVERY ally");
            Assertions.assertEquals("ENERGY_REGENERATION_RATE", e.get(3).getAttribute());
            Assertions.assertEquals(REGEN[rank - 1], e.get(3).getPercent(), 1e-9, "rank " + rank + ": regen");
            Assertions.assertEquals("self", e.get(3).getTarget(), "rank " + rank + ": the WEARER's own regen");

            var wave = table.rulesFor(TriggerEvent.WAVE_START).stream()
                    .filter(r -> r.id().equals("cone23054_wave_energy")).toList();
            Assertions.assertEquals(1, wave.size(), "rank " + rank + ": the wave rule");
            var w = wave.getFirst().effects().getFirst();
            Assertions.assertEquals("GAIN_ENERGY", w.getOp());
            Assertions.assertEquals(15.0, w.getAmount(), 1e-9, "rank " + rank + ": a flat 15 energy");
            Assertions.assertEquals("self", w.getTarget());
            System.out.println("[23054] rank=" + rank + " ok");
        }
    }
}
