package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 24004 (2026-09-30): two of its three clauses.
 *
 * <p>ATK +8..12%; after an attack that hits three or more enemies, SPD +8..16% for 1 turn. The \u300cfor each enemy hit\u300d
 * clause is REGISTERED, not approximated: its multiplier is the number of enemies hit, and per_stack reads only the
 * target's debuff count, DoT count or a counter (measured).
 */
public class Cone24004Test {
    private static final int CONE = 24004;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] ATK = {0.08, 0.09, 0.10, 0.11, 0.12};
    private static final double[] SPD = {0.08, 0.10, 0.12, 0.14, 0.16};

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesWhatTheTextStates() {
        for (int rank = 1; rank <= 5; rank++) {
            var table = wearer(rank).getTriggerTable();

            var atk = table.rulesFor(TriggerEvent.BATTLE_START).stream()
                    .filter(r -> r.id().equals("cone24004_attack")).toList();
            Assertions.assertEquals(1, atk.size(), "rank " + rank + ": the ATK rule");
            var a = atk.getFirst().effects().getFirst();
            Assertions.assertEquals("MODIFY_ATTR", a.getOp());
            Assertions.assertEquals("ATTACK", a.getAttribute());
            Assertions.assertEquals(ATK[rank - 1], a.getPercent(), 1e-9, "rank " + rank + ": ATK");
            Assertions.assertEquals("self", a.getTarget());

            var spd = table.rulesFor(TriggerEvent.ATTACK_FINISHED).stream()
                    .filter(r -> r.id().equals("cone24004_speed_on_wide_hit")).toList();
            Assertions.assertEquals(1, spd.size(), "rank " + rank + ": the SPD rule");
            Assertions.assertEquals(List.of("actor == self", "hit_count >= 3.0"),
                    spd.getFirst().conditions().stream().map(c -> c.source()).toList(),
                    "rank " + rank + ": three enemies, in the parser's form");
            var s = spd.getFirst().effects().getFirst();
            Assertions.assertEquals("MODIFY_ATTR", s.getOp());
            Assertions.assertEquals("SPEED", s.getAttribute());
            Assertions.assertEquals(SPD[rank - 1], s.getPercent(), 1e-9, "rank " + rank + ": SPD");
            Assertions.assertEquals(1, s.getTurns(), "rank " + rank + ": one turn");
            Assertions.assertEquals("self", s.getTarget());
            System.out.println("[24004] rank=" + rank + " ok");
        }
    }
}
