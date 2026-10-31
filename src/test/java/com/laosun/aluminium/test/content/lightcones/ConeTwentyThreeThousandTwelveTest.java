package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 如泥酣眠 (23012): 「当装备者的普攻或战技伤害未造成暴击时，使自身暴击率提高 #2%，持续 1 回合。该效果每 3 回合可以触发 1 次」.
 *
 * <p><b>What it needed.</b> A way to say "this hit did NOT crit": the crit roll happens during settlement, so the only
 * honest event is {@code DAMAGE_SETTLED}, and the new keyword {@code damage_did_not_crit} reads
 * {@code Damage.isCrit()}. The category filter ({@code from_category Normal / BPSkill}) narrows it to basic and skill
 * damage, and {@code cooldown: 3} is the sentence's own frequency.
 */
public class ConeTwentyThreeThousandTwelveTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1001;
    private static final int CONE = 23012;

    /**
     * A non-crit basic raises crit chance by the rank's #2; a non-crit skill does too.
     */
    @Test
    public void aNonCritBasicOrSkillRaisesCritChance() {
        Fixture basic = new Fixture(1, false);
        Assertions.assertEquals(0.36, basic.hit(SkillCategory.NORMAL), 1e-9,
                "「普攻…未造成暴击时，暴击率 +36%」");

        Fixture skill = new Fixture(1, false);
        Assertions.assertEquals(0.36, skill.hit(SkillCategory.BPSKILL), 1e-9, "and a skill hit does the same");
    }

    /**
     * A hit that DOES crit, or a non-crit ultimate, raises nothing.
     */
    @Test
    public void aCritOrAnUltimateRaisesNothing() {
        Fixture crit = new Fixture(1, true);
        Assertions.assertEquals(0.0, crit.hit(SkillCategory.NORMAL), 1e-9,
                "the effect needs a hit that did NOT crit");

        Fixture ultimate = new Fixture(1, false);
        Assertions.assertEquals(0.0, ultimate.hit(SkillCategory.ULTRA), 1e-9,
                "「普攻或战技」 - an ultimate is neither");
    }

    /**
     * 「每 3 回合可以触发 1 次」: a second non-crit hit inside the cooldown changes nothing.
     */
    @Test
    public void theCooldownStopsASecondGrant() {
        Fixture f = new Fixture(1, false);
        double first = f.hit(SkillCategory.NORMAL);
        double second = f.hit(SkillCategory.NORMAL);
        System.out.println("[23012] first=" + first + " second=" + second);
        Assertions.assertEquals(0.36, first, 1e-9, "the first hit grants");
        Assertions.assertEquals(0.0, second, 1e-9, "the next one is inside the 3-turn cooldown");
    }

    /**
     * The share is per rank (36 / 42 / 48 / 54 / 60%).
     */
    @Test
    public void theShareFollowsTheRank() {
        double[] expected = {0.36, 0.42, 0.48, 0.54, 0.6};
        for (int rank = 1; rank <= 5; rank++) {
            Fixture f = new Fixture(rank, false);
            Assertions.assertEquals(expected[rank - 1], f.hit(SkillCategory.NORMAL), 1e-9, "rank " + rank);
        }
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        /** @param crits whether the fixture's hits are forced to crit */
        private Fixture(int rank, boolean crits) {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
            DoubleValue chance = wearer.getAttribute(AttributeType.CRIT_CHANCE);
            // Force the roll: far below zero never crits, far above one always does.
            chance.addModifier(DoubleValue.Modifier.pure(crits ? 10 : -10));
            battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(ALLY, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
        }

        /**
         * Settles one hit of {@code category} and returns the wearer's CRIT_CHANCE delta.
         */
        private double hit(SkillCategory category) {
            double before = wearer.getAttribute(AttributeType.CRIT_CHANCE).get();
            Damage damage = new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 100, category);
            battle.applyDamage(enemy, damage);
            return wearer.getAttribute(AttributeType.CRIT_CHANCE).get() - before;
        }
    }
}
