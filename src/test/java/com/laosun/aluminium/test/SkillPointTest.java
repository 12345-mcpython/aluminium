package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 战技点（P8-4）：开局 3、上限 5、我方普攻 +1、战技 -1、终结技与追加攻击中性。
 *
 * <p><b>数据事实</b>：{@code skills.json} 里**没有**战技点字段 —— 638 条技能中普攻 122 条、
 * 战技 109 条的 {@code sp_need} 全是 {@code null}（有值的 99 条全是终结技，那是开大能量门槛，
 * 见 {@code engine.md} §9.4）。所以战技点只能来自游戏规则，落在 {@link Constant} 里。
 *
 * <p>本测试刻意**不用** {@code castImmediate}（那是绕过队列的测试/演示入口，按设计不碰战技点），
 * 全部走 {@code stepForward → beforeMove → performAction → afterMove} 的真实链路，
 * 否则测的是"没有战技点的世界"。
 */
public class SkillPointTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // 1. 池子本身
    // ==================================================================

    /** 开局 3 点，上限 5。 */
    @Test
    public void startsAtThreeAndCapsAtFive() {
        Battle battle = newBattle();

        Assertions.assertEquals(3, battle.getSkillPoints(), "开局战技点");
        Assertions.assertEquals(Constant.SKILL_POINT_START, battle.getSkillPoints());
        Assertions.assertEquals(5, Constant.SKILL_POINT_MAX);

        battle.gainSkillPoint(1);
        Assertions.assertEquals(4, battle.getSkillPoints());
        battle.gainSkillPoint(1);
        Assertions.assertEquals(5, battle.getSkillPoints(), "到 5");
        battle.gainSkillPoint(1);
        Assertions.assertEquals(5, battle.getSkillPoints(), "封顶，不溢出");
        battle.gainSkillPoint(100);
        Assertions.assertEquals(5, battle.getSkillPoints(), "一次加 100 也只到 5");
    }

    /** 消耗到 0 之后再多消耗要返回 false（而不是变成负数）。 */
    @Test
    public void spendingStopsAtZero() {
        Battle battle = newBattle();

        for (int i = 0; i < 3; i++) {
            Assertions.assertTrue(battle.spendSkillPoint(), "第 " + (i + 1) + " 次消耗应当成功");
        }
        Assertions.assertEquals(0, battle.getSkillPoints());
        Assertions.assertFalse(battle.hasSkillPoint());

        Assertions.assertFalse(battle.spendSkillPoint(), "0 点时应当失败");
        Assertions.assertEquals(0, battle.getSkillPoints(), "失败不能变成负数");
    }

    /** 回复非正数是调用方的 bug：静默忽略，别当成"扣点"。 */
    @Test
    public void gainingNonPositiveAmountIsIgnored() {
        Battle battle = newBattle();

        battle.gainSkillPoint(0);
        Assertions.assertEquals(3, battle.getSkillPoints(), "加 0 不变");
        battle.gainSkillPoint(-5);
        Assertions.assertEquals(3, battle.getSkillPoints(), "加负数不能变成扣点");
    }

    // ==================================================================
    // 2. 真实链路：performAction 真的会改战技点
    // ==================================================================

    /** 真实链路里放一次普攻 → +1。 */
    @Test
    public void basicAttackInRealActionFlowGainsOnePoint() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();

        Assertions.assertEquals(3, battle.getSkillPoints());
        Assertions.assertTrue(actWithRealTurn(battle, hero, () -> skill(hero, Slot.COMMON),
                () -> List.of(firstEnemy(battle))));
        Assertions.assertEquals(4, battle.getSkillPoints(), "普攻 +1");
    }

    /** 真实链路里放一次战技 → -1。 */
    @Test
    public void skillInRealActionFlowSpendsOnePoint() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();

        Assertions.assertTrue(actWithRealTurn(battle, hero, () -> skill(hero, Slot.SKILL),
                () -> List.of(firstEnemy(battle))));
        Assertions.assertEquals(2, battle.getSkillPoints(), "战技 -1");
    }

    /** 连放普攻到封顶：3 → 4 → 5 → 5。 */
    @Test
    public void repeatedBasicAttacksCapAtFive() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();

        for (int i = 0; i < 4; i++) {
            Assertions.assertTrue(
                    actWithRealTurn(battle, hero, () -> skill(hero, Slot.COMMON),
                            () -> List.of(firstEnemy(battle))),
                    "第 " + (i + 1) + " 次普攻");
        }

        Assertions.assertEquals(5, battle.getSkillPoints(), "普攻连打封顶 5");
    }

    // ==================================================================
    // 3. 0 点时战技放不出来，且**没有伤害**
    // ==================================================================

    /**
     * 核心：0 战技点时放战技 → {@code performAction} 返回 false，且目标一滴血不掉。
     *
     * <p>"没有伤害"这一条必须一起断言：{@code performAction} 只是**排队**，
     * 真正的结算在 {@code afterMove → processRequests}。如果扣点失败却已经把请求
     * 排进了队列，就会得到"没花钱却打出去了"。
     */
    @Test
    public void skillWithNoPointsFailsAndDealsNoDamage() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Enemy target = firstEnemy(battle);

        while (battle.spendSkillPoint()) {
            // 把池子清零
        }
        Assertions.assertEquals(0, battle.getSkillPoints());

        double hpBefore = target.getCurrentHp();
        Assertions.assertFalse(actWithoutAfterMove(battle, hero, () -> skill(hero, Slot.SKILL), List.of(target)),
                "0 战技点 → 出手不成立");
        Assertions.assertEquals(0, battle.getSkillPoints(), "失败的出手不能扣成负数");
        Assertions.assertEquals(hpBefore, target.getCurrentHp(), EPS, "失败的出手不能造成伤害");
    }

    // ==================================================================
    // 4. 终结技与追加攻击是"中性"的
    // ==================================================================

    /** 终结技既不消耗也不回复战技点（大招收尾回的那 5 点只进能量，不进战技点）。 */
    @Test
    public void ultimateNeitherSpendsNorGainsSkillPoints() {
        Character jingYuan = CharacterFactory.create(1204, 80);
        Battle battle = newBattle(jingYuan);

        int before = battle.getSkillPoints();
        jingYuan.setCurrentEnergy(200);
        Assertions.assertTrue(battle.castUltra(jingYuan, List.of(firstEnemy(battle))));

        Assertions.assertEquals(before, battle.getSkillPoints(), "终结技不碰战技点");
    }

    /**
     * 追加攻击 / 天赋：数据里 {@code attack_type} 是 {@code null}，必须走中性分支。
     *
     * <p>⚠ 这条防的是把 {@code switch} 写成"不是普攻就是战技"（{@code default -> 扣点}）的写法：
     * 那样天赋与追加攻击会悄悄吃战技点，而 P8-3 一做追加攻击就会立刻踩到。
     */
    @Test
    public void nullAttackTypeIsNeutral() {
        Battle battle = newBattle();
        Skill talentLike = skill(CharacterFactory.create(1003, 80), Slot.TALENT);
        Assertions.assertNull(talentLike.getData().getSkillType(), "前提：天赋的 attack_type 是 null");

        int before = battle.getSkillPoints();
        Assertions.assertTrue(battle.applySkillPointCost(talentLike, battle.characters.getFirst()),
                "中性技能不阻挡出手");
        Assertions.assertEquals(before, battle.getSkillPoints(), "不涨不跌");
    }

    /** 地图普攻（槽位 6，{@code MazeNormal}）也按中性处理，不算"普攻回点"。 */
    @Test
    public void mazeAttackTypeIsNeutral() {
        Battle battle = newBattle();
        Skill maze = skill(CharacterFactory.create(1003, 80), Slot.MAZE);

        int before = battle.getSkillPoints();
        Assertions.assertTrue(battle.applySkillPointCost(maze, battle.characters.getFirst()));
        Assertions.assertEquals(before, battle.getSkillPoints(), "MazeNormal 中性");
    }

    // ==================================================================
    // 5. 只算我方：敌人的普攻不能给我方送点
    // ==================================================================

    /**
     * 敌方行动不能改变我方战技点。
     *
     * <p>⚠ <b>这条测试的陷阱（已修）</b>：敌人的默认技能 {@code EnemySkill} 的
     * {@code getData()} **恒为 null**（它不走角色倍率表，见该类 javadoc），
     * 于是在 {@code applySkillPointCost} 的 null 保护处就返回了 ——
     * 拿默认技能测"敌方不影响战技点"，**无论有没有阵营判断都会通过**。
     * 我第一次就是这么写的，靠变异测试才发现（去掉阵营判断后依然绿）。
     *
     * <p>所以这里**手工给敌人装一个真实角色普攻**（{@code attack_type = "Normal"}）：
     * 只有这样才能真正走到分支上，让"阵营判断"成为唯一能挡住它的东西。
     */
    @Test
    public void enemyBasicAttackDoesNotFeedThePlayerPool() {
        Battle battle = newBattle();
        Enemy enemy = firstEnemy(battle);
        Character hero = battle.characters.getFirst();

        // 前提自检：默认的敌人技能 getData() 是 null，测不出阵营判断
        Assertions.assertNull(enemySkill(enemy).getData(),
                "前提：EnemySkill 没有角色倍率数据，直接测它是空转");

        // 换成"真实角色的普攻"（Normal）—— 数据非 null，才会真的走到战技点分支
        enemy.setSkill(SkillType.COMMON, new DefaultSkill(1003, 1, 1));
        int before = battle.getSkillPoints();
        Assertions.assertTrue(actWithRealTurn(battle, enemy, () -> enemy.getSkills().get(SkillType.COMMON),
                () -> List.of(hero)), "敌人这次普攻本身应当成功");

        Assertions.assertEquals(before, battle.getSkillPoints(),
                "敌方行动不能改变我方战技点（去掉阵营判断这条就会失败）");
    }

    // ==================================================================
    // 6. applySkillPointCost 是**原子**的
    // ==================================================================

    /** 0 点时试图放战技：返回 false，且点数不变（不能先扣成 -1 再判断）。 */
    @Test
    public void skillPointCostIsAtomicAtZero() {
        Battle battle = newBattle();
        Skill skill = battle.characters.getFirst().getSkills().get(SkillType.SKILL);

        while (battle.spendSkillPoint()) {
            // 清零
        }
        Assertions.assertFalse(battle.applySkillPointCost(skill, battle.characters.getFirst()),
                "点数不足 → 失败");
        Assertions.assertEquals(0, battle.getSkillPoints(), "失败的那次不能扣成 -1");
    }

    // ==================================================================
    // 辅助
    // ==================================================================

    private enum Slot {
        COMMON(1), SKILL(2), TALENT(4), MAZE(6);

        private final int slot;

        Slot(int slot) {
            this.slot = slot;
        }
    }

    private static Skill skill(Character hero, Slot slot) {
        return new DefaultSkill(hero.getCid(), slot.slot, 1);
    }

    /** 姬子 1003：槽位 1/2 在数据里分别是 {@code Normal} / {@code BPSkill}。 */
    private static Battle newBattle() {
        return newBattle(CharacterFactory.create(1003, 80));
    }

    private static Battle newBattle(Character hero) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    /** 敌人的普攻（P5-3 装在敌人身上的技能）。 */
    private static Skill enemySkill(Enemy enemy) {
        return enemy.getSkills().values().iterator().next();
    }

    /**
     * 按真实流程让 {@code actor} 出一次手，**并收尾**（{@code afterMove}）。
     *
     * <p>行动值最先到的可能是敌人（冰锋 132 速 > 姬子 96 速），所以要先跳到 actor 的回合。
     */
    private static boolean actWithRealTurn(Battle battle, CanHit actor, Supplier<Skill> skill,
                                           Supplier<List<? extends CanHit>> targets) {
        boolean result = actWithoutAfterMove(battle, actor, skill, targets.get());
        battle.afterMove();
        return result;
    }

    /** 同上，但**不**收尾 —— 留给调用方在结算前做断言（"没伤害"那条要用）。 */
    private static boolean actWithoutAfterMove(Battle battle, CanHit actor, Supplier<Skill> skill,
                                               List<? extends CanHit> targets) {
        for (int i = 0; i < 30; i++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            if (battle.currentMove.getCanHit() == actor) {
                battle.beforeMove();
                return battle.performAction(skill.get(), targets);
            }
            battle.afterMove();
        }
        Assertions.fail("30 步内没轮到 " + actor.getName() + " 的回合");
        return false;
    }

    private static Enemy firstEnemy(Battle battle) {
        return battle.enemies.getFirst();
    }
}
