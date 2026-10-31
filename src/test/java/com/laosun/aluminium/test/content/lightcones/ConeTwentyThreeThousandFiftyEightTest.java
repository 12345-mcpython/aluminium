package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 邂逅于下一个花季 (23058): 「能量恢复效率提高 #4%。装备者的能量上限大于 120 时，每超出 10 点能量上限额外使能量恢复效率
 * 提高 #6%，最多计入 360 点超出的能量上限」.
 *
 * <p><b>What it needed.</b> Two things. First, the two shares must ADD; two same-attribute {@code MODIFY_ATTR}s with no
 * name replace each other (measured), so both effects carry the same {@code buff} name and {@code max_stacks} - they are
 * then two stackable instances and add. Second, the stair counts a cap that is not an attribute, so the interpreter's
 * {@code counterValue} gained {@code max_energy}.
 *
 * <p>Threshold: 「大于 120」 means 120 exactly is still zero layers (the same "above the line" arithmetic as 23001).
 */
public class ConeTwentyThreeThousandFiftyEightTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int CONE = 23058;

    /**
     * The fixed share plus the stair, at the ranks' own #4 values.
     */
    @Test
    public void theFixedShareAndTheStairAdd() {
        Assertions.assertEquals(0.10, err(1, 120), 1e-9,
                "at exactly 120: 「大于 120」 does not hold, so only the fixed 10%");
        Assertions.assertEquals(0.106, err(1, 140), 1e-9,
                "140 -> two 10-point steps above 120 -> 0.10 + 2 x 0.003");
        Assertions.assertEquals(0.118, err(1, 180), 1e-9, "180 -> six steps");
        Assertions.assertEquals(0.16 + 0.006, err(5, 140), 1e-9,
                "rank 5's fixed share is 16% and the step is the same 0.3%");
    }

    /**
     * 「最多计入 360 点」: past the cap the stair stops, at 36 steps.
     */
    @Test
    public void theStairStopsAtThreeHundredSixtyPoints() {
        Assertions.assertEquals(0.208, err(1, 480), 1e-9,
                "480 is exactly 360 above 120 -> 36 steps");
        Assertions.assertEquals(0.208, err(1, 600), 1e-9,
                "600 is 480 above 120, but only the first 360 count");
    }

    // ==================================================================

    private static double err(int rank, double maxEnergy) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
        wearer.setMaxEnergy(maxEnergy);
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        return wearer.getAttribute(AttributeType.ENERGY_REGENERATION_RATE).get();
    }
}
