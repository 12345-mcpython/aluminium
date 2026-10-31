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
 * 愿旅途永远坦然 (21053): 「使装备者提供的护盾量提高 12%；<b>我方目标持有护盾时</b>，造成的伤害提高 12%」.
 *
 * <p><b>What the old note was stuck on.</b> It wanted a judge and stopped at 「BOOST_DAMAGE 要一次真实施放才读得到」 - a manual
 * {@code fire} throws 「没有实例」 because the op reads the instance being settled. So this test performs a REAL cast: the shielded
 * ally attacks, and the instance it settles carries the boost.
 */
public class ConeTwoThousandFiftyThreeTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1001;
    private static final int ALLY = 1204;

    private static final class Run {
        private final Character wearer;
        private final Character ally;
        private final Battle battle;
        private final Enemy enemy;
        private final double[] last = new double[1];

        Run(boolean withCone) {
            wearer = withCone
                    ? CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21053, LEVEL, false, 1))
                    : CharacterFactory.create(WEARER, LEVEL);
            ally = CharacterFactory.create(ALLY, LEVEL);
            enemy = BattleFixture.monster();
            battle = new Battle(new ArrayList<>(List.of(wearer, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
            // NORMAL only: a break instance is settled on the same attack and is an order of magnitude larger (measured:
            // the second hit broke the target and the last-settled value was 4843.99 against a 536.48 normal hit).
            battle.addDamageObserver((Damage damage, double applied, double hpLost, double shield, boolean died) -> {
                if (damage.getType() == com.laosun.aluminium.enums.DamageType.NORMAL) {
                    last[0] = damage.toValue();
                }
            });
        }

        double allyAttack() {
            battle.castImmediate(ally.getSkills().get(SkillType.COMMON), ally, List.of(enemy));
            battle.processRequests();
            return last[0];
        }

        void shieldTheAlly() {
            battle.grantShield(wearer, ally, 400, "judge");
        }
    }

    /**
     * The shielded ally hits harder - and only while it is shielded.
     */
    @Test
    public void aShieldedAllyDealsMoreDamage() {
        Run withCone = new Run(true);
        double bare = withCone.allyAttack();
        withCone.shieldTheAlly();
        double shielded = withCone.allyAttack();
        System.out.println("[21053] ally " + bare + " -> " + shielded + " (x" + (shielded / bare) + ")");
        Assertions.assertEquals(1.12, shielded / bare, 1e-3,
                "「我方目标持有护盾时，造成的伤害提高 12%」");

        Run noCone = new Run(false);
        double plainBare = noCone.allyAttack();
        noCone.shieldTheAlly();
        double plainShielded = noCone.allyAttack();
        Assertions.assertEquals(1.0, plainShielded / plainBare, 1e-6,
                "without the cone a shield changes nothing");
    }

    /**
     * And the shield half follows its own share.
     */
    @Test
    public void theShippedNumbersAreTheTwoShares() {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(21053, LEVEL, false, 1));
        Battle battle = new Battle(new ArrayList<>(List.of(wearer)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.seeded());
        battle.startBattle();
        Assertions.assertEquals(0.12, wearer.getAttribute(AttributeType.SHIELD_BOOST).get(), 1e-9,
                "「使装备者提供的护盾量提高 12%」");
    }
}
