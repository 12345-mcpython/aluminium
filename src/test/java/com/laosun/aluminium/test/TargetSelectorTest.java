package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.EnemySkill;
import com.laosun.aluminium.models.ai.TargetSelector;
import com.laosun.aluminium.models.buffs.TauntBuff;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * P5-2 / P5-4 验收：目标选择。
 *
 * <p>两条策略：嘲讽硬约束优先，其次仇恨加权随机。
 */
public class TargetSelectorTest {
    private static final double EPS = 1e-9;

    @Test
    public void aggroWeightingPicksInProportion() {
        Character tank = withAggro("tank", 150);
        Character other = withAggro("other", 100);
        Battle battle = new Battle(List.of(tank, other), List.of(dummy()), new Random(42));
        Random rng = new Random(42);
        List<CanHit> candidates = List.of(tank, other);

        int tankPicked = 0;
        int rounds = 4000;
        for (int i = 0; i < rounds; i++) {
            if (TargetSelector.select(battle, candidates, TargetSelector.Intent.SINGLE, rng) == tank) {
                tankPicked++;
            }
        }

        double frequency = (double) tankPicked / rounds;
        Assertions.assertEquals(0.6, frequency, 0.03, "150 / 250 ≈ 0.6，偏差 < 3%");
    }

    @Test
    public void deadCandidateIsNeverPicked() {
        Character alive = withAggro("alive", 150);
        Character dead = withAggro("dead", 100);
        dead.takeDamage(999_999);
        Battle battle = new Battle(List.of(alive, dead), List.of(dummy()), new Random(0));

        // 调用方负责过滤死亡目标（候选集口径：Battle.targetableEnemies 只给活人）
        List<CanHit> aliveCandidates = new ArrayList<>(List.of(alive, dead));
        aliveCandidates.removeIf(CanHit::isDeath);

        Random rng = new Random(1);
        for (int i = 0; i < 200; i++) {
            Assertions.assertSame(alive, TargetSelector.select(battle, aliveCandidates,
                    TargetSelector.Intent.SINGLE, rng), "死人不该被选中");
        }
    }

    @Test
    public void tauntForcesTheTargetForSingleAndBlast() {
        Character tank = withAggro("tank", 100);
        Character squishy = withAggro("squishy", 150);       // 仇恨更高，本该更常被打
        squishy.getBuffManager().addBuff(new TauntBuff(2));
        Battle battle = new Battle(List.of(tank, squishy), List.of(dummy()), new Random(7));
        List<CanHit> candidates = List.of(tank, squishy);

        Random rng = new Random(7);
        for (int i = 0; i < 200; i++) {
            Assertions.assertSame(squishy,
                    TargetSelector.select(battle, candidates, TargetSelector.Intent.SINGLE, rng),
                    "单体攻击被嘲讽硬指定");
            Assertions.assertSame(squishy,
                    TargetSelector.select(battle, candidates, TargetSelector.Intent.BLAST, rng),
                    "扩散攻击的中心同样被硬指定");
        }
    }

    @Test
    public void tauntDoesNotAffectAoeOrRandomIntents() {
        Character tank = withAggro("tank", 100);
        Character taunter = withAggro("taunter", 150);
        taunter.getBuffManager().addBuff(new TauntBuff(2));
        Battle battle = new Battle(List.of(tank, taunter), List.of(dummy()), new Random(3));
        List<CanHit> candidates = List.of(tank, taunter);

        // 群攻打全体、弹射逐段随机：都不该被嘲讽"锁死"到嘲讽者身上
        Random rng = new Random(3);
        boolean sawTank = false;
        for (int i = 0; i < 400; i++) {
            if (TargetSelector.select(battle, candidates, TargetSelector.Intent.RANDOM, rng) == tank) {
                sawTank = true;
                break;
            }
        }
        Assertions.assertTrue(sawTank, "弹射不受嘲讽约束（仍会抽到别人）");
    }

    @Test
    public void deadTaunterLosesTheConstraintAndFallsBackToAggro() {
        Character tank = withAggro("tank", 100);
        Character taunter = withAggro("taunter", 150);
        taunter.getBuffManager().addBuff(new TauntBuff(2));
        Battle battle = new Battle(List.of(tank, taunter), List.of(dummy()), new Random(5));

        // 嘲讽者死了，且调用方的候选集已把它滤掉 → 退回仇恨加权，选中唯一剩下的坦克
        taunter.takeDamage(999_999);
        List<CanHit> aliveCandidates = new ArrayList<>(List.of(tank, taunter));
        aliveCandidates.removeIf(CanHit::isDeath);

        Assertions.assertSame(tank, TargetSelector.select(battle, aliveCandidates,
                TargetSelector.Intent.SINGLE, new Random(5)), "不能强制选中尸体");
    }

    @Test
    public void emptyCandidateSetReturnsNull() {
        Battle battle = new Battle(List.of(withAggro("a", 100)), List.of(dummy()), new Random(0));

        Assertions.assertNull(TargetSelector.select(battle, List.of(),
                TargetSelector.Intent.SINGLE, new Random(0)));
    }

    @Test
    public void enemyAttackActuallyRunsThroughPerformAction() {
        // P5-5 的最小验证：敌人作为行动者，用 TargetSelector 选目标、走 performAction 出手
        Enemy iceEdge = EnemyFactory.create(1002011, 90, 1);
        Character victim = Character.fromAttributes("victim", 100_000, 1000, 100, 100);
        victim.setMaxEnergy(120);                             // 没能量条时回能是 no-op（maxEnergy == 0）
        Battle battle = new Battle(List.of(victim), List.of(iceEdge), new Random(0));

        Assertions.assertTrue(iceEdge.getSkills().get(SkillType.COMMON) instanceof EnemySkill,
                "敌人身上装好了 EnemySkill");

        battle.stepForward();                                 // 冰锋速度 132 > 100，先动
        CanHit actor = battle.queue.getCurrentActor().getCanHit();
        Assertions.assertSame(iceEdge, actor);

        CanHit target = TargetSelector.select(battle, List.of(victim), TargetSelector.Intent.SINGLE, battle.getRng());
        double hpBefore = victim.getCurrentHp();
        Assertions.assertTrue(battle.performAction(iceEdge.getSkills().get(SkillType.COMMON), List.of(target)));
        battle.processRequests();

        // 期望值从攻击者面板推导：攻击力 × 倍率 1.0 × 防御区（攻击者 Lv90、受害者防御 1000）
        double levelTerm = Constant.DEFENCE_CONST + Constant.DEFENCE_PER_LEVEL * iceEdge.getLevel();
        double expected = iceEdge.getAttribute(AttributeType.ATTACK).get() * levelTerm / (1000 + levelTerm);
        Assertions.assertEquals(expected, hpBefore - victim.getCurrentHp(), 0.1,
                "冰锋普攻：攻击力 × 1.0 过防御区");
        Assertions.assertEquals(10, victim.getCurrentEnergy(), EPS, "受击回能");
    }

    // ==================================================================

    private static Character withAggro(String name, int aggro) {
        Character c = Character.fromAttributes(name, 10_000, 1000, 100, 100);
        c.setAggro(aggro);
        return c;
    }

    private static Enemy dummy() {
        return EnemyFactory.create(1002011, 90, 1);
    }
}
