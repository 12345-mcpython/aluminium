package com.laosun.aluminium.test.data;

import com.laosun.aluminium.Constant;
import com.laosun.aluminium.GameData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The data tables are loaded by an object now, not only by {@code Constant}'s static initializer.
 *
 * <p>This is the seam a future multi-data-pack process needs: a second load must produce a second
 * set of tables without touching {@link Constant}, and the returned containers must stay frozen.
 */
public class GameDataTest {

    /**
     * A second load is a second object with the same content, and it is not mutable.
     */
    @Test
    public void aSecondLoadIsIndependentAndFrozen() {
        GameData data = GameData.load();

        Assertions.assertEquals(Constant.CHARACTERS.keySet(), data.characters().keySet(),
                "a second load sees the same character ids");
        Assertions.assertEquals(Constant.WEAPONS.keySet(), data.weapons().keySet(),
                "a second load sees the same light-cone ids");
        Assertions.assertNotSame(Constant.CHARACTERS, data.characters(),
                "the second load is its own table, not Constant's map");
        Assertions.assertThrows(UnsupportedOperationException.class, () -> data.characters().clear(),
                "the loaded tables stay frozen");
    }

    /**
     * The default object is the one the static facade reads.
     */
    @Test
    public void theDefaultIsWhatConstantReads() {
        Assertions.assertEquals(Constant.CHARACTERS.keySet(), GameData.DEFAULT.characters().keySet());
        Assertions.assertEquals(Constant.MONSTER_CONFIGS.keySet(), GameData.DEFAULT.monsterConfigs().keySet());
        Assertions.assertEquals(Constant.SKILLS.keySet(), GameData.DEFAULT.skills().keySet());
    }
}
