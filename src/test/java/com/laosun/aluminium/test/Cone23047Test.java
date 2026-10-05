package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 23047: two of its five clauses.
 *
 * <p>Effect hit +40..60%; when an enemy falls into a debuff the WEARER applied, an 80% base chance puts Enthrallment (魂迷) on it
 * for 3 turns. The other three clauses are REGISTERED, not approximated: the per-debuff DoT bonus counts "debuffs the
 * wearer applied" (per_stack reads the target's own count), the speed gift belongs to the ATTACKER, and "removes every
 * Enthrallment (魂迷) when the wearer is knocked out" needs a knocked-out event the engine does not fire.
 */
public class Cone23047Test {
    private static final int CONE = 23047;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] HIT = {0.40, 0.45, 0.50, 0.55, 0.60};

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesWhatTheTextStates() {
        for (int rank = 1; rank <= 5; rank++) {
            var table = wearer(rank).getTriggerTable();

            var hit = table.rulesFor(TriggerEvent.BATTLE_START).stream()
                    .filter(r -> r.id().equals("cone23047_effect_hit")).toList();
            Assertions.assertEquals(1, hit.size(), "rank " + rank + ": the effect-hit rule");
            var e = hit.getFirst().effects().getFirst();
            Assertions.assertEquals("MODIFY_ATTR", e.getOp());
            Assertions.assertEquals("EFFECT_HIT_RATE", e.getAttribute());
            Assertions.assertEquals(HIT[rank - 1], e.getPercent(), 1e-9, "rank " + rank + ": effect hit");
            Assertions.assertEquals("self", e.getTarget());

            var ensnare = table.rulesFor(TriggerEvent.DEBUFF_APPLIED).stream()
                    .filter(r -> r.id().equals("cone23047_ensnare")).toList();
            Assertions.assertEquals(1, ensnare.size(), "rank " + rank + ": the 魂迷 rule");
            Assertions.assertEquals(List.of("actor == self"),
                    ensnare.getFirst().conditions().stream().map(c -> c.source()).toList(),
                    "rank " + rank + ": only a debuff the WEARER applied (the event's actor is the applier)");
            var m = ensnare.getFirst().effects().getFirst();
            Assertions.assertEquals("APPLY_BUFF", m.getOp());
            Assertions.assertEquals("魂迷", m.getBuff(), "rank " + rank + ": the state's name");
            Assertions.assertEquals(3, m.getTurns(), "rank " + rank + ": three turns");
            Assertions.assertEquals("target", m.getTarget());
            Assertions.assertEquals(0.8, m.getBaseChance(), 1e-9, "rank " + rank + ": an 80% base chance");
            System.out.println("[23047] rank=" + rank + " ok");
        }
    }
}
