package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 于夜色中 (23001): 「速度大于 <b>100</b> 时，每超过 <b>10</b> 点，普攻和战技造成的伤害提高 <b>6%</b>，该效果可叠加 <b>6</b> 层」.
 *
 * <p>The stair counts the steps ABOVE a line, which is what {@code per_steps: "SPEED-100:10"} spells: 110 is one layer, 100 is none.
 * Without the threshold the same spelling would read {@code floor(110/10)} = eleven layers.
 */
public class ConeTwentyThreeThousandOneTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1204;
    private static final double SHARE = 0.06;

    /**
     * The wearer's basic-attack boost with the cone on, at a forced speed.
     */
    private static double boostAt(int speed) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23001, LEVEL, false, 1));
        double natural = wearer.getAttribute(AttributeType.SPEED).get();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        // Arm the panel before the battle starts, so the BATTLE_START rule reads the speed we mean to test.
        wearer.getAttribute(AttributeType.SPEED)
                .addModifier(com.laosun.aluminium.DoubleValue.Modifier.pure(speed - natural));
        battle.startBattle();
        return wearer.getAttribute(AttributeType.BASIC_ATTACK_DAMAGE_BOOST).get();
    }

    @Test
    public void theStairCountsTheStepsAboveOneHundred() {
        double atHundred = boostAt(100);
        Assertions.assertEquals(0.0, atHundred, 1e-9, "「大于 100 时」 - at 100 exactly, no layer");

        Assertions.assertEquals(SHARE, boostAt(110), 1e-9, "110 is one step above the line (not eleven)");
        Assertions.assertEquals(3 * SHARE, boostAt(130), 1e-9, "130 is three steps");
        Assertions.assertEquals(6 * SHARE, boostAt(160), 1e-9, "160 is six steps - the sentence's ceiling");
        Assertions.assertEquals(6 * SHARE, boostAt(200), 1e-9,
                "「可叠加 6 层」 - past the ceiling it keeps paying six, not ten");
    }
}
