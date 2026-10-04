package com.laosun.aluminium.test;

import com.google.gson.Gson;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A rule's key has to be a key Gson maps (2026-10-02).
 *
 * <p>Found the hard way twice: I wrote `on_any` in a rule that had no `on` and concluded the engine could not spell it -- when
 * the real story is that RULE OBJECTS had no key guard at all, so a mistyped key vanished and the rule arrived as "Unknown
 * trigger event 'null'". `on_any` itself is spelled and read (`1205.json` uses it).
 */
public class RuleKeyGuardTest {
    /** A key Gson does not know leaves the rule with no event at all -- the failure the guard refuses at load time. */
    @Test
    public void aMistypedEventKeyLeavesNothing() {
        TriggerSpec mistyped = new Gson().fromJson("{\"onn\":\"BATTLE_START\",\"do\":[]}", TriggerSpec.class);
        TriggerSpec spelled = new Gson().fromJson("{\"on\":\"BATTLE_START\",\"on_any\":[\"TURN_START\"]}",
                TriggerSpec.class);
        System.out.println("[rule-keys] onn -> on=" + mistyped.getOn() + " onAny=" + mistyped.getOnAny()
                + " ; on/on_any -> on=" + spelled.getOn() + " onAny=" + spelled.getOnAny());
        Assertions.assertNull(mistyped.getOn(), "the mistyped key is dropped, so the rule never fires");
        Assertions.assertNull(mistyped.getOnAny(), "and it has no fallback event either");
        Assertions.assertEquals("BATTLE_START", spelled.getOn(), "the spelled `on` lands");
        Assertions.assertEquals(1, spelled.getOnAny().size(), "and `on_any` ADDS events beside it");
    }

    /** And the guard lets the corpus through, including the file that really writes `on_any`. */
    @Test
    public void everyShippedRuleSetLoads() {
        for (int cid : new int[] {1205, 1408, 1412, 1513}) {
            Assertions.assertNotNull(TriggerTables.of(cid), "cid " + cid + " loads through the rule guard");
        }
    }

    /** And a rule that can never fire is refused AT LOAD, by name -- not left to fail later as 'Unknown trigger event'. */
    @Test
    public void aRuleWithoutAnEventIsRefused() {
        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
                () -> TriggerTables.of(9901),
                "the fixture's rule states no event, so the load must refuse it");
        System.out.println("[rule-keys] fixture: " + failure.getMessage());
        Assertions.assertTrue(String.valueOf(failure.getMessage()).contains("neither"),
                "the message says what is missing: " + failure.getMessage());
    }
}
