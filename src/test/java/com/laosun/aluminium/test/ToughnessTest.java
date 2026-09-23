package com.laosun.aluminium.test;

import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * P4-1 acceptance: an enemy's toughness / broken state.
 *
 * <p>Anchor: Ice Edge 1002011 @ group 1 · Lv90 → toughness 60 (template 60 × level group 1).
 * This task only builds the state machine, and **reaching zero does not break automatically**
 * (the break judgement is in P4-2); the broken duration / turn skipping is in P4-4.
 */
public class ToughnessTest {
    private static final double EPS = 1e-6;
    private static final int ICE_EDGE = 1002011;

    @Test
    public void toughnessIsCarriedOverFromData() {
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);

        Assertions.assertEquals(60, iceEdge.getMaxStance(), EPS);
        Assertions.assertEquals(60, iceEdge.getStance(), EPS);
        Assertions.assertTrue(iceEdge.hasToughnessBar());
        Assertions.assertFalse(iceEdge.isBroken());
        Assertions.assertNull(iceEdge.getBrokenElement());
        Assertions.assertEquals(0, iceEdge.getBrokenRemainTurns());
    }

    @Test
    public void reduceStanceClampsAtZeroAndNeverBreaksByItself() {
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);

        iceEdge.reduceStance(30);
        Assertions.assertEquals(30, iceEdge.getStance(), EPS);

        iceEdge.reduceStance(30);
        Assertions.assertEquals(0, iceEdge.getStance(), EPS);
        Assertions.assertFalse(iceEdge.isBroken(), "toughness reaching zero is not the same as a break: that judgement is in P4-2");

        iceEdge.reduceStance(10);
        Assertions.assertEquals(0, iceEdge.getStance(), EPS, "already 0, it will not go negative");
    }

    @Test
    public void breakEnemyMarksStateAndRecoverFillsTheBarBack() {
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);

        iceEdge.breakEnemy(DamageElement.FIRE);
        Assertions.assertTrue(iceEdge.isBroken());
        Assertions.assertEquals(DamageElement.FIRE, iceEdge.getBrokenElement());
        Assertions.assertEquals(0, iceEdge.getStance(), EPS);

        iceEdge.recoverFromBroken();
        Assertions.assertFalse(iceEdge.isBroken());
        Assertions.assertNull(iceEdge.getBrokenElement());
        Assertions.assertEquals(0, iceEdge.getBrokenRemainTurns());
        Assertions.assertEquals(60, iceEdge.getStance(), EPS, "recovery = toughness refilled");
    }

    @Test
    public void brokenEnemyIgnoresFurtherToughnessReduction() {
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        iceEdge.breakEnemy(DamageElement.ICE);

        iceEdge.reduceStance(30);

        Assertions.assertEquals(0, iceEdge.getStance(), EPS, "during a break the toughness bar is empty");
        Assertions.assertTrue(iceEdge.isBroken());
    }

    @Test
    public void nonPositiveReductionIsIgnored() {
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);

        iceEdge.reduceStance(0);
        iceEdge.reduceStance(-5);

        Assertions.assertEquals(60, iceEdge.getStance(), EPS);
    }

    @Test
    public void enemyWithoutToughnessBarStaysAtZero() {
        Enemy noBar = Enemy.fromAttributes("dummy", 1000, 100, 100, 100);   // no stance data

        Assertions.assertFalse(noBar.hasToughnessBar());
        noBar.reduceStance(30);
        Assertions.assertEquals(0, noBar.getStance(), EPS);
        Assertions.assertFalse(noBar.isBroken());

        noBar.breakEnemy(DamageElement.PHYSICAL);
        noBar.recoverFromBroken();
        Assertions.assertEquals(0, noBar.getStance(), EPS, "recovery merely returns it to 0");
    }
}
