package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 孤独的疗愈 (24003): 「陷入装备者施加的持续伤害效果的敌方目标被消灭时，装备者恢复 #4 点能量」.
 *
 * <p><b>What it needed.</b> Nothing new: {@code target_dots_from_self} counts the DOTs on the event's target whose source is
 * the rule's owner, and {@code KILL} fires while the victim's buffs are still on it. The 2026-10-19 record ("the
 * ownership cannot be asked") was stale - the variable landed for 23029.
 *
 * <p>The fixture measures the <b>difference</b> between a kill whose DOT is the wearer's and one whose DOT is an ally's,
 * because every kill pays its own base energy; the difference is exactly this clause's share.
 */
public class ConeTwentyFourThousandThreeTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int ALLY = 1001;
    private static final int CONE = 24003;

    /**
     * 「我的 DoT」 pays the rank's energy; 「队友的 DoT」 pays nothing extra.
     */
    @Test
    public void onlyTheWearersOwnDotPaysOnTheKill() {
        double mine = killEnergy(1, true);
        double theirs = killEnergy(1, false);
        Assertions.assertEquals(4.0, mine - theirs, 1e-6,
                "S1 #4 = 4: the extra energy is paid only for the wearer's own DOT");
    }

    /**
     * The amount is per rank (4 / 4.5 / 5 / 5.5 / 6), not a fixed 4.
     */
    @Test
    public void theEnergyFollowsTheRank() {
        double[] expected = {4.0, 4.5, 5.0, 5.5, 6.0};
        for (int rank = 1; rank <= 5; rank++) {
            double mine = killEnergy(rank, true);
            double theirs = killEnergy(rank, false);
            Assertions.assertEquals(expected[rank - 1], mine - theirs, 1e-6,
                    "rank " + rank + ": #4 = " + expected[rank - 1]);
        }
    }

    /**
     * The other half: the ultimate's DOT boost is per rank too (0.24 / 0.30 / 0.36 / 0.42 / 0.48).
     */
    @Test
    public void theUltimateRaisesDotDamagePerRank() {
        double[] expected = {0.24, 0.30, 0.36, 0.42, 0.48};
        for (int rank = 1; rank <= 5; rank++) {
            Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
            Enemy enemy = BattleFixture.monster();
            Battle battle = new Battle(new ArrayList<>(List.of(wearer, CharacterFactory.create(ALLY, LEVEL))),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            battle.startBattle();
            battle.castImmediate(wearer.getSkills().get(SkillType.ULTRA), wearer, List.of(enemy));
            battle.processRequests();
            Assertions.assertEquals(expected[rank - 1],
                    wearer.getAttribute(AttributeType.DOT_DAMAGE_BOOST).get(), 1e-9,
                    "rank " + rank + ": #2 = " + expected[rank - 1]);
        }
    }

    // ==================================================================

    /**
     * Kills one enemy with the wearer's attack after a DOT from the wearer (or the ally) and returns the wearer's energy
     * gain from the kill.
     */
    private static double killEnergy(int rank, boolean ownDot) {
        Character wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(wearer, ally)), new ArrayList<>(List.of(enemy)),
                BattleFixture.seeded());
        battle.startBattle();

        CanHit source = ownDot ? wearer : ally;
        enemy.getBuffManager().addBuff(new DotBuff(source, DamageElement.FIRE, 100, 2));
        wearer.setCurrentEnergy(0);

        battle.applyDamage(enemy, new Damage(wearer, enemy, DamageElement.FIRE, DamageType.NORMAL, 1_000_000));
        battle.processRequests();
        Assertions.assertTrue(enemy.isDeath(), "the fixture's enemy must die");
        return wearer.getCurrentEnergy();
    }
}
