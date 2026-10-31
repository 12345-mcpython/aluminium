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
 * 向浪花掷下盛夏 (23064): 「若施放的欢愉技与上一次施放的不同，则额外获得【潮流】，【潮流】使装备者的欢愉度提高 #3%」.
 *
 * <p><b>What it needed.</b> The engine now remembers the slot of the last <b>finished</b> Elation cast
 * ({@code last_elation_skill_id}), so the clause is the two-variable comparison
 * {@code from_skill_id != last_elation_skill_id} on {@code CAST_SETUP}. The comparison's behaviour is pinned by
 * {@code LastElationSkillTest}; this test pins the content side (the state, the attribute and the per-rank share).
 */
public class ConeTwentyThreeThousandSixtyFourTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1506;   // carries both Elation slots
    private static final int CONE = 23064;

    /**
     * Casting an Elation skill grants [潮流] and raises the wearer's Elation degree by the rank's #3 (S1: 40%).
     */
    @Test
    public void anElationCastGrantsTideAndItsElationShare() {
        Fixture f = new Fixture(1);
        f.cast(SkillType.ELATION_SKILL);
        Assertions.assertTrue(f.wearer.getBuffManager().hasState("潮流"), "「额外获得【潮流】」");
        double withoutCone = bareElation();
        Assertions.assertEquals(0.4, f.elation() - withoutCone, 1e-9, "S1 #3 = 40% on top of the kit basis");
        f.cast(SkillType.ELATION_SKILL);
        Assertions.assertEquals(0.4, f.elation() - withoutCone, 1e-9, "the same skill again does not stack the state");
    }

    /**
     * The share is per rank (40 / 55 / 70 / 85 / 100%).
     */
    @Test
    public void theShareFollowsTheRank() {
        double[] expected = {0.4, 0.55, 0.7, 0.85, 1.0};
        double withoutCone = bareElation();
        for (int rank = 1; rank <= 5; rank++) {
            Fixture f = new Fixture(rank);
            f.cast(SkillType.ELATION_SKILL);
            Assertions.assertEquals(expected[rank - 1], f.elation() - withoutCone, 1e-9, "rank " + rank + ": #3");
        }
    }

    /** The 1506 kit casts the same skill, so its own elation share is the baseline the cone adds to. */
    private static double bareElation() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
        battle.startBattle();
        battle.castImmediate(wearer.getSkills().get(SkillType.ELATION_SKILL), wearer, List.of(enemy));
        return wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture(int rank) {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
            battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(1001, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
        }

        private void cast(SkillType slot) {
            battle.castImmediate(wearer.getSkills().get(slot), wearer, List.of(enemy));
        }

        private double elation() {
            return wearer.getAttribute(AttributeType.ELATION_DAMAGE_BOOST).get();
        }
    }
}

