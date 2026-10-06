package com.laosun.aluminium.test.engine;

import com.google.gson.Gson;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.data.TriggerTables;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * The key a file writes has to be the key Gson maps.
 *
 * <p>Found the hard way: 1513's reward wrote `maxStacks` (the Java name), the loader accepted it, Gson dropped it, and the
 * stackable state's cap became 1 -- so the sentence behaved as "one instance" with nothing to report.
 */
public class EffectKeyGuardTest {
    /** The Java name is dropped by Gson; the annotated key is what lands. */
    @Test
    public void theJavaNameIsSilentlyDropped() {
        EffectSpec camel = new Gson().fromJson("{\"op\":\"APPLY_BUFF\",\"maxStacks\":99}", EffectSpec.class);
        EffectSpec snake = new Gson().fromJson("{\"op\":\"APPLY_BUFF\",\"max_stacks\":99}", EffectSpec.class);
        System.out.println("[effect-keys] maxStacks -> " + camel.getMaxStacks()
                + " ; max_stacks -> " + snake.getMaxStacks());
        Assertions.assertNull(camel.getMaxStacks(),
                "the Java name is not a key Gson maps -- it vanishes without a word");
        Assertions.assertEquals(99, snake.getMaxStacks(), "the annotated key is the one that lands");
    }

    /** And the guard lets the whole shipped corpus through, so it refuses only what Gson would drop. */
    @Test
    public void everyShippedCharacterLoads() {
        List<Integer> ids = List.of(1002, 1112, 1314, 1407, 1409, 1412, 1415, 1505, 1513);
        for (int cid : ids) {
            Assertions.assertNotNull(TriggerTables.of(cid), "cid " + cid + " loads through the key guard");
        }
    }
}
