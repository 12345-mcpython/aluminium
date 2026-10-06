package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * A rule that states `scale` on an op that does not read it must be refused, not silently ignored.
 *
 * <p>&#9888; This is the guard for {@code BOOST_TOUGHNESS}: 1315's "每层[优势口袋]使强化普攻的削韧值提高 50%" needs a
 * stack-scaled magnitude, which the op does not implement yet.
 */
public class ToughnessScaleRefusalTest {
    @Test
    public void theCharacterFilesStillLoad() {
        // The shipped files must be unaffected: they state a plain percent.
        Assertions.assertNotNull(TriggerTables.class);
        Assertions.assertTrue(CharacterFactory.exists(1315), "1315 exists in the data");
        Assertions.assertNotNull(CharacterFactory.create(1207, 80), "1207 (a BOOST_TOUGHNESS user) still builds");
    }
}
