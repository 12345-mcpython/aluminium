package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Signal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P4-4 acceptance: 击破的推条（25% 行动条）、跳回合、以及 2 回合后韧性恢复。
 *
 * <p>锚点：冰锋 @90/组1 速度 132 → 行动周期 {@code 10000/132 ≈ 75.76}。
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
        Assertions.assertEquals(before + cycle * 0.25, after, 1e-6, "推条 25% 行动周期");
        Assertions.assertEquals(Constant.BROKEN_REMAIN_TURNS, iceEdge.getBrokenRemainTurns());
    }

    @Test
    public void brokenEnemySkipsItsTurnsThenRecovers() {
        Character himeko = character("himeko");
        Enemy iceEdge = EnemyFactory.create(ICE_EDGE, 90, 1);
        Battle battle = new Battle(List.of(himeko), List.of(iceEdge), new Random(0));
        breakIt(battle, himeko, iceEdge);

        Assertions.assertTrue(battle.handleBrokenTurn(iceEdge), "第 1 个回合：跳过");
        Assertions.assertEquals(1, iceEdge.getBrokenRemainTurns());
        Assertions.assertTrue(iceEdge.isBroken());

        Assertions.assertTrue(battle.handleBrokenTurn(iceEdge), "第 2 个回合：跳过，并到点恢复");
        Assertions.assertEquals(0, iceEdge.getBrokenRemainTurns());
        Assertions.assertFalse(iceEdge.isBroken());
        Assertions.assertEquals(60, iceEdge.getStance(), EPS, "恢复 = 韧性回满");

        Assertions.assertFalse(battle.handleBrokenTurn(iceEdge), "已经恢复了，不跳过");
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

        Assertions.assertTrue(iceEdge.isBroken(), "韧性回满后可以再破一次");
        Assertions.assertEquals(energyBefore + 40 + 5, himeko.getCurrentEnergy(), EPS, "两次普攻 40 + 击破回能 5");
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

    /** 姬子两次 Fire 普攻（各削韧 30）把冰锋的 60 点韧性打空。 */
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
