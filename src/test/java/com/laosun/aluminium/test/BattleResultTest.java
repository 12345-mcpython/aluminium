package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P7-3 验收：胜负状态机。
 *
 * <pre>
 *   NOT_STARTED ──startBattle()──▶ RUNNING ──一方全灭──▶ WIN / LOSE（终态，不回退）
 * </pre>
 *
 * <p>要点：
 * <ul>
 *   <li>终态之后 {@code stepForward()} **不再推进**行动条；</li>
 *   <li>{@code NOT_STARTED} 时不判胜负（还没开场，谈不上输赢）；</li>
 *   <li>一边**空列表**也算"全灭"（被清光了）。</li>
 * </ul>
 */
public class BattleResultTest {
    private static final double EPS = 1e-9;

    @Test
    public void statusStartsAsNotStarted() {
        Battle battle = newBattle();

        Assertions.assertEquals(Battle.Status.NOT_STARTED, battle.getStatus());
        Assertions.assertFalse(battle.isOver(), "还没开场不算结束");
    }

    @Test
    public void startBattleMovesToRunning() {
        Battle battle = newBattle();

        battle.startBattle();

        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus());
        Assertions.assertFalse(battle.isOver());
    }

    /**
     * 敌人全灭 → WIN，且终态之后行动条不再前进。
     */
    @Test
    public void wipingOutTheEnemiesWinsAndStopsTheClock() {
        Battle battle = newBattle();
        battle.startBattle();

        battle.enemies.getFirst().takeDamage(999_999);
        battle.processRequests();                     // 公开入口：触发死亡清理 + 判定

        Assertions.assertEquals(Battle.Status.WIN, battle.getStatus());
        Assertions.assertTrue(battle.isOver());

        double elapsedBefore = battle.queue.getElapsed();
        battle.stepForward();
        battle.stepForward();

        Assertions.assertEquals(elapsedBefore, battle.queue.getElapsed(), EPS,
                "终态之后 stepForward() 不再推进时钟");
        Assertions.assertNull(battle.currentMove, "也没有人处于行动点");
    }

    /**
     * 我方全灭 → LOSE。
     */
    @Test
    public void wipingOutThePartyLoses() {
        Battle battle = newBattle();
        battle.startBattle();

        for (Character c : battle.characters) {
            c.takeDamage(999_999);
        }
        battle.processRequests();

        Assertions.assertEquals(Battle.Status.LOSE, battle.getStatus());
        Assertions.assertTrue(battle.isOver());
    }

    /**
     * 两边同时全灭 → LOSE（先判负后判胜）。
     *
     * <p>定这条口径是因为"同时"必须有个确定结果，不能随判定顺序摇摆。
     */
    @Test
    public void mutualDestructionIsALoss() {
        Battle battle = newBattle();
        battle.startBattle();

        for (Character c : battle.characters) {
            c.takeDamage(999_999);
        }
        battle.enemies.getFirst().takeDamage(999_999);
        battle.processRequests();

        Assertions.assertEquals(Battle.Status.LOSE, battle.getStatus());
    }

    /**
     * 开场前（{@code NOT_STARTED}）就算全员阵亡也不判负 —— 战斗还没开始。
     */
    @Test
    public void nothingIsJudgedBeforeTheBattleStarts() {
        Battle battle = newBattle();
        battle.enemies.getFirst().takeDamage(999_999);

        Assertions.assertEquals(Battle.Status.NOT_STARTED, battle.checkResult());
        Assertions.assertEquals(Battle.Status.NOT_STARTED, battle.getStatus());
    }

    /**
     * 终态不回退：已经判胜之后再把人打死也不会变成 LOSE。
     */
    @Test
    public void theResultIsFinal() {
        Battle battle = newBattle();
        battle.startBattle();

        battle.enemies.getFirst().takeDamage(999_999);
        battle.processRequests();
        Assertions.assertEquals(Battle.Status.WIN, battle.getStatus());

        for (Character c : battle.characters) {
            c.takeDamage(999_999);
        }
        battle.processRequests();

        Assertions.assertEquals(Battle.Status.WIN, battle.getStatus(), "胜负已定，不再改判");
    }

    /**
     * 空的一方也算全灭：没有敌人的战斗开场即胜。
     */
    @Test
    public void anEmptySideCountsAsWipedOut() {
        Character hero = character("hero", 100);
        Battle battle = new Battle(List.of(hero), List.of(), new Random(0));

        battle.startBattle();

        Assertions.assertEquals(Battle.Status.WIN, battle.getStatus());
    }

    /**
     * 打完一场之后状态是终态，再来一场互不影响（状态不是 static）。
     */
    @Test
    public void statusIsPerBattleInstance() {
        Battle first = newBattle();
        first.startBattle();
        first.enemies.getFirst().takeDamage(999_999);
        first.processRequests();
        Assertions.assertEquals(Battle.Status.WIN, first.getStatus());

        Battle second = newBattle();

        Assertions.assertEquals(Battle.Status.NOT_STARTED, second.getStatus(),
                "新战斗的状态不受上一场影响");
    }

    /**
     * 正常打一场：中途状态是 RUNNING，终态是 WIN。
     */
    @Test
    public void statusIsRunningWhileTheBattleGoesOn() {
        Battle battle = newBattle();
        battle.startBattle();

        battle.stepForward();
        battle.afterMove();

        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus(),
                "还没分出胜负就一直是 RUNNING");
        Assertions.assertFalse(battle.isOver());
    }

    // ==================================================================

    private static Battle newBattle() {
        return new Battle(List.of(character("hero", 100)),
                List.of(EnemyFactory.create(1002011, 90, 1)), new Random(0));
    }

    private static Character character(String name, int speed) {
        return Character.fromAttributes(name, 10_000, 100, 100, speed);
    }
}
