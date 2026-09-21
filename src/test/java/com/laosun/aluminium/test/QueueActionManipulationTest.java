package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Queue;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.models.buffs.SpeedBoostBuff;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 行动条的三处修正（P7 前置）：
 * <ol>
 *   <li><b>E1</b>：{@code setTopZero()} 重置的是 {@code currentActor}，不是堆顶 ——
 *       遇到"行动期间把别人拉到最前"时不会错轴；{@code currentActor == null} 时什么都不做。</li>
 *   <li><b>E2</b>：速度变化会**立刻**重排行动时间（按已积累的进度比例换算），
 *       而不是等他下一次排周期才生效。</li>
 *   <li><b>E3</b>：{@code advanceActionByPercent} 做 clamp、{@code move()} 保护时钟不倒退 ——
 *       否则行动者会连动两次。</li>
 * </ol>
 */
public class QueueActionManipulationTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // E1：setTopZero 认人
    // ==================================================================

    /**
     * 行动期间"把别人拉到最前"（模拟 P7-2 额外回合 / P10-4 拉条）：
     * 此时堆顶已经不是行动者了，{@code setTopZero()} 必须仍然重置**行动者**。
     *
     * <p>修之前的行为：重置堆顶（= 被拉上来的 B），A 的周期没重置 → A 会连动两次。
     *
     * <p>⚠ 这里刻意让 A 与 B 的时间**不相等**（250 vs 187.5）：相等键的堆内顺序
     * 未定义（见 P7 修正 E4，那条还没修），断言"谁是堆顶"会变成碰运气。
     * "时间恰好相等"的那种情况由 {@link #actorIsNotSkippedWhenAnotherSignalSitsInThePast} 覆盖，
     * 那条只断言行动者被重置、不断言堆顶身份。
     */
    @Test
    public void setTopZeroResetsTheActorEvenWhenSomeoneElseWasPulledAhead() {
        Character a = character("A", 100);               // 先动（150）
        Character b = character("B", 80);                // 周期 125 → 首轮 187.5
        Queue q = new Queue(List.of(a, b));

        q.move();                                        // A 行动，elapsed = 150
        Assertions.assertEquals(a, q.getCurrentActor().getCanHit());

        // 把 A 推后（推条）：A 250，B 187.5 → 堆顶变成 B，而"正在行动的人"仍是 A。
        q.delayAction(a, 100);
        Assertions.assertEquals(187.5, signalOf(q, b).getNextActionTime(), EPS);
        Assertions.assertEquals(b, q.peekNext(), "现在堆顶是 B，不是行动者 A");

        q.setTopZero();                                  // 结束 A 的回合

        Assertions.assertEquals(250, signalOf(q, a).getNextActionTime(), EPS,
                "A（行动者）的周期被重置：elapsed + 100");
        Assertions.assertEquals(187.5, signalOf(q, b).getNextActionTime(), EPS,
                "B 不受影响（他仍是 187.5，下一个该他动）");
        Assertions.assertNull(q.getCurrentActor(), "行动者已清空");

        q.move();
        Assertions.assertEquals(b, q.getCurrentActor().getCanHit(), "接着动的是 B");
        Assertions.assertEquals(187.5, q.getElapsed(), EPS);
    }

    /**
     * 没调 {@code move()} 就调 {@code setTopZero()}：什么都不做。
     *
     * <p>修之前会静默把堆顶推后一整个周期 —— 等于"跳过一个人的回合"，是更坏的失败方式。
     */
    @Test
    public void setTopZeroWithoutAMoveDoesNothing() {
        Character a = character("A", 100);
        Queue q = new Queue(List.of(a));
        double before = signalOf(q, a).getNextActionTime();

        q.setTopZero();

        Assertions.assertEquals(before, signalOf(q, a).getNextActionTime(), EPS,
                "没有正在行动的人 → 一个字节都不该改");
    }

    // ==================================================================
    // E2：速度变化立刻重排
    // ==================================================================

    /**
     * 中途加速：按**已积累进度**换算剩余等待。
     *
     * <p>速度 100 → 周期 100，首轮 next = 150（进度账本 = 1/1.5 = 2/3）。
     * 队里的敌人 132 速（首轮 113.64）先动，跑两次 {@code move()} 后 elapsed = 150，
     * hero 刚行动完（next = 250、进度归 0）。
     *
     * <p>此时把速度翻倍到 200（周期 50）：新 next = elapsed + (1 - 0) × 50 = 200。
     */
    @Test
    public void speedChangeReSchedulesTheSignalImmediately() {
        Character hero = character("hero", 100);
        Battle battle = new Battle(List.of(hero), List.of(dummy()), new Random(0));
        Queue q = battle.queue;

        // 敌人 132 速 → 首轮 10000/132 × 1.5；比 hero 的 150 早，所以他先动
        Assertions.assertEquals(10000.0 / 132 * 1.5, signalOf(q, dummyOf(battle)).getNextActionTime(), 1e-6);

        q.move();                                        // 敌人先动
        Assertions.assertNotEquals(hero, q.getCurrentActor().getCanHit());
        q.setTopZero();
        q.move();                                        // 现在轮到 hero，elapsed = 150
        Assertions.assertEquals(hero, q.getCurrentActor().getCanHit());
        q.setTopZero();                                  // hero → next = 250

        Signal heroSignal = signalOf(q, hero);
        Assertions.assertEquals(250, heroSignal.getNextActionTime(), EPS);

        hero.getBuffManager().addBuff(new SpeedBoostBuff(2, 1.0));   // 速度 100 → 200

        Assertions.assertEquals(200, heroSignal.getNextActionTime(), EPS,
                "立刻重排：150 + (1 - 0) × 50 = 200（而不是等下一次排周期）");
        Assertions.assertEquals(200, hero.getAttribute(AttributeType.SPEED).get(), EPS,
                "面板也确实变成了 200");
    }

    /**
     * 移除加速后同样立刻回退到原周期。
     *
     * <p>hero 速度 200 → 首轮 75，比 132 速敌人的 113.64 早，所以这次确实是 hero 先动。
     */
    @Test
    public void removingTheSpeedBuffReSchedulesBack() {
        Character hero = character("hero", 100);
        Battle battle = new Battle(List.of(hero), List.of(dummy()), new Random(0));
        Queue q = battle.queue;

        SpeedBoostBuff buff = new SpeedBoostBuff(2, 1.0);
        hero.getBuffManager().addBuff(buff);             // hero 速度 200 → 周期 50、首轮 75
        q.move();                                        // hero 先动（75 < 敌人 113.64）
        Assertions.assertEquals(hero, q.getCurrentActor().getCanHit());
        q.setTopZero();                                  // hero → 75 + 50 = 125

        Assertions.assertEquals(125, signalOf(q, hero).getNextActionTime(), EPS);

        hero.getBuffManager().removeBuff(buff);          // 速度回到 100

        // 刚行动完（进度 0）→ 新 next = 75 + (1 - 0) × 100 = 175
        Assertions.assertEquals(175, signalOf(q, hero).getNextActionTime(), EPS,
                "移除加速也立刻生效");
    }

    /**
     * 直接改属性（不走 buff）也会触发重排 —— 触发点在 {@code CanHit.setAttribute}。
     *
     * <p>这里断言的是 **首轮系数被保留**：速度 100 的单位首轮预约长度是
     * {@code 100 × 1.5 = 150}，也就是行动条上还剩 150 格。把速度改成 200（周期 50）后，
     * 剩下那 150 格按新速度走：{@code 150 / 200 × 10000 = 75}。
     *
     * <p>错误实现会算出别的值：用 {@code cycleTime()}（100）当分母反推进度会得到 1.5，
     * clamp 成 1 之后变成"立刻行动"（0）；直接丢掉首轮系数会算出 50。
     */
    @Test
    public void settingTheSpeedAttributeDirectlyAlsoReSchedules() {
        Character hero = character("hero", 100);
        Battle battle = new Battle(List.of(hero), List.of(dummy()), new Random(0));
        Queue q = battle.queue;
        Signal signal = signalOf(q, hero);

        Assertions.assertEquals(150, signal.getNextActionTime(), EPS, "改动前：首轮 150");
        Assertions.assertEquals(150, signal.getRemaining(), EPS, "行动条上还剩 150 格");

        hero.setAttribute(AttributeType.SPEED, new DoubleValue(200));

        Assertions.assertEquals(75, signal.getNextActionTime(), EPS,
                "剩余 150 格按速度 200 走：150 / 200 × 10000 = 75");
    }

    // ==================================================================
    // E3：clamp，行动者不会连动两次
    // ==================================================================

    /**
     * {@code advanceActionByPercent(…, 1.0)} 刚好把行动时间压到 {@code elapsed}：
     * 时钟不会倒退、行动者也不会因为"落在过去"而被 {@code move()} 反复消费。
     */
    @Test
    public void advanceByPercentNeverGoesBelowElapsed() {
        Character a = character("A", 100);
        Queue q = new Queue(List.of(a));

        q.move();                                        // elapsed = 150
        q.setTopZero();                                  // A → 250
        q.advanceActionByPercent(a, 1.0);                // 100%：应该刚好到 elapsed

        Assertions.assertEquals(150, signalOf(q, a).getNextActionTime(), EPS, "clamp 到 elapsed");

        Assertions.assertEquals(0, q.move(), EPS, "时钟已经在 150，不倒退也不前进");
        Assertions.assertEquals(a, q.getCurrentActor().getCanHit(), "A 立刻再动");
        Assertions.assertEquals(150, q.getElapsed(), EPS, "时钟不倒退");
    }

    /**
     * clamp 的**必要性**：把一个已经落在 {@code elapsed} 之前（哪怕只差一个 ulp）的信号
     * 再按比例拉条时，{@code remaining} 必须取 max 到 0，否则"差额"是负数、拉条会把他
     * 推得**更靠过去**，接着 {@link Queue#move()} 就会把全局时钟往回拨。
     *
     * <p>这条不是空想：binary64 下 {@code a - (a-e)·p ≥ e} 数学上成立但浮点上不保证，
     * 而且行动条操纵（P10-4 拉条 / P7-2 额外回合）本来就会把信号排到 {@code elapsed} 上，
     * 后续再叠加一次拉条就会踩到这里。
     *
     * <p>构造方式：从堆里取出信号引用（{@code Queue} 的"剩余距离"账本只在
     * move/setTopZero/refreshSpeed 时同步，所以直接改 {@code nextActionTime} 不会破坏本测试
     * 要验证的逻辑 —— {@code advanceActionByPercent} 只读 {@code nextActionTime}）。
     */
    @Test
    public void advanceByPercentClampsASignalThatIsAlreadyInThePast() {
        Character a = character("A", 100);
        Queue q = new Queue(List.of(a));
        Signal signal = signalOf(q, a);

        signal.setNextActionTime(q.getElapsed() - 1e-7);   // 已经落在过去

        q.advanceActionByPercent(a, 0.5);

        Assertions.assertEquals(0, signal.getNextActionTime(), EPS,
                "clamp 到 elapsed(0)，而不是被推得更靠过去");

        Assertions.assertEquals(0, q.move(), EPS, "时钟不倒退");
        Assertions.assertEquals(a, q.getCurrentActor().getCanHit(), "他立刻行动");
    }

    /**
     * 核心断言：把**另一个人**拉到行动点上（与 {@code elapsed} 同值）之后，
     * {@code setTopZero()} 仍然只重置刚行动的那个，且他不会连动。
     *
     * <p>此时两人的 {@code nextActionTime} 都是 150 —— 相等键的堆内顺序未定义（P7 修正 E4），
     * 所以这里只断言"行动者被重置"和"时钟不倒退"，**不**断言堆顶身份。
     */
    @Test
    public void actorIsNotSkippedWhenAnotherSignalSitsInThePast() {
        Character a = character("A", 100);
        Character b = character("B", 100);
        Queue q = new Queue(List.of(a, b));

        q.move();                                        // A 行动，elapsed = 150
        q.advanceAction(b, 1000);                        // B 被拉到 150（clamp 到 elapsed，与 A 同值）

        Assertions.assertEquals(150, signalOf(q, b).getNextActionTime(), EPS);

        q.setTopZero();                                  // 结束 A 的回合（重置的必须是 A）

        Assertions.assertEquals(250, signalOf(q, a).getNextActionTime(), EPS,
                "A 被推回 250（下一轮才轮到他）");
        Assertions.assertEquals(150, signalOf(q, b).getNextActionTime(), EPS,
                "B 仍在 150（没被当成行动者重置掉）");

        q.move();
        Assertions.assertEquals(b, q.getCurrentActor().getCanHit(), "下一个行动的是 B（不是 A 连动）");
        Assertions.assertEquals(150, q.getElapsed(), EPS, "时钟没有倒退");
    }

    // ==================================================================

    private static Character character(String name, int speed) {
        return Character.fromAttributes(name, 10_000, 100, 100, speed);
    }

    private static Enemy dummy() {
        return EnemyFactory.create(1002011, 90, 1);
    }

    /** 队里那个敌人（冰锋，132 速）：首轮 {@code 10000/132 × 1.5 ≈ 113.64}，比速度 100 的角色（150）先动。 */
    private static CanHit dummyOf(Battle battle) {
        return battle.enemies.getFirst();
    }

    /**
     * 按 {@code CanHit} 的**身份**在堆里找它的信号。
     *
     * <p>⚠ 不能用 {@code getHeap().stream().findFirst()} 那种写法：{@code PriorityQueue}
     * 的迭代顺序是**堆数组顺序**，不是时间顺序，也不保证与插入顺序一致 ——
     * 两个同速单位会互换结果（我第一版就是这么写错的）。
     * 这里用 {@code equals}（{@code CanHit} 没重写它 → 身份比较）过滤，再断言唯一。
     */
    private static Signal signalOf(Queue q, CanHit target) {
        List<Signal> matches = q.getHeap().stream()
                .filter(s -> s.getCanHit().equals(target))
                .toList();
        Assertions.assertEquals(1, matches.size(),
                target.getName() + " 在行动条里应当只出现一次，实际 " + matches.size() + " 次");
        return matches.getFirst();
    }
}
