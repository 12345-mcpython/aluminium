package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Cone 21025 (2026-09-30): shape, plus a runtime measurement that CAN tell the two readings apart.
 *
 * <p>The wearer must wear 21025, so its place in the queue is not freely chosen -- it is decided by SPEED. Variant A of
 * the capability is "the next to act", variant B is "the party's first ally"; with the wearer fastest they coincide and
 * the test proves nothing (measured: 1210 leads the queue). So the fixture searches: who wears the cone, and which two
 * allies stand beside it, until an ally acts before the wearer AND one after. Discriminability is ASSERTED, because a
 * silent coincidence would make the engine mutation below invisible.
 */
public class Cone21025NextAllyTest {
    private static final int LEVEL = 80;

    private static List<CanHit> orderOf(Battle battle) {
        List<CanHit> order = new ArrayList<>();
        for (var s : battle.getQueueSnapshot()) {
            order.add(s.getCanHit());
        }
        return order;
    }

    @Test
    public void theShapeStatesWhatTheTextStates() {
        double[] values = {0.16, 0.20, 0.24, 0.28, 0.32};
        for (int rank = 1; rank <= 5; rank++) {
            var wearer = CharacterFactory.create(1210, LEVEL, true, Weapon.build(21025, LEVEL, false, rank));
            var rules = wearer.getTriggerTable().rulesFor(TriggerEvent.ATTACK_FINISHED).stream()
                    .filter(r -> r.id().equals("cone21025_next_ally")).toList();
            Assertions.assertEquals(1, rules.size(), "rank " + rank);
            Assertions.assertEquals(List.of("from_category BPSKILL"),
                    rules.getFirst().conditions().stream().map(c -> c.source()).toList(), "rank " + rank);
            var e = rules.getFirst().effects().getFirst();
            Assertions.assertEquals("ALL_DAMAGE_TYPE_BOOST", e.getAttribute(), "rank " + rank);
            Assertions.assertEquals(values[rank - 1], e.getPercent(), 1e-9, "rank " + rank);
            Assertions.assertEquals(1, e.getTurns(), "rank " + rank + ": one turn");
            Assertions.assertEquals("next_ally", e.getTarget(), "rank " + rank + ": the new selector");
        }
    }

    /** Finds a battle where the cone's wearer has an ally before and after it in the queue. */
    @Test
    public void theBoostLandsOnTheAllyWhoActsNext() {
        int[] wearers = {1210, 1204, 1209, 1211, 1112, 1101, 1102, 1103, 1104, 1001, 1002, 1004, 1006, 1008};
        int[] allies = {1001, 1002, 1004, 1006, 1008, 1101, 1102, 1103, 1104, 1112};
        Battle battle = null;
        List<CanHit> order = null;
        Character owner = null;
        for (int w : wearers) {
            for (int i = 0; i < allies.length && battle == null; i++) {
                for (int j = i + 1; j < allies.length && battle == null; j++) {
                    if (allies[i] == w || allies[j] == w) {
                        continue;
                    }
                    Character a = CharacterFactory.create(w, LEVEL, true, Weapon.build(21025, LEVEL, false, 1));
                    Character b = CharacterFactory.create(allies[i], LEVEL);
                    Character c = CharacterFactory.create(allies[j], LEVEL);
                    Battle cand = new Battle(List.of(a, b, c),
                            List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
                    cand.startBattle();
                    List<CanHit> ord = orderOf(cand);
                    if (ord.size() != 4 || !ord.contains(a)) {
                        continue;
                    }
                    int idx = ord.indexOf(a);
                    int before = 0;
                    int after = 0;
                    for (int k = 0; k < ord.size(); k++) {
                        CanHit u = ord.get(k);
                        if (u != a && cand.allies.contains(u)) {
                            if (k < idx) {
                                before++;
                            } else {
                                after++;
                            }
                        }
                    }
                    if (before >= 1 && after >= 1) {
                        battle = cand;
                        order = ord;
                        owner = a;
                        System.out.println("[21025] wearer=" + w + " allies=" + allies[i] + "," + allies[j]
                                + " order=" + ord.stream().map(CanHit::getName).toList());
                    }
                }
            }
        }
        Assertions.assertNotNull(battle, "no searched trio put the wearer between two allies");
        final Character own = owner;
        final Battle bt = battle;
        final List<CanHit> ord = order;
        int index = ord.indexOf(own);
        CanHit expected = null;
        for (int i = 1; i <= ord.size(); i++) {
            CanHit cand = ord.get((index + i) % ord.size());
            if (cand != own && bt.allies.contains(cand)) {
                expected = cand;
                break;
            }
        }
        Assertions.assertNotNull(expected, "another ally acts later");
        final CanHit exp = expected;
        CanHit firstAlly = ord.stream().filter(u -> u != own && bt.allies.contains(u)).findFirst().orElseThrow();
        Assertions.assertNotEquals(firstAlly, exp, "the fixture must separate 'next to act' from 'first ally'");

        int fired = bt.fireTriggers(TriggerEvent.ATTACK_FINISHED, own, exp, 1, 0, SkillCategory.BPSKILL);
        Assertions.assertTrue(fired >= 1, "the wearer's rule fires");
        Assertions.assertEquals(0.16, exp.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "the ally who acts NEXT gets the boost");
        Assertions.assertEquals(0.0, own.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get(), 1e-9,
                "and not the wearer");
    }
}
