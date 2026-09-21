package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Queue;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Signal;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P7-2 验收：额外回合（{@code Battle.grantExtraTurn}）。
 *
 * <pre>
 * 额外回合 = 白送一次行动：下一次 stepForward() 由他行动，
 *            **时钟不动** ⇒ 不消耗行动值、轮次不变、他的正常回合排期原封不动
 * </pre>
 *
 * <p>与"拉条"的区别（很容易混淆，也是本条最核心的断言）：
 * 拉条是把他的**正常**回合提前（消耗掉它）；额外回合是额外给一次，
 * 他的正常回合还在原来的位置等他。
 */
public class ExtraTurnTest {
    private static final double EPS = 1e-9;

    /**
     * 额外回合让目标立刻行动，且时钟不动 → 轮次不变。
     */
    @Test
    public void extraTurnActsImmediatelyWithoutAdvancingTheClock() {
        Character hero = character("hero", 100);
        Queue q = new Queue(List.of(hero));

        q.move();                                        // hero 首轮 150
        q.setTopZero();                                  // hero → 250，elapsed = 150
        Assertions.assertEquals(250, signalOf(q, hero).getNextActionTime(), EPS);
        int roundBefore = q.getRound();

        Assertions.assertTrue(q.grantExtraTurn(hero));
        Assertions.assertEquals(hero, q.getExtraTurnActor());

        Assertions.assertEquals(0, q.move(), EPS, "额外回合不推进时钟");

        Assertions.assertEquals(hero, q.getCurrentActor().getCanHit());
        Assertions.assertEquals(150, q.getElapsed(), EPS, "时钟仍是 150");
        Assertions.assertEquals(roundBefore, q.getRound(), "轮次不变");
    }

    /**
     * 核心断言：额外回合**不消耗**目标的正常回合排期 —— 行动完之后，
     * 他的下一个行动点仍在原来的 250。
     *
     * <p>如果实现成"拉条到 elapsed"（错法），这里会得到 150 + 100 = 250 之外的值，
     * 或者干脆把他的正常回合吃掉（下一次行动点被推后）。
     */
    @Test
    public void extraTurnDoesNotConsumeTheNormalTurn() {
        Character hero = character("hero", 100);
        Battle battle = new Battle(List.of(hero), List.of(dummy()), new Random(0));
        Queue q = battle.queue;

        battle.stepForward();                            // 敌人 132 速先动（113.64）
        battle.afterMove();
        battle.stepForward();                            // hero 首轮 150
        Assertions.assertEquals(hero, battle.currentMove.getCanHit());
        battle.afterMove();                              // hero → 250

        Assertions.assertEquals(250, signalOf(q, hero).getNextActionTime(), EPS);

        Assertions.assertTrue(battle.grantExtraTurn(hero));
        battle.stepForward();                            // 额外回合：hero，时钟不动
        Assertions.assertEquals(hero, battle.currentMove.getCanHit());
        Assertions.assertEquals(150, q.getElapsed(), EPS, "时钟仍是 150");
        battle.afterMove();                              // hero 的正常排期被推后到 250

        Assertions.assertEquals(250, signalOf(q, hero).getNextActionTime(), EPS,
                "额外回合没吃掉正常回合：他的下一个行动点仍是 250");
    }

    /**
     * 核心断言（区分"正确实现"与"额外回合顺手把正常回合也消耗掉"）：
     * 额外回合之后，**他的正常回合仍在原来的位置**，不能被这次额外回合顶掉。
     *
     * <p>构造：actor 速度 140（首轮 {@code 10000/140 × 1.5 ≈ 107.14}），
     * fast 速度 200（首轮 75）。发出额外回合时 elapsed = 0，所以他原本的排期是 107.14。
     *
     * <pre>
     *   t=0     actor 拿额外回合，立刻行动（时钟不动）
     *   正确：actor 的正常排期仍是 107.14 → fast(75) → actor(107.14)
     *   错误（没还原）：setTopZero 把他排到 0 + 71.43 = 71.43 → 他会插到 fast 前面
     * </pre>
     *
     * <p>为什么必须有这条：单角色场景下"还原成原值"和"重排成 elapsed + 周期"恰好相等，
     * 测不出差别 —— 必须有另一个"正常回合更早"的单位才能把两者分开。
     */
    @Test
    public void extraTurnKeepsTheNormalTurnInItsOriginalPlace() {
        Character actor = character("actor", 140);       // 首轮 107.14，周期 71.43
        Character fast = character("fast", 200);         // 首轮 75
        Queue q = new Queue(List.of(actor, fast));

        double originalSchedule = 10000.0 / 140 * 1.5;
        Assertions.assertEquals(originalSchedule, signalOf(q, actor).getNextActionTime(), 1e-9);

        Assertions.assertTrue(q.grantExtraTurn(actor));
        Assertions.assertEquals(0, q.move(), EPS, "额外回合在 t=0 立刻发生");
        Assertions.assertEquals(actor, q.getCurrentActor().getCanHit());
        q.setTopZero();                                  // 周期被重排到 0 + 71.43

        // 下一次 move() 开头才还原他原本的排期（107.14）——
        // 这才是"额外回合"，而不是"把他的正常回合提前了"
        Assertions.assertEquals(fast, nextActor(q), "fast 的正常回合在 75，不该被 actor 插队");
        Assertions.assertEquals(75, q.getElapsed(), EPS);

        Assertions.assertEquals(actor, nextActor(q), "actor 的正常回合仍在 107.14");
        Assertions.assertEquals(originalSchedule, q.getElapsed(), 1e-9);
    }

    /**
     * 额外回合只有一次：用掉之后回到正常推进。
     */
    @Test
    public void extraTurnHappensOnce() {
        Character hero = character("hero", 100);
        Queue q = new Queue(List.of(hero));

        q.move();
        q.setTopZero();
        q.grantExtraTurn(hero);

        Assertions.assertEquals(hero, nextActor(q));
        Assertions.assertNull(q.getExtraTurnActor(), "额外回合已被消费");
        Assertions.assertEquals(150, q.getElapsed(), EPS);

        // 回到正常推进：下一次要等满一个周期
        Assertions.assertEquals(100, q.move(), EPS, "正常回合：150 → 250");
    }

    /**
     * 额外回合可以让"本来还早"的人抢先行动 —— 这正是插队语义。
     */
    @Test
    public void extraTurnJumpsAheadOfTheQueue() {
        Character soon = character("soon", 200);         // 首轮 75，先动
        Character late = character("late", 100);         // 首轮 150
        Queue q = new Queue(List.of(soon, late));

        Assertions.assertEquals(soon, nextActor(q));      // soon 在 75 行动

        // soon 下一次在 125；late 首轮在 150。给 late 额外回合 → 他插到 125 之前
        Assertions.assertTrue(q.grantExtraTurn(late));
        Assertions.assertEquals(late, nextActor(q), "late 插队先动");
        Assertions.assertEquals(75, q.getElapsed(), EPS, "时钟仍停在 75");

        // soon 的正常回合不受影响，仍在 125
        Assertions.assertEquals(soon, nextActor(q));
        Assertions.assertEquals(125, q.getElapsed(), EPS);
    }

    /**
     * 重复给同一个人发额外回合等价于一次（不会攒多次）。
     */
    @Test
    public void repeatedGrantsStillOnlyGiveOneExtraTurn() {
        Character hero = character("hero", 100);
        Queue q = new Queue(List.of(hero));

        q.move();
        q.setTopZero();                                  // hero → 250

        Assertions.assertTrue(q.grantExtraTurn(hero));
        Assertions.assertTrue(q.grantExtraTurn(hero), "重复发仍返回 true（他是合法目标）");

        Assertions.assertEquals(hero, nextActor(q));      // 额外回合
        Assertions.assertNull(q.getExtraTurnActor());

        Assertions.assertEquals(250, signalOf(q, hero).getNextActionTime(), EPS,
                "正常回合仍在 250，没有被额外回合顶掉");
        Assertions.assertEquals(100, q.move(), EPS, "回到正常推进：150 → 250");
    }

    /**
     * 已死亡的目标拿不到额外回合。
     */
    @Test
    public void deadTargetCannotGetAnExtraTurn() {
        Character hero = character("hero", 100);
        Queue q = new Queue(List.of(hero));

        hero.takeDamage(999_999);

        Assertions.assertFalse(q.grantExtraTurn(hero));
        Assertions.assertNull(q.getExtraTurnActor());
    }

    /**
     * 不在队列里的目标（未入场）拿不到额外回合。
     */
    @Test
    public void targetOutsideTheQueueCannotGetAnExtraTurn() {
        Character inQueue = character("inQueue", 100);
        Queue q = new Queue(List.of(inQueue));
        Character outsider = character("outsider", 100);

        Assertions.assertFalse(q.grantExtraTurn(outsider));
        Assertions.assertNull(q.getExtraTurnActor());
    }

    /**
     * 拿到额外回合之后死掉：这次额外回合作废，正常推进继续（不能因为一个死人卡住行动条）。
     */
    @Test
    public void extraTurnIsDroppedIfTheActorDiesBeforeUsingIt() {
        Character hero = character("hero", 100);
        Character other = character("other", 100);
        Queue q = new Queue(List.of(hero, other));

        q.move();                                        // hero 行动
        q.setTopZero();
        q.grantExtraTurn(hero);
        hero.takeDamage(999_999);
        q.removeCombatant(hero);                          // 死亡清理

        Assertions.assertEquals(other, nextActor(q), "死人的额外回合作废，轮到其他人");
        Assertions.assertNull(q.getExtraTurnActor());
    }

    /**
     * 额外回合期间禁止插入**别人**的终结技；但额外回合本人可以放。
     */
    @Test
    public void ultimateCannotBeInsertedDuringSomeoneElsesExtraTurn() {
        Character hero = character("hero", 100);
        Character ally = character("ally", 100);
        hero.setMaxEnergy(100);                          // fromAttributes 的 maxEnergy 默认 0：没能量条放不了大招
        ally.setMaxEnergy(100);
        Enemy enemy = dummy();
        Battle battle = new Battle(List.of(hero, ally), List.of(enemy), new Random(0));

        battle.stepForward();
        battle.afterMove();
        battle.stepForward();                            // hero 或 ally 行动
        CanHit actor = battle.currentMove.getCanHit();
        battle.afterMove();

        battle.grantExtraTurn(actor);

        CanHit bystander = actor == hero ? ally : hero;
        bystander.gainEnergy(bystander.getMaxEnergy());
        Assertions.assertTrue(bystander.isEnergyFull(), "旁观者能量已满（否则这条测不出东西）");

        Assertions.assertFalse(battle.castUltra(bystander, List.of(enemy)),
                "额外回合期间不能插别人的终结技");

        actor.gainEnergy(actor.getMaxEnergy());
        Assertions.assertTrue(actor.isEnergyFull());
        Assertions.assertTrue(battle.castUltra(actor, List.of(enemy)),
                "额外回合本人可以放终结技");
    }

    // ==================================================================

    /** {@code move()} + {@code setTopZero()}：消费一个回合并返回行动者。 */
    private static CanHit nextActor(Queue q) {
        q.move();
        CanHit actor = q.getCurrentActor().getCanHit();
        q.setTopZero();
        return actor;
    }

    private static Character character(String name, int speed) {
        return Character.fromAttributes(name, 10_000, 100, 100, speed);
    }

    private static Enemy dummy() {
        return EnemyFactory.create(1002011, 90, 1);
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
