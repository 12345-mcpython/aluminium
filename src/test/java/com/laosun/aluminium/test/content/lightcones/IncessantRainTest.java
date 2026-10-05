package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Weapon;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Light cone 2300(2026-09-30): three of its four clauses.
 *
 * <p>Sentences: effect hit +24..40%; apply [以太编码] to a random HIT target not holding it (after a basic
 * attack / skill, and separately after an ultimate); the holder takes +12..20% damage for 1 turn. The ">=N debuffs"
 * clause is REGISTERED, not approximated.
 *
 * <p>Note: The tier axis is the SUPERIMPOSITION RANK (exact rank, no accumulation), so each tier is read at its own rank.
 */
public class IncessantRainTest {
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
                // Note: The VALUE, not just the keyword: a mutation that swaps BPSKILL for ULTRA stayed green
                // while this line only asked for the prefix -- measured, and the reason the expected text is literal.
                Assertions.assertEquals(id.endsWith("bpskill") ? "from_category BPSKILL" : "from_category ULTRA",
                        conditions.get(1), id + ": the cast category the sentence names");
                var effects = rules.getFirst().effects();
                Assertions.assertEquals(2, effects.size(), id + " has two effects");
                Assertions.assertEquals("APPLY_BUFF", effects.get(0).getOp(), id);
                Assertions.assertEquals("以太编码", effects.get(0).getBuff(), id + ": the state's name");
                Assertions.assertEquals(1, effects.get(0).getTurns(), id + ": 1 turn");
                Assertions.assertEquals("random_hit_enemy", effects.get(0).getTarget(),
                        id + ": a random one of the targets that WAS HIT");
                // The FILTER is a dimension of its own: the exclusion rides on the effect, tested per
                // candidate -- and since round 245 the random selector applies it BEFORE rolling.
                Assertions.assertEquals(List.of("!target has_state 以太编码"),
                        effects.get(0).getTargetWhen(), id + ": only targets NOT holding the state");
                Assertions.assertEquals(List.of("target has_state 以太编码"),
                        effects.get(1).getTargetWhen(), id + ": and only the holder takes more damage");
                Assertions.assertEquals("MODIFY_DAMAGE_TAKEN", effects.get(1).getOp(), id);
                Assertions.assertEquals(VULN[rank - 1], effects.get(1).getPercent(), 1e-9, id + ": the vulnerability");
                Assertions.assertEquals(1, effects.get(1).getTurns(), id);
                System.out.println("[23007] rank=" + rank + " " + id + " conditions=" + conditions);
            }
        }
    }

    /**
     * The second sentence: the crit rate against a target carrying three or more negative effects.
     *
     * <p>Note: The bonus belongs to THIS hit, so it carries {@code instance: true} -- without it the op would raise the
     * WEARER's attribute for a while, a different sentence. Note: It also carries {@code permanent}, because the op's
     * validation demands one of turn/permanent/until even on the instance path (measured 2026-09-30).
     * Note: The threshold reads {@code >= 3.0}: the parser writes an integral threshold with a decimal point.
     */
    @Test
    public void theCritClauseIsAnInstanceModifier() {
        for (int rank = 1; rank <= 5; rank++) {
            var table = wearer(rank).getTriggerTable();
            var rules = table.rulesFor(TriggerEvent.DEALING_DAMAGE).stream()
                    .filter(r -> r.id().equals("cone23007_crit_vs_debuffed")).toList();
            Assertions.assertEquals(1, rules.size(), "rank " + rank + ": the crit rule");
            var conditions = rules.getFirst().conditions().stream().map(c -> c.source()).toList();
            Assertions.assertEquals(List.of("actor == self", "target_debuff_count >= 3.0"), conditions,
                    "rank " + rank + ": the threshold, in the form the parser writes");
            var effect = rules.getFirst().effects().getFirst();
            Assertions.assertEquals("MODIFY_ATTR", effect.getOp());
            Assertions.assertEquals("CRIT_CHANCE", effect.getAttribute());
            Assertions.assertEquals(VULN[rank - 1], effect.getPercent(), 1e-9, "rank " + rank + ": the crit rate");
            Assertions.assertEquals(Boolean.TRUE, effect.getInstance(),
                    "the crit bonus is a property of the hit, not of the wearer");
            System.out.println("[23007] rank=" + rank + " crit conditions=" + conditions);
        }
    }
}
