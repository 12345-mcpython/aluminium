package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.battle.BattleDriver;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * A broken enemy must skip its turn on the <b>driven</b> path too.
 *
 * <h2>The bug this pins</h2>
 * {@code Battle.handleBrokenTurn} is documented as "the caller asks", and it does two things only it does: it ticks
 * the broken countdown, and it reports "so this turn is skipped". The hand-written demo is that caller -- its
 * {@code enemyTurn} opens with the call and returns early on {@code true}.
 *
 * <p>{@code BattleDriver} never called it. So on the driven path a broken enemy <b>acted anyway</b> and its broken
 * state <b>never expired by turn</b>: the entire reward for emptying a toughness bar did not exist. This is the third
 * defect of one shape found in this stretch -- a rule only a caller can apply is a rule some caller will not, after
 * the free ultimate and the double skill-point charge.
 *
 * <p>It also hid the party's real strength: with it fixed, the same three-on-three at 30000 HP per enemy goes from
 * 0 wins in 8 to 8 in 8.
 */
public class DrivenBrokenTurnTest {

    private static final int HIMEKO = 1003;
    private static final int LEVEL = 80;
    /** 冰锋 1002011: toughness 60, weak to FIRE -- 姬子's element. */
    private static final int ICE_EDGE = 1002011;
    /** Enough steps to walk past the hero's turns and into the enemy's, whatever the speeds. */
    private static final int STEPS = 12;

    /**
     * A broken enemy stops acting, and its countdown is ticked, on the driver's own path.
     *
     * <p>The two assertions are deliberately independent: <b>dealing no damage</b> is the skip, and <b>the countdown
     * moving</b> is the tick. An implementation that only did one of them would still leave the break reward broken,
     * in one case permanently (a countdown that never moves means an enemy that never recovers).
     */
    @Test
    public void aBrokenEnemySkipsItsTurnOnTheDrivenPath() {
        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        Enemy foe = bigEnemy();

        BattleDriver driver = BattleDriver.of(List.of(himeko), List.of(foe), 1L);
        driver.battle().startBattle();

        // Break it by hand: this case is about the broken RULE, not about how a break is achieved. The countdown has
        // to be set too, because that is what the engine does at the moment toughness reaches zero
        // (Battle.reduceToughness: breakEnemy + setBrokenRemainTurns(BROKEN_REMAIN_TURNS)).
        foe.breakEnemy(com.laosun.aluminium.enums.DamageElement.FIRE);
        foe.setBrokenRemainTurns(com.laosun.aluminium.Constant.BROKEN_REMAIN_TURNS);
        Assertions.assertTrue(foe.isBroken(), "precondition: the enemy is broken");
        int remainBefore = foe.getBrokenRemainTurns();
        double heroHpBefore = himeko.getCurrentHp();

        for (int i = 0; i < STEPS && !driver.battle().isOver(); i++) {
            driver.step();
            if (foe.getBrokenRemainTurns() != remainBefore) {
                break;              // the enemy's turn has now been handled
            }
        }

        Assertions.assertTrue(foe.getBrokenRemainTurns() < remainBefore,
                "the driven path must tick the broken countdown; it stayed at " + remainBefore
                        + " because only the hand-written demo ever called handleBrokenTurn");
        Assertions.assertEquals(heroHpBefore, himeko.getCurrentHp(), 0.001,
                "a broken enemy deals no damage while it skips -- before the fix it attacked as usual");
    }

    /**
     * An enemy that cannot die quickly, so the case cannot end before the assertions.
     *
     * @return the enemy
     */
    private static Enemy bigEnemy() {
        Enemy e = EnemyFactory.create(ICE_EDGE, 90, 1);
        e.setAttribute(AttributeType.HEALTH, new DoubleValue(500_000));
        e.heal(500_000);
        e.setMaxEnergy(0);
        return e;
    }
}
