package com.laosun.aluminium.test.content.lightcones;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.equipment.Weapon;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 勿忘她的火焰 (23050): 「进入战斗时，使装备者和另一位开战的队友造成的击破伤害提高 #2%，若不存在开战的队友则对装备者和
 * 击破特攻最高的队友生效」.
 *
 * <p><b>What it needed.</b> "Another teammate who <b>triggered combat</b>" is the teammate whose map attack started the
 * fight - the engine already records that declaration ({@code Battle.declareMapAttack}). The two new selectors read it:
 * {@code other_map_attackers} (all of them except the wearer) and, for the no-starter branch,
 * {@code highest_break_effect_ally}. {@code map_attacker_count} is the gate that tells the two branches apart.
 */
public class ConeTwentyThreeThousandFiftyTest {

    private static final int LEVEL = 80;
    private static final int WEARER = 1002;
    private static final int STARTER = 1204;   // lands the map attack in the first case
    private static final int HIGHEST = 1001;   // gets a break-effect boost, so the fallback can pick it
    private static final int CONE = 23050;

    /**
     * A teammate triggered combat: the wearer and <b>that</b> teammate get the share, nobody else.
     */
    @Test
    public void theTeammateWhoTriggeredCombatGetsTheShare() {
        Fixture f = new Fixture(1, STARTER);
        Assertions.assertEquals(0.32, f.boost(f.wearer), 1e-9, "the wearer's own share");
        Assertions.assertEquals(0.32, f.boost(f.starter), 1e-9, "「另一位开战的队友」");
        Assertions.assertEquals(0.0, f.boost(f.other), 1e-9, "a teammate who did not trigger combat gets nothing");
    }

    /**
     * Nobody triggered combat: the wearer and the highest-break-effect teammate get it.
     */
    @Test
    public void withoutAStarterTheHighestBreakTeammateGetsIt() {
        Fixture f = new Fixture(1, 0);
        Assertions.assertEquals(0.32, f.boost(f.wearer), 1e-9);
        Assertions.assertEquals(0.32, f.boost(f.highest), 1e-9,
                "「击破特攻最高的队友」 - 1001 carries the raised Break Effect in this fixture");
        Assertions.assertEquals(0.0, f.boost(f.starter), 1e-9, "the other teammate is not the highest");
    }

    /**
     * The wearer herself started the fight: no OTHER starter exists, so the fallback runs.
     */
    @Test
    public void theWearerStartingTheFightSelectsTheFallback() {
        Fixture f = new Fixture(1, WEARER);
        Assertions.assertEquals(0.32, f.boost(f.wearer), 1e-9);
        Assertions.assertEquals(0.32, f.boost(f.highest), 1e-9, "the fallback branch, not the starter branch");
        Assertions.assertEquals(0.0, f.boost(f.starter), 1e-9);
    }

    /**
     * The share is per rank (32 / 42 / 52 / 62 / 72%).
     */
    @Test
    public void theShareFollowsTheRank() {
        double[] expected = {0.32, 0.42, 0.52, 0.62, 0.72};
        for (int rank = 1; rank <= 5; rank++) {
            Fixture f = new Fixture(rank, STARTER);
            Assertions.assertEquals(expected[rank - 1], f.boost(f.wearer), 1e-9, "rank " + rank + " wearer");
            Assertions.assertEquals(expected[rank - 1], f.boost(f.starter), 1e-9, "rank " + rank + " starter");
        }
    }

    // ==================================================================

    private static final class Fixture {
        private final Character wearer;
        private final Character starter;
        private final Character highest;
        private final Character other;
        private final Enemy enemy = BattleFixture.monster();
        private final Battle battle;

        /**
         * @param rank         the superimposition rank
         * @param mapAttacker  which character declared the map attack, or {@code 0} for nobody
         */
        private Fixture(int rank, int mapAttacker) {
            wearer = CharacterFactory.create(WEARER, LEVEL, true, Weapon.build(CONE, LEVEL, false, rank));
            starter = CharacterFactory.create(STARTER, LEVEL);
            highest = CharacterFactory.create(HIGHEST, LEVEL);
            other = CharacterFactory.create(1003, LEVEL);
            highest.getAttribute(AttributeType.BREAKING_EFFECT).addModifier(DoubleValue.Modifier.pure(0.5));

            battle = new Battle(new ArrayList<>(List.of(wearer, starter, highest, other)),
                    new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
            if (mapAttacker == STARTER) {
                battle.declareMapAttack(starter, enemy);
            } else if (mapAttacker == WEARER) {
                battle.declareMapAttack(wearer, enemy);
            }
            battle.startBattle();
        }

        private double boost(CanHit unit) {
            DoubleValue share = unit.getAttribute(AttributeType.BREAK_DAMAGE_BOOST);
            return share == null ? 0.0 : share.get();
        }
    }
}
