package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 23007 (2026-09-30): three of its four clauses.
 *
 * <p>Sentences: effect hit +24..40%; apply 【\u4ee5\u592a\u7f16\u7801】 to a random HIT target not holding it (after a basic
 * attack / skill, and separately after an ultimate); the holder takes +12..20% damage for 1 turn. The \u300c\u2265N debuffs\u300d
 * clause is REGISTERED, not approximated.
 *
 * <p>\u26a0 The tier axis is the SUPERIMPOSITION RANK (exact rank, no accumulation), so each tier is read at its own rank.
 */
public class Cone23007Test {
    private static final int CONE = 23007;
    private static final int WEARER = 1210;
    private static final int LEVEL = 80;
    private static final double[] HIT = {0.24, 0.28, 0.32, 0.36, 0.40};
    private static final double[] VULN = {0.12, 0.14, 0.16, 0.18, 0.20};

    private Character wearer(int rank) {
        return CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
    }

    @Test
    public void everyTierStatesWhatTheTextStates() {
        for (int rank = 1; rank <= 5; rank++) {
            var table = wearer(rank).getTriggerTable();
            var start = table.rulesFor(TriggerEvent.BATTLE_START).stream()
                    .filter(r -> r.id().equals("cone23007_effect_hit")).toList();
            Assertions.assertEquals(1, start.size(), "rank " + rank + ": the effect-hit rule");
            var effect = start.getFirst().effects().getFirst();
            Assertions.assertEquals("MODIFY_ATTR", effect.getOp());
            Assertions.assertEquals("EFFECT_HIT_RATE", effect.getAttribute());
            Assertions.assertEquals(HIT[rank - 1], effect.getPercent(), 1e-9, "rank " + rank + ": effect hit");
            Assertions.assertEquals("self", effect.getTarget());
            for (String id : List.of("cone23007_mark_bpskill", "cone23007_mark_ultra")) {
                var rules = table.rulesFor(TriggerEvent.ATTACK_FINISHED).stream()
                        .filter(r -> r.id().equals(id)).toList();
                Assertions.assertEquals(1, rules.size(), "rank " + rank + ": " + id);
                var conditions = rules.getFirst().conditions().stream().map(c -> c.source()).toList();
                Assertions.assertEquals(2, conditions.size(), id + " has two conditions");
                Assertions.assertEquals("actor == self", conditions.get(0), id);
                Assertions.assertTrue(conditions.get(1).startsWith("from_category"), id + ": " + conditions.get(1));
                var effects = rules.getFirst().effects();
                Assertions.assertEquals(2, effects.size(), id + " has two effects");
                Assertions.assertEquals("APPLY_BUFF", effects.get(0).getOp(), id);
                Assertions.assertEquals("\u4ee5\u592a\u7f16\u7801", effects.get(0).getBuff(), id + ": the state's name");
                Assertions.assertEquals(1, effects.get(0).getTurns(), id + ": 1 turn");
                Assertions.assertEquals("random_hit_enemy", effects.get(0).getTarget(),
                        id + ": a random one of the targets that WAS HIT");
                Assertions.assertEquals("MODIFY_DAMAGE_TAKEN", effects.get(1).getOp(), id);
                Assertions.assertEquals(VULN[rank - 1], effects.get(1).getPercent(), 1e-9, id + ": the vulnerability");
                Assertions.assertEquals(1, effects.get(1).getTurns(), id);
                System.out.println("[23007] rank=" + rank + " " + id + " conditions=" + conditions);
            }
        }
    }
}
