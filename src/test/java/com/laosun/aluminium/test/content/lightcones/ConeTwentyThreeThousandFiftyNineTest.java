package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 灼尽炼狱的新骸 (23059): 「【炼狱】状态下目标受到的暴击伤害提高 #4%，受到来自装备者的暴击伤害额外提高 #5%」.
 *
 * <p><b>What it needed.</b> Nothing new: an instance {@code MODIFY_ATTR CRIT_ATTACK} adds to that hit's
 * {@code extraCritDamage}, and {@code CritArea.rate()} only reads it when the hit <b>crits</b> - so "crit damage taken"
 * needs no new event, keyword or zone. The first rule has no attacker gate (anyone's crit on a 【炼狱】 target), the second
 * adds the wearer's own share on top ({@code actor == self}); instance shares accumulate, so the two add.
 */
public class ConeTwentyThreeThousandFiftyNineTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1001;
    private static final int CONE = 23059;

    /**
     * The wearer gets #4 + #5 on a 【炼狱】 target, another attacker gets #4 only, and nobody gets anything without the state.
     */
    @Test
    public void thePurgatorySharesApply() {
        Fixture f = new Fixture(1, true);
        Assertions.assertEquals(0.6, f.extraCritDamage(f.wearer), 1e-9,
                "the wearer: 30% from the state + 30% from herself");
        Assertions.assertEquals(0.3, f.extraCritDamage(f.ally), 1e-9,
                "an ally: only the state's 30%");

        Fixture bare = new Fixture(1, false);
        Assertions.assertEquals(0.0, bare.extraCritDamage(bare.wearer), 1e-9,
                "no 【炼狱】: nothing");
    }

    /**
     * The share only matters when the hit crits: a non-crit instance keeps a 1.0 multiplier, a forced crit reads it.
     */
    @Test
    public void theShareOnlyCountsOnACrit() {
        Fixture nonCrit = new Fixture(1, true);
        nonCrit.hit(nonCrit.wearer, true);
        Assertions.assertEquals(2.1, nonCrit.critRate, 1e-9,
                "a crit multiplies by 1 + (0.5 base crit damage + 0.6) - the【炼狱】shares are inside the crit");

        Fixture flat = new Fixture(1, true);
        flat.hit(flat.wearer, false);
        Assertions.assertEquals(1.0, flat.critRate, 1e-9,
                "a hit that did not crit is untouched: the same shares wait inside the crit multiplier");
    }

    /**
     * Both shares are per rank (30 / 37.5 / 45 / 52.5 / 60%).
     */
    @Test
    public void theSharesFollowTheRank() {
        double[] expected = {0.3, 0.375, 0.45, 0.525, 0.6};
        for (int rank = 1; rank <= 5; rank++) {
            Fixture f = new Fixture(rank, true);
            Assertions.assertEquals(2 * expected[rank - 1], f.extraCritDamage(f.wearer), 1e-9,
                    "rank " + rank + " wearer");
            Assertions.assertEquals(expected[rank - 1], f.extraCritDamage(f.ally), 1e-9, "rank " + rank + " ally");
        }
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Character ally;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;
        /** The crit multiplier of the last settled hit (read after settlement). */
        private double critRate;

        private Fixture(int rank, boolean purgatory) {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
            ally = CharacterFactory.create(ALLY, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(wearer, ally)),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
            if (purgatory) {
                enemy.getBuffManager().addBuff(new StateBuff("炼狱", 2));
            }
        }

        /** Settles one hit from {@code attacker} and returns the crit-damage term it collected. */
        private double extraCritDamage(Character attacker) {
            boolean crit = attacker.getAttribute(AttributeType.CRIT_CHANCE).get() == 0;
            Damage damage = new Damage(attacker, enemy, DamageElement.FIRE, DamageType.NORMAL, 100);
            battle.applyDamage(enemy, damage);
            critRate = damage.critArea().getRate();
            return damage.getExtraCritDamage();
        }

        /** Settles one forced-crit or forced-non-crit hit, keeping its crit multiplier. */
        private void hit(Character attacker, boolean crit) {
            DoubleValue chance = attacker.getAttribute(AttributeType.CRIT_CHANCE);
            chance.addModifier(DoubleValue.Modifier.pure(crit ? 10 : -10));
            Damage damage = new Damage(attacker, enemy, DamageElement.FIRE, DamageType.NORMAL, 100);
            battle.applyDamage(enemy, damage);
            critRate = damage.critArea().getRate();
        }
    }
}



