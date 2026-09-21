package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.StageBean;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.WaveManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * P7-4 验收：关卡数据（{@code stage.json}）+ 波次。
 *
 * <p>覆盖两类东西：
 * <ol>
 *   <li>数据层：{@link StageBean} 的读法（波 = {@code monster} 的元素、波内顺序 = {@code MonsterN} 的 N 序）；</li>
 *   <li>流程层：{@link WaveManager} 逐波进怪，且**敌队为空不等于打赢**（P7-3 × P7-4 的接缝）。</li>
 * </ol>
 *
 * <p>⚠ 依赖 {@code stage.json} 的测试用 {@link Assumptions#assumeFalse} 兜底：
 * 这张表是 generator 产出的，未生成时 {@link Constant#stages()} 是空表
 * （刻意设计成不拖垮整个测试套件，见 {@code Constant.stages()} 的说明）。
 */
public class WaveManagerTest {

    /**
     * 数据层：{@code monster} 的每一项是一波，波内 id 按 {@code MonsterN} 的 N 排序读出。
     */
    @Test
    public void stageBeanReadsWavesAndMonsterOrder() {
        StageBean stage = new StageBean("Mainline", 1, 29, List.of(
                wave(10, 20, 30),
                wave(40)));

        Assertions.assertEquals(2, stage.waveCount(), "monster 的两个元素 = 两波");
        Assertions.assertEquals(List.of(10, 20, 30), stage.monsterIds(0));
        Assertions.assertEquals(List.of(40), stage.monsterIds(1));
        Assertions.assertEquals(List.of(), stage.monsterIds(2), "越界给空列表，不抛");
        Assertions.assertEquals(List.of(), stage.monsterIds(-1), "负数下标也给空列表");
    }

    /**
     * 数据层：真的 {@code stage.json} —— stage 103201 是 1 波 3 只。
     */
    @Test
    public void stageDataHasTheExpectedShape() {
        StageBean stage = stage(103201);

        Assertions.assertEquals(1, stage.waveCount());
        Assertions.assertEquals(3, stage.monsterIds(0).size());
        Assertions.assertEquals(29, stage.level());
        Assertions.assertEquals(1, stage.hardLevelGroup());
    }

    /**
     * 数据层：多波关卡（310030 有 3 波）—— 波数直接来自 {@code monster} 的元素个数。
     */
    @Test
    public void multiWaveStageIsReadAsMultipleWaves() {
        StageBean stage = stage(310030);

        Assertions.assertTrue(stage.waveCount() >= 2, "310030 应当是多波关卡");
        Assertions.assertFalse(stage.monsterIds(0).isEmpty());
        Assertions.assertFalse(stage.monsterIds(1).isEmpty());
    }

    /**
     * 进一波：怪被造出来、加进敌对列表、并排队入场。
     */
    @Test
    public void nextWaveSpawnsTheWaveAndQueuesIt() {
        Battle battle = waveBattle(103201);
        WaveManager waves = battle.getWaveManager();

        Assertions.assertTrue(waves.hasNextWave(), "还有第 1 波没进");
        Assertions.assertEquals(-1, waves.getWaveIndex(), "一波都还没进");

        Assertions.assertTrue(waves.nextWave());
        Assertions.assertEquals(0, waves.getWaveIndex());
        Assertions.assertEquals(3, battle.enemies.size(), "103201 第 1 波 3 只");
        Assertions.assertEquals(3, battle.addRequestItems.size(), "都排队等着入场");

        battle.processRequests();
        Assertions.assertEquals(4, battle.queue.size(),
                "入场后进了行动条：1 名角色 + 3 只怪（角色在构造时就入了队）");
        Assertions.assertEquals(3, battle.targetableEnemies().size());
    }

    /**
     * 标准用法：{@code startBattle()} 之后再进波，行动条里是"我方 + 这一波的怪"。
     */
    @Test
    public void wavesJoinTheQueueAlongsideTheParty() {
        Battle battle = waveBattle(103201);
        battle.startBattle();
        Assertions.assertEquals(1, battle.queue.size(), "开场只有我方 1 人");

        battle.getWaveManager().nextWave();
        battle.processRequests();

        Assertions.assertEquals(4, battle.queue.size(), "1 名角色 + 3 只怪");
        Assertions.assertEquals(3, battle.targetableEnemies().size());
    }

    /**
     * 单波关卡：进完就没有下一波了。
     */
    @Test
    public void singleWaveStageHasNoNextWave() {
        Battle battle = waveBattle(103201);
        WaveManager waves = battle.getWaveManager();

        Assertions.assertTrue(waves.nextWave());
        Assertions.assertFalse(waves.hasNextWave());
        Assertions.assertFalse(waves.nextWave(), "没有下一波时返回 false");
        Assertions.assertEquals(3, battle.enemies.size(), "不会重复进怪");
    }

    /**
     * 多波关卡：一波一波进，每次只加那一波的怪。
     */
    @Test
    public void multiWaveStageSpawnsOneWaveAtATime() {
        Battle battle = waveBattle(310030);
        WaveManager waves = battle.getWaveManager();

        StageBean stage = Constant.stages().get(310030);
        int firstWaveSize = stage.monsterIds(0).size();
        int secondWaveSize = stage.monsterIds(1).size();

        Assertions.assertTrue(waves.nextWave());
        Assertions.assertEquals(firstWaveSize, battle.enemies.size());

        Assertions.assertTrue(waves.nextWave());
        Assertions.assertEquals(firstWaveSize + secondWaveSize, battle.enemies.size(),
                "第二波是**追加**，不是替换");
        Assertions.assertEquals(1, waves.getWaveIndex());
    }

    /**
     * 核心接缝：**敌队为空 ≠ 打赢**。还有波没进时，{@code checkResult()} 不能判胜。
     *
     * <p>这是 P7-3 与 P7-4 之间最容易踩的一处：P7-3 的判据是"一方全灭"，
     * 而波次模式里"敌队是空的"只是**这一波还没进**。
     */
    @Test
    public void pendingWavesDoNotCountAsAWonBattle() {
        Battle battle = waveBattle(310030);

        Assertions.assertTrue(battle.getWaveManager().hasPendingWaves());
        battle.startBattle();

        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus(),
                "敌队是空的，但还有波没进 → 不能判胜");
        Assertions.assertEquals(Battle.Status.RUNNING, battle.checkResult());
    }

    /**
     * 打完最后一波才判胜。
     */
    @Test
    public void theBattleIsWonOnlyAfterTheLastWave() {
        Battle battle = waveBattle(103201);
        WaveManager waves = battle.getWaveManager();
        battle.startBattle();
        waves.nextWave();
        battle.processRequests();

        // 把这一波全打死
        for (Enemy enemy : battle.enemies) {
            enemy.takeDamage(999_999_999);
        }
        battle.processRequests();

        Assertions.assertFalse(waves.hasNextWave(), "只有一波，已经进完了");
        Assertions.assertEquals(Battle.Status.WIN, battle.getStatus());
    }

    /**
     * 多波关卡里，第 1 波灭掉**不**判胜；进第 2 波之后战斗继续。
     */
    @Test
    public void clearingAnIntermediateWaveDoesNotEndTheBattle() {
        Battle battle = waveBattle(310030);
        WaveManager waves = battle.getWaveManager();
        battle.startBattle();
        waves.nextWave();
        battle.processRequests();

        for (Enemy enemy : battle.enemies) {
            enemy.takeDamage(999_999_999);
        }
        battle.processRequests();

        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus(),
                "还有第 2 波 → 不能判胜");
        Assertions.assertTrue(waves.isCurrentWaveCleared());

        int before = battle.enemies.size();
        Assertions.assertTrue(waves.nextWave());
        battle.processRequests();

        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus(), "新一波进来了，继续打");
        Assertions.assertTrue(battle.enemies.size() > before);
        Assertions.assertFalse(waves.isCurrentWaveCleared(), "新一波是活的");
        Assertions.assertFalse(waves.aliveEnemies().isEmpty());
    }

    /**
     * 我方全灭时，就算还有没进的波也是输 —— "要不要再进一波"救不了团灭。
     */
    @Test
    public void aWipedPartyStillLosesWithPendingWaves() {
        Battle battle = waveBattle(310030);
        battle.startBattle();

        for (Character c : battle.characters) {
            c.takeDamage(999_999_999);
        }
        battle.processRequests();

        Assertions.assertEquals(Battle.Status.LOSE, battle.getStatus());
    }

    /**
     * 中途入场的怪是从**当前行动值**起跑的，不是回到 0 重开一轮。
     */
    @Test
    public void lateWaveEnemiesEnterFromTheCurrentActionValue() {
        Battle battle = waveBattle(103201);
        WaveManager waves = battle.getWaveManager();
        battle.startBattle();

        battle.stepForward();
        battle.afterMove();                              // 时钟走了一段（敌人 132 速先动）
        double elapsedBefore = battle.queue.getElapsed();
        Assertions.assertTrue(elapsedBefore > 0);

        waves.nextWave();
        battle.processRequests();

        for (Enemy enemy : battle.enemies) {
            double remaining = battle.queue.getTimeRemaining(signalOf(battle, enemy));
            Assertions.assertTrue(remaining > 0 && remaining < Double.MAX_VALUE,
                    "新怪的剩余行动值是正数且有限");
            Assertions.assertTrue(battle.queue.getElapsed() >= elapsedBefore,
                    "进怪不会把时钟拨回去");
        }
    }

    /**
     * 没有波次管理器的普通战斗不受影响（P7-3 的行为不变）。
     */
    @Test
    public void battlesWithoutWavesBehaveAsBefore() {
        Battle battle = new Battle(List.of(character("hero", 100)),
                List.of(com.laosun.aluminium.models.EnemyFactory.create(1002011, 90, 1)), new Random(0));

        Assertions.assertNull(battle.getWaveManager());
        battle.startBattle();

        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus());
        Assertions.assertEquals(1, battle.enemies.size());
    }

    /**
     * 关卡表最多解析一次，重复取用命中缓存。
     *
     * <p>懒加载的护栏（"静态块不解析 stage.json"）在 {@link StageLazyLoadTest} ——
     * 那边单独一个类，因为本类里别的用例会先把表加载掉。
     */
    @Test
    public void stageTableIsLoadedAtMostOnceAndCached() {
        Map<Integer, StageBean> first = Constant.stages();
        Map<Integer, StageBean> second = Constant.stages();

        Assertions.assertSame(first, second, "重复取用必须命中同一份缓存，不能重复解析");
        Assertions.assertEquals(1, Constant.stageLoadAttempts(),
                "stage.json 只解析一次，实际 " + Constant.stageLoadAttempts() + " 次");
    }

    /**
     * 关卡表里至少有 1 万条关卡（数据完整性 sanity check）。
     */
    @Test
    public void stageTableHasTheWholeDataSet() {
        Assumptions.assumeFalse(Constant.stages().isEmpty(), "stage.json 未生成");

        Assertions.assertTrue(Constant.stages().size() > 10_000,
                "stage.json 应当有上万条关卡，实际 " + Constant.stages().size());
    }

    // ==================================================================

    /** 造一个"空敌队 + 波次管理器"的战斗（P7-4 的标准用法）。 */
    private static Battle waveBattle(int stageId) {
        StageBean stage = stage(stageId);
        Battle battle = new Battle(List.of(character("hero", 100)), new ArrayList<>(), new Random(0));
        new WaveManager(battle, stage);
        return battle;
    }

    /** 取关卡；没有数据就 skip（stage.json 是 generator 产出的，见类注释）。 */
    private static StageBean stage(int stageId) {
        StageBean stage = Constant.stages().get(stageId);
        Assumptions.assumeFalse(Constant.stages().isEmpty(),
                "缺少 stage.json（generator 产出），跳过关卡相关断言");
        Assertions.assertNotNull(stage, "stage.json 里应当有 " + stageId);
        return stage;
    }

    /** 按 {@code Monster0..N} 的顺序造一波（模拟 Gson 读出来的 LinkedHashMap）。 */
    private static Map<String, Integer> wave(int... ids) {
        Map<String, Integer> wave = new LinkedHashMap<>();
        for (int i = 0; i < ids.length; i++) {
            wave.put("Monster" + i, ids[i]);
        }
        return wave;
    }

    private static Character character(String name, int speed) {
        return Character.fromAttributes(name, 10_000, 100, 100, speed);
    }

    private static com.laosun.aluminium.models.Signal signalOf(Battle battle, Enemy enemy) {
        return battle.queue.getHeap().stream()
                .filter(s -> s.getCanHit() == enemy)
                .findFirst()
                .orElseThrow(() -> new AssertionError(enemy.getName() + " 不在行动条里"));
    }
}
