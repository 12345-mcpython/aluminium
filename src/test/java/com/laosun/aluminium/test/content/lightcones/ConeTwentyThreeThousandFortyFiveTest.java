package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 没有回报的加冕 (23045): 「施放终结技时，使装备者的攻击力提高 #6%，若能量上限 ≥ 300，固定恢复等同于能量上限 10% 的能量，
 * 并再次使装备者的攻击力提高 #2%，持续 2 回合」.
 *
 * <p><b>What it needed.</b> Two ATK shares that must ADD: same-attribute modifiers replace each other, so both carry the
 * same {@code buff} name and {@code max_stacks: 2} (two stackable instances, the 23058 trick). The energy share is
 * {@code GAIN_ENERGY percent: 0.1, fixed: true} - fixed keeps the regeneration rate out, and the share is of the
 * recipient's OWN maximum (the 1512 spelling). The gate is the existing {@code self_max_energy} variable.
 */
public class ConeTwentyThreeThousandFortyFiveTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int CONE = 23045;

    /**
     * With a 300 max-energy bar both ATK shares apply (40% + 40%) and the fixed 10% energy comes back.
     */
    @Test
    public void theEnergyGateAddsTheSecondShareAndTheEnergy() {
        double base = weapon(1, 300).attackBase;
        double[] with = ultDeltas(1, 300, true);
        double[] without = ultDeltas(1, 300, false);
        Assertions.assertEquals(0.8 * base, with[0] - without[0], 1e-6,
                "「使攻击力提高 40%」 and 「再次提高 40%」 add up to 80% of BASE attack");
        Assertions.assertEquals(30.0, with[1] - without[1], 1e-6,
                "「固定恢复等同于能量上限 10% 的能量」: 10% of 300, untouched by regeneration rate");
    }

    /**
     * Below 300 only the first share exists; nothing is restored.
     */
    @Test
    public void belowTheCapOnlyTheFirstShareExists() {
        double base = weapon(1, 200).attackBase;
        double[] with = ultDeltas(1, 200, true);
        double[] without = ultDeltas(1, 200, false);
        Assertions.assertEquals(0.4 * base, with[0] - without[0], 1e-6, "only the unconditional 40%");
        Assertions.assertEquals(0.0, with[1] - without[1], 1e-6, "「若能量上限 ≥ 300」 does not hold, so no energy");
    }

    /**
     * The two shares are per rank (#6 = #2 = 40 / 50 / 60 / 70 / 80%).
     */
    @Test
    public void bothSharesFollowTheRank() {
        double[] expected = {0.4, 0.5, 0.6, 0.7, 0.8};
        for (int rank = 1; rank <= 5; rank++) {
            double base = weapon(rank, 300).attackBase;
            double[] with = ultDeltas(rank, 300, true);
            double[] without = ultDeltas(rank, 300, false);
            Assertions.assertEquals(2 * expected[rank - 1] * base, with[0] - without[0], 1e-6,
                    "rank " + rank + ": #6 + #2 = " + (2 * expected[rank - 1]));
        }
    }

    // ==================================================================

    /** The fixture, queried only for its base attack (no battle needed). */
    private static Weaponed weapon(int rank, double maxEnergy) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
        wearer.setMaxEnergy(maxEnergy);
        return new Weaponed(wearer.getAttribute(AttributeType.ATTACK).baseValue());
    }

    private record Weaponed(double attackBase) {
    }

    /**
     * @return {@code [attackDelta, energyDelta]} across an ultimate cast
     */
    private static double[] ultDeltas(int rank, double maxEnergy, boolean withCone) {
        Character wearer = withCone
                ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank))
                : CharacterFactory.create(WEARER, LEVEL);
        wearer.setMaxEnergy(maxEnergy);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
        battle.startBattle();
        double attackBefore = wearer.getAttribute(AttributeType.ATTACK).get();
        double energyBefore = wearer.getCurrentEnergy();
        battle.castImmediate(wearer.getSkills().get(SkillType.ULTRA), wearer, List.of(enemy));
        return new double[]{
                wearer.getAttribute(AttributeType.ATTACK).get() - attackBefore,
                wearer.getCurrentEnergy() - energyBefore,
        };
    }
}
