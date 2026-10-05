package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 23023 (2026-09-30): all three clauses.
 *
 * <p>DEF +40..64%; when the wearer grants a shield to an ally, the wearer's CRIT DAMAGE rises 40..64% for 2 turns; when
 * a follow-up attack hits, the enemies that were hit take 10..16% more damage for 2 turns.
 *
 * <p>Note: The tier axis is the SUPERIMPOSITION RANK (exact rank, no accumulation), so every tier is read at its own rank.
 */
public class InherentlyUnjustDestinyTest {
    private static final int CONE = 23023;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] DEF = {0.40, 0.46, 0.52, 0.58, 0.64};
    private static final double[] CRIT = {0.40, 0.46, 0.52, 0.58, 0.64};
    private static final double[] VULN = {0.10, 0.115, 0.13, 0.145, 0.16};

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesWhatTheTextStates() {
        for (int rank = 1; rank <= 5; rank++) {
            var table = wearer(rank).getTriggerTable();

            var def = table.rulesFor(TriggerEvent.BATTLE_START).stream()
                    .filter(r -> r.id().equals("cone23023_defence")).toList();
            Assertions.assertEquals(1, def.size(), "rank " + rank + ": the DEF rule");
            var defEffect = def.getFirst().effects().getFirst();
            Assertions.assertEquals("MODIFY_ATTR", defEffect.getOp());
            Assertions.assertEquals("DEFENCE", defEffect.getAttribute());
            Assertions.assertEquals(DEF[rank - 1], defEffect.getPercent(), 1e-9, "rank " + rank + ": DEF");
            Assertions.assertEquals("self", defEffect.getTarget());

            var shield = table.rulesFor(TriggerEvent.SHIELD_GRANTED).stream()
                    .filter(r -> r.id().equals("cone23023_crit_on_shield")).toList();
            Assertions.assertEquals(1, shield.size(), "rank " + rank + ": the shield rule");
            Assertions.assertEquals(List.of("actor == self"),
                    shield.getFirst().conditions().stream().map(c -> c.source()).toList(),
                    "rank " + rank + ": the shield must be the WEARER's");
            var crit = shield.getFirst().effects().getFirst();
            Assertions.assertEquals("MODIFY_ATTR", crit.getOp());
            Assertions.assertEquals("CRIT_ATTACK", crit.getAttribute());
            Assertions.assertEquals(CRIT[rank - 1], crit.getPercent(), 1e-9, "rank " + rank + ": crit damage");
            Assertions.assertEquals(2, crit.getTurns(), "rank " + rank + ": two turns");
            Assertions.assertEquals("self", crit.getTarget());

            var follow = table.rulesFor(TriggerEvent.FOLLOW_UP).stream()
                    .filter(r -> r.id().equals("cone23023_vuln_on_followup")).toList();
            Assertions.assertEquals(1, follow.size(), "rank " + rank + ": the follow-up rule");
            Assertions.assertEquals(List.of("actor == self"),
                    follow.getFirst().conditions().stream().map(c -> c.source()).toList(),
                    "rank " + rank + ": the wearer's own follow-up");
            var vuln = follow.getFirst().effects().getFirst();
            Assertions.assertEquals("MODIFY_DAMAGE_TAKEN", vuln.getOp());
            Assertions.assertEquals(VULN[rank - 1], vuln.getPercent(), 1e-9, "rank " + rank + ": the vulnerability");
            Assertions.assertEquals(2, vuln.getTurns(), "rank " + rank + ": two turns");
            Assertions.assertEquals("target", vuln.getTarget(),
                    "rank " + rank + ": the enemy that was hit -- FOLLOW_UP fires once per victim");
            System.out.println("[23023] rank=" + rank + " ok");
        }
    }
}
