package com.laosun.aluminium.test.content.characters.luocha;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 1203 罗刹's Eidolon 2 has two halves that are mutually exclusive: 「触发战技效果时，若指定我方目标当前生命值百分比**小于 50%**，
 * 则治疗量提高 30%；**大于等于 50%**，则提供护盾」.
 *
 * <p>The shield half must carry the {@code >= 50%} gate, or a low-HP ally would get the shield as well - the two halves are
 * the sentence's own "if / otherwise", and this engine spells "otherwise" as a per-target filter on the shield. The heal
 * half is a {@code REGISTER_HEAL_BONUS} with {@code target_when: target_hp_percent < 0.5}.
 */
public class LuochaEidolonTwoTest {

    private static final int LEVEL = 80;
    private static final int LUOCHA = 1203;
    private static final int ALLY = 1002;

    /**
     * An ally at or above half HP gets the shield; an ally below half does NOT (that side is the heal bonus).
     */
    @Test
    public void theShieldOnlyCoversAnAllyAtOrAboveHalfHp() {
        Fixture high = new Fixture(2, 1.0);
        high.cast();
        Assertions.assertTrue(high.ally.getShield() > 0, "「大于等于 50%，则为其提供…护盾」");

        Fixture low = new Fixture(2, 0.3);
        low.cast();
        Assertions.assertEquals(0.0, low.ally.getShield(), 1e-9,
                "below half HP the shield must not apply - the other half of the sentence is the heal bonus");
    }

    /**
     * Below half HP the other half pays: the heal of an E2 Luocha is 30% larger than an E0 Luocha on the same target.
     */
    @Test
    public void theHealBonusIsTheOtherHalf() {
        // The heal itself can cap at full HP, so the BONUS is measured on the healing amount, not on HP gained.
        double e2 = new Fixture(2, 0.3).calculatedHeal();
        double e0 = new Fixture(0, 0.3).calculatedHeal();
        System.out.println("[1203] E2 heal=" + e2 + " E0 heal=" + e0);
        Assertions.assertEquals(1.3, e2 / e0, 1e-6,
                "「小于 50% 时治疗量提高 30%」 - same cast, same target");

        Fixture full = new Fixture(2, 1.0);
        Fixture fullBare = new Fixture(0, 1.0);
        Assertions.assertEquals(1.0, full.calculatedHeal() / fullBare.calculatedHeal(), 1e-6,
                "a full-HP ally gets no bonus (the condition is the whole point)");
    }

    /**
     * Without the Eidolon neither half runs: no shield at either HP level.
     */
    @Test
    public void withoutTheEidolonThereIsNoShield() {
        Fixture high = new Fixture(0, 1.0);
        high.cast();
        Assertions.assertEquals(0.0, high.ally.getShield(), 1e-9, "E0 has no shield clause");
    }

    // ==================================================================

    private static final class Fixture {
        private final Character luocha;
        private final Character ally;
        private final Battle battle;

        /**
         * @param eidolon the Eidolon rank
         * @param hpShare the ally's HP share the fixture starts with
         */
        private Fixture(int eidolon, double hpShare) {
            luocha = CharacterFactory.create(LUOCHA, LEVEL, true, null, null, eidolon);
            ally = CharacterFactory.create(ALLY, LEVEL);
            Enemy enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(luocha, ally)),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
            if (hpShare < 1.0) {
                ally.consumeHp(ally.getMaxHp() * (1.0 - hpShare));
            }
        }

        private void cast() {
            battle.castImmediate(luocha.getSkills().get(SkillType.SKILL), luocha, List.of(ally));
        }

        private double calculatedHeal() {
            return battle.calculateHeal(luocha, ally, 100.0);
        }
    }
}


