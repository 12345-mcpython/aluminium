package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
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
 * 记忆的质料 (24002): 「如果装备者持有护盾，则使自身受到的伤害降低 #5%」.
 *
 * <p><b>What it needed.</b> The clause is a live shield check at <b>settlement</b> time: an instance
 * {@code MODIFY_DAMAGE_TAKEN} with a <b>negative</b> share, gated on {@code target == self} + {@code self has_shield}.
 * The interpreter's instance path now routes a negative share to the reduction zone (the same reading the sustained zone
 * arm already makes), instead of handing it to the vulnerability zone.
 *
 * <p>The other half of the cone (grant a shield when hit unshielded) is what this fixture uses to get a shield.
 */
public class ConeTwentyFourThousandTwoTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int CONE = 24002;

    /**
     * Without a shield the reduction is absent; with the cone's own shield it is the rank's #5 (S1: 12%).
     */
    @Test
    public void theReductionOnlyExistsWhileShielded() {
        Assertions.assertEquals(1.0, reduction(1, false), 1e-9,
                "no shield: the rule does not fire, the instance is untouched");
        Assertions.assertEquals(0.88, reduction(1, true), 1e-9,
                "shielded: 「受到的伤害降低 12%」");
    }

    /**
     * The reduction is per rank (12 / 15 / 18 / 21 / 24%).
     */
    @Test
    public void theReductionFollowsTheRank() {
        double[] expected = {0.12, 0.15, 0.18, 0.21, 0.24};
        for (int rank = 1; rank <= 5; rank++) {
            Assertions.assertEquals(1.0 - expected[rank - 1], reduction(rank, true), 1e-9,
                    "rank " + rank + ": #5 = " + expected[rank - 1]);
        }
    }

    // ==================================================================

    /**
     * Settles one instance on the wearer and returns the reduction zone's rate ({@code 1} = no reduction).
     */
    private static double reduction(int rank, boolean shieldFirst) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
        battle.startBattle();

        if (shieldFirst) {
            // A shield is all the gate asks for; granting one directly keeps the damage measurement about THIS clause.
            battle.grantShield(wearer, 500);
            Assertions.assertTrue(wearer.getShield() > 0, "the fixture must actually hold a shield");
        }

        Damage damage = new Damage(enemy, wearer, DamageElement.FIRE, DamageType.NORMAL, 1000);
        battle.applyDamage(wearer, damage);
        return damage.reductionArea().getRate();
    }
}



