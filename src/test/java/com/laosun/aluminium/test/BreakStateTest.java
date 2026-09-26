package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.Signal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P4-4 acceptance: break's delay/push-back (25% of the action bar), the skipped turn, and toughness
 * recovery after 2 turns.
 *
 * <p>Anchor: Ice Edge @90/group 1 has speed 132 → action cycle {@code 10000/132 ≈ 75.76}.
 */
public class BreakStateTest {
    private static final double EPS = 1e-6;
    private static final int ICE_EDGE = 1002011;

    @Test
    public void breakingDelaysTheEnemyByAQuarterOfItsCycle() {
        Character himeko = character("himeko");
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = new Battle(List.of(himeko), List.of(iceEdge), new Random(0));
        double cycle = 10000.0 / iceEdge.getAttribute(AttributeType.SPEED).get();
        double before = battle.queue.getTimeRemaining(signalOf(battle, iceEdge));

        breakIt(battle, himeko, iceEdge);

        double after = battle.queue.getTimeRemaining(signalOf(battle, iceEdge));
        Assertions.assertTrue(iceEdge.isBroken());
        Assertions.assertEquals(before + cycle * 0.25, after, 1e-6, "delay = 25% of the action cycle");
        Assertions.assertEquals(Constant.BROKEN_REMAIN_TURNS, iceEdge.getBrokenRemainTurns());
    }

    @Test
    public void brokenEnemySkipsItsTurnsThenRecovers() {
        Character himeko = character("himeko");
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = new Battle(List.of(himeko), List.of(iceEdge), new Random(0));
        breakIt(battle, himeko, iceEdge);

        Assertions.assertTrue(battle.handleBrokenTurn(iceEdge), "turn 1: skipped");
        Assertions.assertEquals(1, iceEdge.getBrokenRemainTurns());
        Assertions.assertTrue(iceEdge.isBroken());

        Assertions.assertTrue(battle.handleBrokenTurn(iceEdge), "turn 2: skipped, and it recovers right on schedule");
        Assertions.assertEquals(0, iceEdge.getBrokenRemainTurns());
        Assertions.assertFalse(iceEdge.isBroken());
        Assertions.assertEquals(60, iceEdge.getStance(), EPS, "recovery = toughness restored to full");

        Assertions.assertFalse(battle.handleBrokenTurn(iceEdge), "it has already recovered, no skip");
    }

    @Test
    public void recoveredEnemyCanBeBrokenAgain() {
        Character himeko = character("himeko");
        himeko.setMaxEnergy(120);
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = new Battle(List.of(himeko), List.of(iceEdge), new Random(0));

        breakIt(battle, himeko, iceEdge);
        battle.handleBrokenTurn(iceEdge);
        battle.handleBrokenTurn(iceEdge);
        Assertions.assertFalse(iceEdge.isBroken());

        double energyBefore = himeko.getCurrentEnergy();
        breakIt(battle, himeko, iceEdge);

        Assertions.assertTrue(iceEdge.isBroken(), "it can be broken once more after toughness is restored");
        Assertions.assertEquals(energyBefore + 40 + 5, himeko.getCurrentEnergy(), EPS, "two basic attacks 40 + break energy gain 5");
    }

    @Test
    public void delayMovePercentRejectsBadInput() {
        Character himeko = character("himeko");
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = new Battle(List.of(himeko), List.of(iceEdge), new Random(0));

        Assertions.assertFalse(battle.delayMovePercent(null, 0.25));
        Assertions.assertFalse(battle.delayMovePercent(iceEdge, 0));
        Assertions.assertFalse(battle.delayMovePercent(iceEdge, -1));
    }

    /** Himeko's two Fire basic attacks (30 toughness reduction each) empty Ice Edge's 60 toughness. */
    private static void breakIt(Battle battle, Character himeko, Enemy iceEdge) {
        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));
        battle.castImmediate(new DefaultSkill(1003, 1, 1), himeko, List.of(iceEdge));
    }

    private static Signal signalOf(Battle battle, Enemy enemy) {
        return battle.getQueueSnapshot().stream()
                .filter(s -> s.getCanHit() == enemy)
                .findFirst()
                .orElseThrow();
    }

    private static Character character(String name) {
        return Character.fromAttributes(name, 10_000, 100, 100, 100);
    }
}
