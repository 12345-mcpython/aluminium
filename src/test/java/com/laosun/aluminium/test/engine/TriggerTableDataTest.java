package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.GameData;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The character trigger table can come from the data pack, not only from the static classpath cache.
 */
public class TriggerTableDataTest {

    /**
     * An empty provider must leave a real character with no rules.
     */
    @Test
    public void aCustomProviderReplacesTheTriggerTable() {
        GameData emptyTables = GameData.withTriggerTables(cid -> TriggerTable.EMPTY);

        Character withoutRules = CharacterFactory.create(emptyTables, 1003, 80, true, null, null, 0);
        Character withRules = CharacterFactory.create(1003, 80, true);

        Assertions.assertTrue(withoutRules.getTriggerTable().isEmpty(),
                "the empty provider must be honoured");
        Assertions.assertFalse(withRules.getTriggerTable().isEmpty(),
                "the default 1003 table still has rules");
    }
}
