package com.laosun.aluminium.test;

import com.laosun.aluminium.Queue;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Signal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * 行动条的同行动值裁决（P7 修正 E4）。
 *
 * <p>修之前 {@code Signal.compareTo} 只比 {@code nextActionTime}，相等时
 * {@code PriorityQueue} 的顺序**未定义** —— "两个同速单位谁先出手"变成碰运气，
 * 而且 {@code snapshot()} 是按堆数组稳定排序，**显示顺序可能与实际出手顺序不一致**。
 *
 * <p>修法：{@code Signal} 记一个全局递增的**排期序号**，{@code compareTo} 在行动值相等时比它
 * （先排期的先动）。
 */
public class QueueTieBreakTest {
    private static final double EPS = 1e-9;

    /**
     * 同速单位的出手顺序必须是**入场顺序**，而且可复现 —— 反复跑同一场战斗结果一致。
     *
     * <p>断言两件事：{@code peekNext()} 拿到第一个入场的；以及连续 {@code move()} 的顺序
     * 与入场顺序一致。修之前这几条会随堆内部状态变化而随机失败。
     */
    @Test
    public void equalSpeedsActInEntryOrder() {
        Character first = character("first", 100);
        Character second = character("second", 100);
        Character third = character("third", 100);
        Queue q = new Queue(List.of(first, second, third));

        Assertions.assertEquals(first, q.peekNext(), "先入场的先行动");

        Assertions.assertEquals(first, nextActor(q));
        Assertions.assertEquals(second, nextActor(q));
        Assertions.assertEquals(third, nextActor(q));
    }

    /**
     * 同一场战斗跑两遍必须得到完全相同的出手顺序（确定性）。
     *
     * <p>用"同速 + 多单位"把相等键堆满，让未定义顺序真的有机会暴露出来。
     */
    @Test
    public void equalSpeedOrderIsDeterministicAcrossRuns() {
        List<String> firstRun = runEqualSpeedBatch(8);
        List<String> secondRun = runEqualSpeedBatch(8);

        Assertions.assertEquals(firstRun, secondRun, "同一构造两次运行必须给出同一顺序");
        Assertions.assertEquals(
                List.of("c0", "c1", "c2", "c3", "c4", "c5", "c6", "c7").subList(0, 8),
                firstRun.subList(0, 8),
                "首轮就是入场顺序");
    }

    /**
     * {@code snapshot()} 的顺序必须**等于**实际出手顺序（E4 的第二个症状）。
     */
    @Test
    public void snapshotOrderMatchesActualTurnOrder() {
        Character a = character("a", 100);
        Character b = character("b", 100);
        Character c = character("c", 100);
        Queue q = new Queue(List.of(a, b, c));

        List<CanHit> displayed = q.snapshot().stream().map(Signal::getCanHit).toList();
        List<CanHit> actual = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            actual.add(nextActor(q));
        }

        Assertions.assertEquals(actual, displayed, "显示顺序必须等于出手顺序");
    }

    /**
     * 行动条操纵**不**重新取号：把一个单位拉到与前面的人同一行动值，他仍然排在后面。
     *
     * <p>这条是刻意定下的语义。如果拉条也换序号，"谁被拉条谁就先手"，
     * 会让 P7-2 额外回合 / P10-4 拉条的先后变得反直觉且难以预测。
     */
    @Test
    public void advancingToTheSameActionValueDoesNotJumpAhead() {
        Character a = character("A", 100);
        Character b = character("B", 100);
        Queue q = new Queue(List.of(a, b));

        long seqA = signalOf(q, a).getSequence();
        long seqB = signalOf(q, b).getSequence();
        Assertions.assertTrue(seqA < seqB, "A 先入场 → 序号更小");
        Assertions.assertEquals(a, q.peekNext(), "同行动值 150 → 排期更早的 A 先动");

        // 两人都在 150 行动过一轮，都被重新预约到 250（速度 100 → 周期 100）。
        // ⚠ 不能用 advanceAction(…, 1e9) 把某人"拉到最前"来制造对齐：clamp 会把他按到
        // elapsed 上，而 move() 又会把时钟拖到那里 —— 他会连动两次。
        Assertions.assertEquals(a, nextActor(q));
        Assertions.assertEquals(b, nextActor(q));
        Assertions.assertEquals(250, signalOf(q, a).getNextActionTime(), EPS);
        Assertions.assertEquals(250, signalOf(q, b).getNextActionTime(), EPS);
        Assertions.assertEquals(150, q.getElapsed(), EPS, "两人都在 150 行动，时钟就停在 150");

        // 现在把 A 拉后到 280、再把 B 也拉后到 280：两者同值。
        // 用 delayAction 而不是 advanceAction，避免把任何一方排到 elapsed 上。
        // 注意断言用的序号要取**行动之后**的：两人都刚重新预约过，序号已经换新。
        long seqAAfterActing = signalOf(q, a).getSequence();
        long seqBAfterActing = signalOf(q, b).getSequence();
        Assertions.assertTrue(seqAAfterActing < seqBAfterActing,
                "同一时刻行动时 A 先（A 的预约更早）→ 重新取号后 A 仍更小");

        q.delayAction(a, 30);
        q.delayAction(b, 30);

        Assertions.assertEquals(280, signalOf(q, a).getNextActionTime(), EPS);
        Assertions.assertEquals(280, signalOf(q, b).getNextActionTime(), EPS);
        Assertions.assertEquals(seqAAfterActing, signalOf(q, a).getSequence(), "推条不重新取号");
        Assertions.assertEquals(seqBAfterActing, signalOf(q, b).getSequence(), "推条不重新取号");
        Assertions.assertEquals(a, q.peekNext(), "同行动值 280 → 排期更早的 A 先动");

        Assertions.assertEquals(a, nextActor(q), "A 先手");
        Assertions.assertEquals(b, nextActor(q), "然后才是 B");
    }

    /**
     * 行动者被重新预约后拿到**新**序号，于是不会靠旧序号插到同级前面：
     * 行动者回到队尾，下一个同级的人接手。
     */
    @Test
    public void actorGoesToTheBackOfItsTierAfterActing() {
        Character a = character("A", 100);
        Character b = character("B", 100);
        Queue q = new Queue(List.of(a, b));

        long seqBefore = signalOf(q, a).getSequence();
        Assertions.assertEquals(a, nextActor(q));                 // A 行动并重新预约

        Assertions.assertTrue(signalOf(q, a).getSequence() > seqBefore,
                "行动后换新序号（排到同级末尾）");
        Assertions.assertEquals(b, q.peekNext(), "B 在 150，A 已到 250");
        Assertions.assertEquals(b, nextActor(q), "接下来是 B");
    }

    /**
     * 死亡移除之后，剩下的人仍然按序出手，不会因为堆重建而乱掉。
     */
    @Test
    public void removalKeepsDeterministicOrder() {
        Character a = character("a", 100);
        Character b = character("b", 100);
        Character c = character("c", 100);
        Queue q = new Queue(List.of(a, b, c));

        Assertions.assertTrue(q.removeCombatant(b));

        Assertions.assertEquals(a, q.peekNext());
        Assertions.assertEquals(a, nextActor(q));
        Assertions.assertEquals(c, nextActor(q), "b 被移除后轮到 c");
    }

    /**
     * 速度不同（行动值不同）时，裁决序号不该抢戏：还是行动值小的先动。
     *
     * <p>⚠ 这里只断言"谁先动"，**不**断言两个同值信号里谁先 ——
     * 那取决于两人的序号大小，而 fast 行动后会被重新取号（见
     * {@link #actorGoesToTheBackOfItsTierAfterActing}）。
     * 本条要证明的是：{@code compareTo} 永远以行动值当第一关键字。
     */
    @Test
    public void sequenceNeverOverridesTheActionValue() {
        Character slow = character("slow", 100);      // 首轮 150
        Character fast = character("fast", 200);      // 首轮 75
        Queue q = new Queue(List.of(slow, fast));

        Assertions.assertNotEquals(signalOf(q, slow).getSequence(), signalOf(q, fast).getSequence(),
                "两个信号必须拿到不同的序号");
        Assertions.assertEquals(fast, q.peekNext(), "fast 的行动值 75 < 150，先动");

        Assertions.assertEquals(fast, nextActor(q), "fast 先出手");
        Assertions.assertEquals(75, q.getElapsed(), EPS);
    }

    /**
     * 中途入场（召唤物 / P9-4）拿的是新序号，和同刻的旧单位比时排在后面。
     */
    @Test
    public void latecomerGetsAHigherSequence() {
        Character early = character("early", 100);
        Queue q = new Queue(List.of(early));

        Character late = character("late", 100);
        q.addCombatant(late);

        Assertions.assertTrue(signalOf(q, early).getSequence() < signalOf(q, late).getSequence(),
                "早入场的序号更小");
    }

    // ==================================================================

    /** 造 8 个同速单位，记录前 8 次出手的名字。 */
    private static List<String> runEqualSpeedBatch(int count) {
        List<CanHit> characters = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            characters.add(character("c" + i, 100));
        }
        Queue q = new Queue(characters);

        List<String> order = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            order.add(nextActor(q).getName());
        }
        return order;
    }

    /** {@code move()} + {@code setTopZero()}：消费掉一个回合并返回行动者。 */
    private static CanHit nextActor(Queue q) {
        q.move();
        CanHit actor = q.getCurrentActor().getCanHit();
        q.setTopZero();
        return actor;
    }

    private static Character character(String name, int speed) {
        return Character.fromAttributes(name, 10_000, 100, 100, speed);
    }

    private static Signal signalOf(Queue q, CanHit target) {
        List<Signal> matches = q.getHeap().stream()
                .filter(s -> s.getCanHit().equals(target))
                .toList();
        Assertions.assertEquals(1, matches.size(),
                target.getName() + " 在行动条里应当只出现一次，实际 " + matches.size() + " 次");
        return matches.getFirst();
    }
}
