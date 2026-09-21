package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Queue;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P7-1 验收：轮次制行动值。
 *
 * <pre>
 * 首轮总行动值 150，之后每轮 100      ⇒ 每个单位的**首个周期** = 10000/速度 × 1.5
 * </pre>
 *
 * <p>含义：速度 100 的单位首轮要等 150 才动（第二圈起每 100 动一次）；
 * 速度 200 的单位首轮等 75 —— 所以**首轮里高速单位能多动一次**
 * （速度 240 的周期是 41.67，首轮 150 之内能动 3 次）。
 */
public class QueueRoundTest {
    private static final double EPS = 1e-9;

    @Test
    public void firstRoundIsOneAndAHalfCycles() {
        Queue q = new Queue(List.of(character("speed100", 100)));

        Assertions.assertEquals(150, q.timeUntilNext(), EPS, "首轮：10000/100 × 1.5 = 150");
        Assertions.assertEquals(1, q.getRound(), "还没走，是第 1 轮");
    }

    @Test
    public void laterRoundsAreExactlyOneCycle() {
        Queue q = new Queue(List.of(character("speed100", 100)));

        Assertions.assertEquals(150, q.move(), EPS, "首轮 150");
        q.setTopZero();
        Assertions.assertEquals(100, q.move(), EPS, "第 2 轮起每轮 100");
        q.setTopZero();
        Assertions.assertEquals(100, q.move(), EPS);
        q.setTopZero();
        Assertions.assertEquals(100, q.move(), EPS);
    }

    @Test
    public void fastUnitActsBeforeTheFirstRoundEnds() {
        // 速度 200：首轮 75，所以 150 之内能动两次（t=75、t=125）
        Queue q = new Queue(List.of(character("speed200", 200)));

        Assertions.assertEquals(75, q.move(), EPS, "第一次行动在 75");
        q.setTopZero();
        Assertions.assertEquals(50, q.move(), EPS, "第二次在 125（75 + 50）");
        q.setTopZero();
        Assertions.assertEquals(50, q.move(), EPS, "第三次在 175 —— 已经进入第 2 轮");
    }

    @Test
    public void firstRoundMultiplierDoesNotChangeTheOrder() {
        // 所有人都 ×1.5 ⇒ 首轮顺序与纯速度顺序一致
        Character slow = character("slow", 100);
        Character mid = character("mid", 150);
        Character fast = character("fast", 200);
        Queue q = new Queue(List.of(slow, mid, fast));

        Assertions.assertEquals(fast, q.peekNext());
        Assertions.assertEquals(75, q.timeUntilNext(), EPS);
        q.move();
        q.setTopZero();
        Assertions.assertEquals(mid, q.peekNext(), "第二个是 150 速（周期 66.67）");
    }

    @Test
    public void roundCounterFollowsElapsedTime() {
        Queue q = new Queue(List.of(character("speed100", 100)));

        Assertions.assertEquals(1, q.getRound(), "elapsed = 0");
        q.move();                                    // elapsed = 150 → 首轮结束
        Assertions.assertEquals(1, q.getRound(), "elapsed = 150 仍算第 1 轮");
        q.setTopZero();

        q.move();                                    // elapsed = 250
        Assertions.assertEquals(2, q.getRound(), "elapsed = 250 → 第 2 轮");
        q.setTopZero();

        q.move();                                    // elapsed = 350
        Assertions.assertEquals(3, q.getRound(), "elapsed = 350 → 第 3 轮");
    }

    @Test
    public void battleExposesTheRound() {
        Character hero = Character.fromAttributes("hero", 10_000, 100, 100, 100);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);      // 速度 132 > 100，先动
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));

        Assertions.assertEquals(1, battle.getRound(), "开场是第 1 轮");
        battle.stepForward();
        Assertions.assertEquals(1, battle.getRound(), "冰锋 132 速首轮在 75.76 行动，仍是第 1 轮");
    }

    @Test
    public void midBattleJoinerIsNotStretched() {
        // P7-1 只作用于 initialize()：中途入场按正常周期（这是刻意的，见 addCombatant 的注释）
        Queue q = new Queue(List.of(character("speed100", 100)));
        q.move();
        q.setTopZero();                              // elapsed = 150

        Character joiner = character("joiner", 100);
        q.addCombatant(joiner);

        // joiner 排在 elapsed + 100 = 250；当前堆顶是 speed100（elapsed+100 = 250）——
        // 两者同值，谁先由堆决定，这里只断言"joiner 等的是 100 而不是 150"
        Assertions.assertEquals(100, q.getTimeRemaining(q.getHeap().stream()
                .filter(s -> s.getCanHit() == joiner).findFirst().orElseThrow()), EPS);
    }

    // ==================================================================

    private static Character character(String name, int speed) {
        return Character.fromAttributes(name, 10_000, 100, 100, speed);
    }
}
