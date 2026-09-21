package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.StageBean;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.WaveManager;
import com.laosun.aluminium.utils.StageFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * P7-5 验收：{@code StageFactory.load(stageId)} 按关卡组装一场能直接开打的战斗。
 *
 * <p>覆盖：
 * <ol>
 *   <li>难度来自关卡（{@code hard_level_group} + {@code level} 喂给 {@code EnemyFactory}）；</li>
 *   <li>第 1 波已入场且**排进了行动条**（进怪是排队入场，容易漏 {@code processRequests()}）；</li>
 *   <li>状态已经是 {@code RUNNING}（不是 {@code NOT_STARTED}），可完整跑回合；</li>
 *   <li>报错路径：未知关卡 / 队伍为空。</li>
 * </ol>
 *
 * <p>⚠ 依赖 {@code stage.json}（generator 产出、不入库）的用例用 assume 兜底。
 */
public class StageFactoryTest {

    /**
     * 计划里的验收：{@code load(103201)} 能开打、3 只怪、状态已不是 NOT_STARTED。
     */
    @Test
    public void loadBuildsARunnableBattle() {
        assumeStageData();
        Battle battle = StageFactory.load(103201);

        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus(),
                "load 出来的战斗应当已经开打");
        Assertions.assertEquals(3, battle.enemies.size(), "103201 第 1 波 3 只");
        Assertions.assertEquals(0, battle.getWaveManager().getWaveIndex(), "第 1 波已进");

        int expectedQueueSize = battle.characters.size() + battle.enemies.size();
        Assertions.assertEquals(expectedQueueSize, battle.queue.size(),
                "我方 + 第 1 波的怪都该在行动条里（漏 processRequests 就会少人）");
    }

    /**
     * 难度来自关卡：同一只怪在不同 level 的关卡里面板不同。
     *
     * <p>这条是 P7-5 的核心 —— "难"是数据决定的，不是调用方传系数。
     */
    @Test
    public void difficultyComesFromTheStage() {
        assumeStageData();
        // 103201: level 29；103203: level 60（两者 hard_level_group 都是 1）
        StageBean low = StageFactory.requireStage(103201);
        StageBean high = StageFactory.requireStage(103203);

        Assertions.assertTrue(high.level() > low.level(), "选中的两个关卡等级应当不同");

        Enemy lowEnemy = battle(103201).enemies.getFirst();
        Enemy highEnemy = battle(103203).enemies.getFirst();

        Assertions.assertTrue(highEnemy.getMaxHp() > lowEnemy.getMaxHp(),
                "等级高的关卡里怪物血量应当更高（低：" + lowEnemy.getMaxHp()
                        + "，高：" + highEnemy.getMaxHp() + "）");
    }

    /**
     * 关卡里的怪确实是关卡数据里写的那些 id / 数量。
     */
    @Test
    public void enemiesMatchTheStageData() {
        assumeStageData();
        StageBean stage = StageFactory.requireStage(103201);
        Battle battle = StageFactory.load(103201, StageFactory.temporaryTeam(), new Random(1));

        Assertions.assertEquals(stage.monsterIds(0).size(), battle.enemies.size());
        for (Enemy enemy : battle.enemies) {
            Assertions.assertFalse(enemy.isDeath());
            Assertions.assertTrue(enemy.getMaxHp() > 0);
            Assertions.assertEquals(stage.level(), enemy.getLevel(),
                    "怪物等级应当来自关卡");
        }
    }

    /**
     * 固定种子 → 整场可复现（同一个 stage + 同一个 seed 两次组装结果一致）。
     */
    @Test
    public void fixedSeedMakesTheBattleReproducible() {
        assumeStageData();
        Battle first = StageFactory.load(103201, StageFactory.temporaryTeam(), new Random(42));
        Battle second = StageFactory.load(103201, StageFactory.temporaryTeam(), new Random(42));

        Assertions.assertEquals(describe(first), describe(second),
                "同种子两次组装应当完全一致");
    }

    /**
     * 完整跑几个回合不炸，且状态保持在 RUNNING / 终态。
     */
    @Test
    public void theBattleCanRunTurns() {
        assumeStageData();
        Battle battle = StageFactory.load(103201, StageFactory.temporaryTeam(), new Random(7));

        for (int i = 0; i < 5 && !battle.isOver(); i++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            battle.beforeMove();
            battle.afterMove();
        }

        Assertions.assertTrue(battle.queue.getElapsed() > 0, "时钟应当走过了");
        Assertions.assertNotEquals(Battle.Status.NOT_STARTED, battle.getStatus());
    }

    /**
     * 多波关卡：打完第 1 波可以进第 2 波，且不会判胜。
     */
    @Test
    public void multiWaveStageCanAdvanceToTheNextWave() {
        assumeStageData();
        StageBean stage = StageFactory.requireStage(310030);
        Assumptions.assumeTrue(stage.waveCount() >= 2, "310030 应当是多波关卡");

        Battle battle = StageFactory.load(310030, StageFactory.temporaryTeam(), new Random(3));
        WaveManager waves = battle.getWaveManager();
        Assertions.assertEquals(0, waves.getWaveIndex());

        for (Enemy enemy : battle.enemies) {
            enemy.takeDamage(999_999_999);
        }
        battle.processRequests();

        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus(), "还有下一波 → 不判胜");

        int before = battle.enemies.size();
        Assertions.assertTrue(waves.nextWave());
        battle.processRequests();

        Assertions.assertTrue(battle.enemies.size() > before, "第 2 波是追加");
        Assertions.assertEquals(1, waves.getWaveIndex());
        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus());
    }

    /**
     * 临时队伍：3 人、速度各不相同（行动条才有区分度）、有能量条（否则放不出大招）。
     */
    @Test
    public void temporaryTeamIsUsable() {
        List<Character> team = StageFactory.temporaryTeam();

        Assertions.assertEquals(3, team.size());
        Assertions.assertEquals(3, team.stream()
                        .map(c -> c.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get())
                        .distinct().count(),
                "速度应当各不相同");
        for (Character c : team) {
            Assertions.assertTrue(c.getMaxHp() > 0);
            Assertions.assertTrue(c.hasEnergyBar(), c.getName() + " 应当有能量条（否则放不出终结技）");
            Assertions.assertFalse(c.isDeath());
        }
    }

    /**
     * 调用方可以传自己的队伍（P8-5 换真队就是走这条路）。
     */
    @Test
    public void callersCanSupplyTheirOwnTeam() {
        assumeStageData();
        List<Character> team = new ArrayList<>();
        team.add(Character.fromAttributes("solo", 50_000, 200, 200, 150));

        Battle battle = StageFactory.load(103201, team, new Random(0));

        Assertions.assertEquals(1, battle.characters.size());
        Assertions.assertEquals("solo", battle.characters.getFirst().getName());
        Assertions.assertEquals(1 + battle.enemies.size(), battle.queue.size());
    }

    /**
     * 报错路径：未知关卡给自解释的信息；队伍为空也拒绝。
     */
    @Test
    public void invalidInputsAreRejected() {
        assumeStageData();

        IllegalArgumentException unknown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> StageFactory.load(-1));
        Assertions.assertTrue(unknown.getMessage().contains("-1"), unknown.getMessage());

        IllegalArgumentException emptyTeam = Assertions.assertThrows(IllegalArgumentException.class,
                () -> StageFactory.load(103201, List.of(), new Random(0)));
        Assertions.assertTrue(emptyTeam.getMessage().contains("队伍"), emptyTeam.getMessage());

        Assertions.assertFalse(StageFactory.hasStage(-1));
        Assertions.assertTrue(StageFactory.hasStage(103201));
    }

    // ==================================================================

    /** 关卡数据不在仓库里，缺失时 skip（见 README 的 generator 一节）。 */
    private static void assumeStageData() {
        Assumptions.assumeFalse(Constant.stages().isEmpty(),
                "缺少 stage.json（generator 产出），跳过 StageFactory 相关断言");
    }

    private static Battle battle(int stageId) {
        return StageFactory.load(stageId, StageFactory.temporaryTeam(), new Random(0));
    }

    /** 把一场战斗的可见状态压成字符串，用来比对两次组装是否一致。 */
    private static String describe(Battle battle) {
        StringBuilder text = new StringBuilder();
        text.append("elapsed=").append(battle.queue.getElapsed())
                .append(" status=").append(battle.getStatus())
                .append(" round=").append(battle.getRound())
                .append('\n');
        for (var signal : battle.queue.snapshot()) {
            text.append(signal.getCanHit().getName())
                    .append('@').append(signal.getNextActionTime())
                    .append(" speed=").append(signal.getSpeed())
                    .append('\n');
        }
        for (Enemy enemy : battle.enemies) {
            text.append(enemy.getName()).append(" hp=").append(enemy.getMaxHp()).append('\n');
        }
        return text.toString();
    }
}
