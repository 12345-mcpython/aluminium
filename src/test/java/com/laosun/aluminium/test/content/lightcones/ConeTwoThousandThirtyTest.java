package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 这就是我啦！(21030): 「施放终结技时造成的伤害提高，提高数值等同于装备者防御力的 <b>60%</b>，该效果每次施放终结技时对每个敌方目标仅生效 1 次」.
 *
 * <p><b>The earlier reading was measuring the wrong thing.</b> The row said 「实测一个额外实例都没有」 - but the clause does not add an
 * INSTANCE, it raises the instance's value: {@code ADD_DAMAGE} adds a flat addend to the damage being settled
 * ({@code Damage.flatAddend}). This test reads that addend, which is exactly the number the sentence names.
 */
public class ConeTwoThousandThirtyTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1204;

    /**
     * The flat addend the ultimate's instance carried, and the wearer's DEFENCE at that moment.
     */
    private static double[] run(boolean withCone) {
        Character wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21030, LEVEL, false, 1))
                : CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();
        wearer.setCurrentEnergy(wearer.getMaxEnergy());
        double[] flat = new double[1];
        battle.addDamageObserver((Damage damage, double applied, double hpLoss, double shield, boolean died) -> {
            if (damage.getType() == com.laosun.aluminium.enums.DamageType.NORMAL) {
                flat[0] = damage.getFlatAddend();
            }
        });
        battle.castImmediate(wearer.getSkills().get(SkillType.ULTRA), wearer, List.of(enemy));
        battle.processRequests();
        return new double[]{flat[0], wearer.getAttribute(AttributeType.DEFENCE).get()};
    }

    @Test
    public void theUltimateCarriesSixtyPercentOfHerDefence() {
        double[] bare = run(false);
        double[] cone = run(true);
        Assertions.assertEquals(0.0, bare[0], 1e-9, "without the cone the instance carries no such addend");
        Assertions.assertEquals(0.6 * cone[1], cone[0], 1e-6,
                "「提高数值等同于装备者防御力的 60%」 - the addend IS that share of HER defence ("
                        + cone[1] + " x 0.6)");
    }
}
