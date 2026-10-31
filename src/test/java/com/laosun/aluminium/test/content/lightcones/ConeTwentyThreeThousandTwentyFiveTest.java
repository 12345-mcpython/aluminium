package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 梦应归于何处 (23025): 「【溃败】状态下目标受到装备者造成的击破伤害提高 #2%，速度降低 #3%，同类效果无法叠加」.
 *
 * <p><b>What it needed.</b> Two scopes at once on a damage-taken zone: the damage TYPE ({@code damage_type: BREAK}) and
 * the ATTACKER ({@code from_self: true}, new). Break damage ignores the ordinary damage-bonus zone, but <b>not</b> the
 * vulnerability zone - so a break vulnerability is the right channel, and this test pins the three scopes separately:
 * break from the wearer is raised, break from an ally is not, ordinary damage from the wearer is not.
 */
public class ConeTwentyThreeThousandTwentyFiveTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1001;
    private static final int CONE = 23025;

    /**
     * The wearer's break is raised by #2; anything else passes untouched.
     */
    @Test
    public void onlyTheWearersBreakIsRaised() {
        Fixture f = new Fixture(1);
        Assertions.assertEquals(1.0, f.breakRate(f.wearer), 1e-9, "no 【溃败】 yet: nothing to raise");

        f.breakEventFromWearer();
        Assertions.assertEquals(1.24, f.breakRate(f.wearer), 1e-9,
                "「受到装备者造成的击破伤害提高 24%」");
        Assertions.assertEquals(1.0, f.breakRate(f.ally), 1e-9, "an ally's break is not the wearer's");
        Assertions.assertEquals(1.0, f.rate(f.wearer, DamageType.NORMAL), 1e-9,
                "the same wearer's ordinary damage is not break damage");
    }

    /**
     * The share is per rank (24 / 28 / 32 / 36 / 40%) and does not stack on a second break.
     */
    @Test
    public void theShareFollowsTheRankAndDoesNotStack() {
        double[] expected = {0.24, 0.28, 0.32, 0.36, 0.4};
        for (int rank = 1; rank <= 5; rank++) {
            Fixture f = new Fixture(rank);
            f.breakEventFromWearer();
            f.breakEventFromWearer();
            Assertions.assertEquals(1.0 + expected[rank - 1], f.breakRate(f.wearer), 1e-9,
                    "rank " + rank + ": #2, after two breaks (同类效果无法叠加)");
        }
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        private Fixture(int rank) {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
            ally = CharacterFactory.create(ALLY, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(wearer, ally)), new ArrayList<>(List.of(enemy)),
                    BattleFixture.seeded());
            battle.startBattle();
        }

        /** 「当装备者对敌方目标造成击破伤害时」: the BREAK event with the wearer as its actor. */
        private void breakEventFromWearer() {
            battle.fire(TriggerPayload.Simple.of(TriggerEvent.BREAK, wearer, enemy, 0, 0));
        }

        private double breakRate(CanHit attacker) {
            return rate(attacker, DamageType.BREAK);
        }

        private double rate(CanHit attacker, DamageType type) {
            Damage damage = new Damage(attacker, enemy, DamageElement.FIRE, type, 1000);
            battle.applyDamage(enemy, damage);
            return damage.vulnerableArea().getRate();
        }
    }
}
