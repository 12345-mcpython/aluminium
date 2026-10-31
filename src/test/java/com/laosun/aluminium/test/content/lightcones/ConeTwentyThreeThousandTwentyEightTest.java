package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
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
 * 偏偏希望无价 (23028): 「战斗中暴击伤害大于 <b>120%</b> 时，每超过 <b>20%</b>，追加攻击造成的伤害提高 <b>12%</b>，可叠加 <b>4</b> 层」.
 *
 * <p>The second cone to use the threshold stair ({@code per_steps: "CRIT_ATTACK-1.2:0.2"}). CRIT_DMG is a ratio here, so the
 * sentence's 120% is 1.2 and its 20% is 0.2 - the same numbers, in the units the attribute is stored in.
 */
public class ConeTwentyThreeThousandTwentyEightTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1204;
    private static final double SHARE = 0.12;

    /**
     * The follow-up boost at a forced CRIT DMG, with the cone on.
     */
    private static double boostAt(double critDamage) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(23028, LEVEL, false, 1));
        double natural = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        wearer.getAttribute(AttributeType.CRIT_ATTACK)
                .addModifier(DoubleValue.Modifier.pure(critDamage - natural));
        Battle battle = new Battle(new ArrayList<>(List.of(wearer)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        return wearer.getAttribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST).get();
    }

    @Test
    public void theStairCountsTheCritDamageAboveOneHundredTwentyPercent() {
        double atThreshold = boostAt(1.2);
        double oneStep = boostAt(1.4);
        double twoSteps = boostAt(1.6);
        System.out.println("[23028] at 1.2 = " + atThreshold + " ; 1.4 = " + oneStep + " ; 1.6 = " + twoSteps);
        Assertions.assertEquals(0.0, atThreshold, 1e-9, "「大于 120% 时」 - at exactly 120% there is no layer");
        Assertions.assertEquals(SHARE, oneStep, 1e-9, "140% is one step above the line");
        Assertions.assertEquals(2 * SHARE, twoSteps, 1e-9, "160% is two steps");
        Assertions.assertEquals(4 * SHARE, boostAt(2.0), 1e-9, "200% is four steps - the sentence's ceiling");
        Assertions.assertEquals(4 * SHARE, boostAt(3.0), 1e-9,
                "「可叠加 4 层」 - past the ceiling it keeps paying four");
    }
}
