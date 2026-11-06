package com.laosun.aluminium.test.content.characters.xueyi;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1214 雪衣's trace 摧锋轴承:「如果敌方目标当前韧性大于等于其自身韧性上限的 50%，施放终结技时造成的伤害提高 10%」.
 *
 * <p>The condition is a bar PERCENTAGE, which is what this rule needed from the engine (`target_toughness_percent`). The
 * test puts the same enemy at two toughness levels and compares the ultimate's settled damage.
 */
public class XueyiThrustBearingTest {
    private static final int XUEYI = 1214;
    private static final int LEVEL = 80;

    @Test
    public void theUltimateHitsHarderOnAHealthyBar() {
        double healthy = ultimateDamage(1.0);
        double worn = ultimateDamage(0.4);
        Assertions.assertTrue(healthy > 0, "precondition: the ultimate landed");
        Assertions.assertTrue(healthy > worn,
                "「韧性 ≥ 上限的 50% ⇒ 终结技伤害 +10%」: " + healthy + " vs " + worn);
        // The clause ADDS 0.1 to the boost zone, and the zone already carries her break-effect trace (0.373) plus 0.08
        // from her own sources, so the settled ratio is 1 + 0.1/1.453 -- pinned with attribution, like the Eidolon 1 case.
        Assertions.assertEquals(1 + 0.1 / 1.453, healthy / worn, 1e-3,
                "and it is the stated 10% ON TOP of the zone, not 10% of the final number");
    }

    /** The ultimate's settled damage against an enemy whose toughness is at {@code fraction} of its maximum. */
    private static double ultimateDamage(double fraction) {
        Character xueyi = CharacterFactory.create(XUEYI, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(xueyi)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // A bar big enough that the ultimate cannot break it: a break would add its own damage and swamp the 10%.
        enemy.setMaxStance(10_000);
        enemy.setStance(10_000 * fraction);
        double before = enemy.getCurrentHp();
        battle.castImmediate(xueyi.getSkills().get(SkillType.ULTRA), xueyi, List.of(enemy));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }
}
