package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 23059 (2026-09-30): three of five clauses -- and the judge that caught two real errors the suite could not.
 *
 * <p>A rule that is never SELECTED is never validated, so a green suite said nothing: the HP rule named an attribute
 * the engine does not have (it is HEALTH), and the state rule hung a category test on SKILL_CAST, which carries none,
 * so it could never fire. Both are fixed here, and this test selects them.
 */
public class Cone23059Test {
    private static final int CONE = 23059;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] HP = {0.30, 0.375, 0.45, 0.525, 0.60};
    private static final String MARKER = "23059_wave_energy";
    private static final String STATE = "炼狱";

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesWhatTheTextStates() {
        for (int rank = 1; rank <= 5; rank++) {
            var table = wearer(rank).getTriggerTable();

            var hp = table.rulesFor(TriggerEvent.BATTLE_START).stream()
                    .filter(r -> r.id().equals("cone23059_hp")).toList();
            Assertions.assertEquals(1, hp.size(), "rank " + rank + ": the HP rule");
            Assertions.assertEquals("HEALTH", hp.getFirst().effects().getFirst().getAttribute(),
                    "rank " + rank + ": the attribute is HEALTH, not HP");
            Assertions.assertEquals(HP[rank - 1], hp.getFirst().effects().getFirst().getPercent(), 1e-9,
                    "rank " + rank + ": HP");

            var energy = table.rulesFor(TriggerEvent.TURN_START).stream()
                    .filter(r -> r.id().equals("cone23059_wave_energy")).toList();
            Assertions.assertEquals(1, energy.size(), "rank " + rank + ": the energy rule");
            Assertions.assertEquals(List.of("!self has_state " + MARKER),
                    energy.getFirst().conditions().stream().map(c -> c.source()).toList(),
                    "rank " + rank + ": only while the wave marker is absent");
            var e = energy.getFirst().effects();
            Assertions.assertEquals(20.0, e.get(0).getAmount(), 1e-9, "rank " + rank + ": a flat 20");
            Assertions.assertEquals("APPLY_BUFF", e.get(1).getOp(), "rank " + rank + ": it then marks");
            Assertions.assertEquals(MARKER, e.get(1).getBuff(),
                    "rank " + rank + ": the marker it applies -- the assertion the 7-way sweep demanded");

            var reset = table.rulesFor(TriggerEvent.WAVE_START).stream()
                    .filter(r -> r.id().equals("cone23059_wave_reset")).toList();
            Assertions.assertEquals(1, reset.size(), "rank " + rank + ": the per-wave reset");
            Assertions.assertEquals(MARKER, reset.getFirst().effects().getFirst().getBuff(),
                    "rank " + rank + ": clears exactly that marker");

            var state = table.rulesFor(TriggerEvent.ATTACK_FINISHED).stream()
                    .filter(r -> r.id().equals("cone23059_purgatory")).toList();
            Assertions.assertEquals(1, state.size(), "rank " + rank + ": the state rule");
            Assertions.assertEquals(List.of("from_category BPSKILL"),
                    state.getFirst().conditions().stream().map(c -> c.source()).toList(),
                    "rank " + rank + ": only a skill, on an event that CARRIES a category");
            var s = state.getFirst().effects().getFirst();
            Assertions.assertEquals(STATE, s.getBuff(), "rank " + rank + ": the state's name");
            Assertions.assertEquals(2, s.getTurns(), "rank " + rank + ": two turns");
            System.out.println("[23059] rank=" + rank + " ok");
        }
    }
}
