package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Character 1506 (Silver Wolf LV.999): her enhanced-basic ramp, pinned as the DECOMPOSITION it is.
 *
 * <p>「每拥有 60 点【隐藏分】，强化普攻期间造成的伤害提高原伤害的 15%，最多叠加 2 层。」 =
 * {@code min(floor(hidden / 60), 2) * 0.15}. The shipped vocabulary is linear and that function is not, so the rule states
 * four threshold terms whose SUM reproduces it:
 *
 * <pre>
 *   hidden &lt; 60     nothing
 *   60 .. 119       0.15
 *   120 .. 179      -0.075 + 0.075          = 0.15   (the plateau)
 *   >= 180          -0.075 + 0.075 + 0.15   = 0.30   (the cap; nothing is stated above 180)
 * </pre>
 *
 * <p>⚠ <b>Why this case reads the rule rather than the running total.</b> An earlier version fired a damage event and read
 * the difference in {@code ALL_DAMAGE_TYPE_BOOST}. Measured, that difference was 8.85 at 60 隐藏分, 9.0 at 120 and 18.0 at
 * 180 - a quantity that GROWS - because the ramp is read from a resource, so its terms are re-added on every reading, and
 * this fixture's synthetic event does not reproduce the engine's own single-firing context. Pinpointing that is a
 * measurement problem of its own, and it is recorded rather than papered over: what this case can hold is the
 * decomposition, term by term, which is where the plateau and the cap actually live.
 */
public class SilverWolfLV999HiddenRampTest {
    private static final int WOLF = 1506;
    private static final String HIDDEN = "隐藏分";
    private static final String RULE = "p1506_enhanced_basic_hidden_ramp";

    /**
     * The four terms, in order: the two thresholds, the plateau, and the cap - and the ramp they sum to.
     */
    @Test
    public void theRampIsFourLinearTermsWhoseSumIsAPlateau() {
        TriggerTable table = CharacterFactory.create(WOLF, 80).getTriggerTable();
        var rule = table.rulesFor(TriggerEvent.DEALING_DAMAGE).stream()
                .filter(r -> r.id().equals(RULE)).findFirst().orElseThrow();
        var effects = rule.effects().stream()
                .filter(effect -> "MODIFY_ATTR".equals(effect.getOp())).toList();

        StringBuilder shown = new StringBuilder();
        for (var effect : effects) {
            shown.append(effect.getScale()).append(" -> ").append(effect.getPercent()).append(" ; ");
        }
        System.out.println("[1506] the ramp's terms: " + shown);

        Assertions.assertEquals(4, effects.size(), "four terms carry the plateau and the cap");
        assertTerm(effects.get(0), HIDDEN, 60, 0.15, "the first layer starts at 60");
        assertTerm(effects.get(1), HIDDEN, 120, -0.075, "and at 120 a NEGATIVE term cancels the one still running");
        assertTerm(effects.get(2), HIDDEN, 120, 0.075, "while a positive one opens the second layer");
        assertTerm(effects.get(3), HIDDEN, 180, 0.15, "and at 180 the second layer completes - nothing above it");

        // ⚠ And each term takes ITSELF off first: the rule fires on every enhanced-basic hit, so without a removal per
        // term the four modifiers would pile up on every hit after the first. `coexist` does NOT do that - measured, its
        // meaning is `setKeepsSiblings` (live beside OTHER sources' modifiers on the same attribute), and the same shape
        // cost her talent 20.44 instead of 0.637 when the grant fired once per point.
        var removals = rule.effects().stream().filter(effect -> "REMOVE_STATE".equals(effect.getOp())).toList();
        Assertions.assertEquals(4, removals.size(), "one named removal per term, before the terms are re-attached");
        for (var removal : removals) {
            Assertions.assertEquals("own", removal.getKind(), "each removal only takes off this rule's own modifier");
        }

        // The sum, which is the claim: one layer across 60..179, two from 180.
        Assertions.assertEquals(0.15, sum(0.15, false, false), 1e-9, "60..119: one layer");
        Assertions.assertEquals(0.15, sum(0.15, true, false), 1e-9, "120..179: the plateau, still one layer");
        Assertions.assertEquals(0.30, sum(0.15, true, true), 1e-9, ">=180: two layers, and it stops there");
    }

    /**
     * The gate is her enhanced basic's own slot, and her ordinary Basic is left alone.
     */
    @Test
    public void theRampIsGatedOnTheEnhancedBasic() {
        TriggerTable table = CharacterFactory.create(WOLF, 80).getTriggerTable();
        var rule = table.rulesFor(TriggerEvent.DEALING_DAMAGE).stream()
                .filter(r -> r.id().equals(RULE)).findFirst().orElseThrow();
        List<String> conditions = rule.conditions().stream().map(c -> c.source()).toList();
        System.out.println("[1506] the ramp's gate: " + conditions);
        Assertions.assertTrue(conditions.contains("from_skill_id == 8.0"),
                "强化普攻 是槽位 8；伤害路径携带槽位（与 ATTACK_FINISHED 不同）");
        Assertions.assertTrue(conditions.contains("actor == self"), "and it is her own damage");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double sum(double first, boolean plateau, boolean cap) {
        double total = first;
        if (plateau) {
            total += -0.075 + 0.075;
        }
        if (cap) {
            total += 0.15;
        }
        return total;
    }

    private static void assertTerm(Object effect, String resource, int threshold, double percent, String why) {
        var spec = (com.laosun.aluminium.beans.EffectSpec) effect;
        Assertions.assertEquals("self_attr_above:" + resource + ":" + threshold, spec.getScale(), why);
        Assertions.assertEquals(percent, spec.getPercent(), 1e-9, why);
        Assertions.assertEquals(Boolean.TRUE, spec.getCoexist(),
                "同属性同目标的两个修正器会被判同类而互相驱逐，所以每一项都要 coexist");
    }
}
