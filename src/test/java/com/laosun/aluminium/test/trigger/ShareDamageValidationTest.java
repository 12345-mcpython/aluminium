package com.laosun.aluminium.test.trigger;

import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.TriggerSpecs;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The engine-side judge for {@code SHARE_DAMAGE}: the op is only meaningful on the event that carries the instance being
 * settled, the share is a fraction, and the bearer must be named. Every refusal has to name the reason, because each one is
 * the "loads and does nothing" shape the closed vocabulary exists to prevent.
 */
public class ShareDamageValidationTest {

    private static final int OWNER = 1002;

    private static EffectSpec share(String event, Double percent, String target) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "SHARE_DAMAGE");
        TriggerSpecs.set(effect, "percent", percent);
        TriggerSpecs.set(effect, "target", target);
        return effect;
    }

    private static IllegalArgumentException refused(TriggerSpec rule) {
        return Assertions.assertThrows(IllegalArgumentException.class, () -> new TriggerTable(OWNER, List.of(rule)));
    }

    @Test
    public void itOnlyRunsOnTheEventThatCarriesTheInstance() {
        IllegalArgumentException wrongEvent =
                refused(TriggerSpecs.rule("TAKING_HIT", List.of(), share("TAKING_HIT", 0.5, "self")));
        Assertions.assertTrue(wrongEvent.getMessage().contains("DEALING_DAMAGE"), wrongEvent.getMessage());
    }

    @Test
    public void theShareIsAFractionOfTheHit() {
        for (double bad : new double[]{0.0, 1.0, 1.5, -0.2}) {
            IllegalArgumentException error =
                    refused(TriggerSpecs.rule("DEALING_DAMAGE", List.of(), share("DEALING_DAMAGE", bad, "self")));
            Assertions.assertTrue(error.getMessage().contains("fraction"), "percent " + bad + ": " + error.getMessage());
        }
    }

    @Test
    public void theBearerMustBeNamed() {
        IllegalArgumentException error =
                refused(TriggerSpecs.rule("DEALING_DAMAGE", List.of(), share("DEALING_DAMAGE", 0.5, null)));
        Assertions.assertTrue(error.getMessage().contains("target"), error.getMessage());
    }

    @Test
    public void theShippedUserLoads() {
        // The one real reader: Fu Xuan's zone. Loading it is the spelling check (every key is validated at load).
        Assertions.assertNotNull(TriggerTables.of(1208));
    }
}
