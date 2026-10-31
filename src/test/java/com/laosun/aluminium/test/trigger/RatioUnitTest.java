package com.laosun.aluminium.test.trigger;

import com.google.gson.Gson;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@code ratio}: a ratio attribute's magnitude, spelled with the unit it means.
 *
 * <p>It is not a second number; it is the same fraction as {@code percent}, with a name that says "this is a ratio"
 * and a load-time refusal on flat attributes (where a ratio is not a sentence the engine can read).
 */
public class RatioUnitTest {

    /**
     * 1001's soft-aggro rule is the first content use: +500% is {@code ratio: 5}, not {@code percent: 5}.
     */
    @Test
    public void theSoftAggroRuleUsesRatio() {
        var effect = TriggerTables.of(1001).rulesFor(TriggerEvent.SKILL_CAST).stream()
                .filter(rule -> "skill_soft_aggro".equals(rule.id()))
                .findFirst()
                .orElseThrow()
                .effects().getFirst();

        Assertions.assertEquals(5, effect.getRatio(), 1e-9, "the ratio attribute reads ratio: 5");
        Assertions.assertNull(effect.getPercent(), "and not the ambiguous percent spelling");
    }

    /**
     * A flat attribute has no ratio: +5 ATTACK is points, and +500% of ATTACK is a share of the base.
     */
    @Test
    public void ratioIsRefusedOnAFlatAttribute() {
        String message = refusal("{\"op\":\"MODIFY_ATTR\",\"attribute\":\"ATTACK\",\"ratio\":5,"
                + "\"turns\":1,\"target\":\"self\"}");
        Assertions.assertTrue(message.contains("ratio") && message.contains("flat"),
                "the message names the field and the reason: " + message);
    }

    /**
     * One number, one spelling.
     */
    @Test
    public void ratioCannotBeCombinedWithPercent() {
        String message = refusal("{\"op\":\"MODIFY_ATTR\",\"attribute\":\"CRIT_CHANCE\",\"ratio\":0.5,"
                + "\"percent\":0.1,\"turns\":1,\"target\":\"self\"}");
        Assertions.assertTrue(message.contains("ratio") && message.contains("magnitude"),
                "the message names the field and the rule: " + message);
    }

    /**
     * Only MODIFY_ATTR reads a ratio.
     */
    @Test
    public void ratioIsRefusedOnAnotherOp() {
        String message = refusal("{\"op\":\"SHIELD\",\"ratio\":0.5,\"amount\":10,\"turns\":1,"
                + "\"target\":\"self\"}");
        Assertions.assertTrue(message.contains("ratio") && message.contains("MODIFY_ATTR"),
                "the message names the only reader: " + message);
    }

    private static String refusal(String effectJson) {
        EffectSpec effect = new Gson().fromJson(effectJson, EffectSpec.class);
        TriggerSpec rule = TriggerSpecs.rule("BATTLE_START", null, effect);
        return Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(1003, List.of(rule))).getMessage();
    }
}
