package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.GameData;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.StageFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The relic-rule table is supplied through {@link GameData}, not read from the static loader.
 */
public class RelicTriggerTableDataTest {

    /**
     * Building a character with a suit must ask the provider once per worn set.
     */
    @Test
    public void theRelicProviderIsAskedForWornSets() {
        AtomicInteger calls = new AtomicInteger();
        GameData data = GameData.withRelicTriggerTables((setId, wornPieces) -> {
            calls.incrementAndGet();
            return TriggerTable.EMPTY;
        });

        CharacterFactory.create(data, 1003, 80, true, null, StageFactory.referenceRelics(), 0);

        Assertions.assertTrue(calls.get() > 0,
                "the reference suit's worn sets must reach the injected provider");
    }
}
