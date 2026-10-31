package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
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
 * 向着不可追问处 (23037): the energy threshold clause - "after the wearer uses the Ultimate, if the energy THIS cast spent
 * is 140 or more, restores 1 Skill Point".
 *
 * <p><b>What it needed.</b> Nothing new in the engine: {@code ULT_CAST} already carries the energy the cast spent as its
 * amount (measured: 140 for a 140-cost ultimate), and the condition variable {@code event_amount} reads it. The ledger
 * used to say this value was always zero - that was measured <b>after</b> settlement, where it is zero on purpose (the
 * bar has been spent and the cast's own gain credited). The probe that settled the question is the rule itself:
 * {@code event_amount >= 140} fires for a 140-energy cast and does not for a 90-energy one.
 *
 * <p>The boundary is the interesting part: Argenti (银枝, 1302) has a 90 threshold on a 180 bar, so one fixture can
 * produce both a below-threshold cast (90) and an above-threshold one (180) without changing the character.
 */
public class ConeTwentyThreeThousandThirtySevenTest {

    private static final int LEVEL = 80;
    private static final int CONE = 23037;
    private static final int ARGENTI = 1302;
    private static final int MARCH = 1001;

    /**
     * 180 spent: the clause pays one Skill Point.
     */
    @Test
    public void oneHundredEightyEnergyPaysASkillPoint() {
        Fixture f = new Fixture(ARGENTI, 180);
        int before = f.battle.getSkillPoints();
        f.cast();
        Assertions.assertEquals(before + 1, f.battle.getSkillPoints(),
                "「本次终结技消耗的能量 ≥ 140 ⇒ 恢复 1 个战技点」");
    }

    /**
     * 90 spent: below the threshold, nothing is paid - the same character, the same cone, only the bar differs.
     */
    @Test
    public void ninetyEnergyPaysNothing() {
        Fixture f = new Fixture(ARGENTI, 90);
        int before = f.battle.getSkillPoints();
        f.cast();
        Assertions.assertEquals(before, f.battle.getSkillPoints(),
                "90 < 140: the clause must not fire (a rule without this boundary would pay on every ultimate)");
    }

    /**
     * A 120-cost ultimate is below the threshold too.
     */
    @Test
    public void aHundredTwentyEnergyCastPaysNothing() {
        Fixture f = new Fixture(MARCH, 120);
        int before = f.battle.getSkillPoints();
        f.cast();
        Assertions.assertEquals(before, f.battle.getSkillPoints(), "120 < 140");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture(int cid, double energy) {
            wearer = CharacterFactory.create(cid, LEVEL, true, Weapon.build(CONE, LEVEL, false, 1));
            battle = new Battle(new ArrayList<>(List.of(wearer)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
            wearer.setCurrentEnergy(energy);
        }

        private void cast() {
            Assertions.assertTrue(battle.castUltra(wearer, List.of(enemy)),
                    "the fixture must be able to cast: energy " + wearer.getCurrentEnergy());
            battle.processRequests();
        }
    }
}

