package com.laosun.aluminium.test.data;

import com.laosun.aluminium.Constant;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * The Elation base-damage table is loaded: {@code data/elation_basic_level_damage.json}, the 101-row
 * level-indexed factor that the roadmap recorded as "never loaded".
 *
 * <p>Loaded strictly, like every other table, so this test can actually fail: a loader that returns an empty
 * table, or the wrong numbers, is caught here. (Its first version was lenient and a mutant proved it blind.)
 */
public class ElationBaseTableTest {

    @Test
    public void theTableCarriesTheMeasuredShape() {
        var table = Constant.ELATION_BASIC_LEVEL_DAMAGE;
        System.out.println("[elation_base] entries = " + table.size() + " ; level 1 = " + table.get(1));

        Assertions.assertEquals(101, table.size(), "the measured table has 101 entries");
        Assertions.assertEquals(108.0, table.get(1), 1e-9, "level 1 is 108");
        Assertions.assertEquals(116.0, table.get(2), 1e-9, "level 2 is 116");
        Assertions.assertEquals(124.0, table.get(3), 1e-9, "level 3 is 124");
    }
}
