package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
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
public class WhyDoesTheOceanSingTest {
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

            // 2026-10-19: the effect-hit share is the cone's own ffect_hit_rate property now (the rule was a duplicate),
            // so the panel carries it - read against a cone-less wearer.
            double plain = CharacterFactory.create(WEARER, LEVEL, true).getAttribute(AttributeType.EFFECT_HIT_RATE).get();
            Assertions.assertTrue(wearer(rank).getAttribute(AttributeType.EFFECT_HIT_RATE).get() > plain,
                    "rank " + rank + ": the effect-hit share reaches the panel");
            var ensnare = table.rulesFor(TriggerEvent.DEBUFF_APPLIED).stream()
                    .filter(r -> r.id().equals("cone23047_ensnare")).toList();
            Assertions.assertEquals(1, ensnare.size(), "rank " + rank + ": the Enthrallment (魂迷) rule");
            Assertions.assertEquals(List.of("actor == self", "!target has_state 魂迷"),
                    ensnare.getFirst().conditions().stream().map(c -> c.source()).toList(),
                    "rank " + rank + ": only a debuff the WEARER applied, and only while the target is not charmed yet - "
                            + "「同类效果无法叠加」 is also the guard against this rule meeting its own application");
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
