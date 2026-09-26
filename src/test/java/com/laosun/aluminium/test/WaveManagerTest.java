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
 * P7-4 acceptance: stage data ({@code stage.json}) + waves.
 *
 * <p>Covers two kinds of thing:
 * <ol>
 *   <li>The data layer: how {@link StageBean} is read (a wave = an element of {@code monster}; the
 *   order within a wave = the N order of {@code MonsterN});</li>
 *   <li>The flow layer: {@link WaveManager} spawning monsters wave by wave, and **an empty enemy
 *   team does not equal a win** (the seam between P7-3 × P7-4).</li>
 * </ol>
 *
 * <p>⚠ Tests that depend on {@code stage.json} fall back to {@link Assumptions#assumeFalse}:
 * that table is produced by a generator, and when it has not been generated
 * {@link Constant#stages()} is an empty table (deliberately designed not to drag down the whole
 * test suite — see the notes on {@code Constant.stages()}).
 */
public class WaveManagerTest {

    /**
     * The data layer: each element of {@code monster} is one wave, and the ids within a wave are read
     * sorted by the N of {@code MonsterN}.
     */
    @Test
    public void stageBeanReadsWavesAndMonsterOrder() {
        StageBean stage = new StageBean("Mainline", 1, 29, List.of(
                wave(10, 20, 30),
                wave(40)));

        Assertions.assertEquals(2, stage.waveCount(), "two elements of monster = two waves");
        Assertions.assertEquals(List.of(10, 20, 30), stage.monsterIds(0));
        Assertions.assertEquals(List.of(40), stage.monsterIds(1));
        Assertions.assertEquals(List.of(), stage.monsterIds(2), "out of range gives an empty list, does not throw");
        Assertions.assertEquals(List.of(), stage.monsterIds(-1), "a negative index also gives an empty list");
    }

    /**
     * The data layer: the real {@code stage.json} — stage 103201 is 1 wave of 3.
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
     * The data layer: a multi-wave stage (310030 has 3 waves) — the wave count comes directly from
     * the number of elements of {@code monster}.
     */
    @Test
    public void multiWaveStageIsReadAsMultipleWaves() {
        StageBean stage = stage(310030);

        Assertions.assertTrue(stage.waveCount() >= 2, "310030 should be a multi-wave stage");
        Assertions.assertFalse(stage.monsterIds(0).isEmpty());
        Assertions.assertFalse(stage.monsterIds(1).isEmpty());
    }

    /**
     * Entering a wave: the monsters are created, added to the enemy list, and queued to enter.
     */
    @Test
    public void nextWaveSpawnsTheWaveAndQueuesIt() {
        Battle battle = waveBattle(103201);
        WaveManager waves = battle.getWaveManager();

        Assertions.assertTrue(waves.hasNextWave(), "wave 1 has not been entered yet");
        Assertions.assertEquals(-1, waves.getWaveIndex(), "not a single wave has been entered yet");

        Assertions.assertTrue(waves.nextWave());
        Assertions.assertEquals(0, waves.getWaveIndex());
        Assertions.assertEquals(3, battle.enemies.size(), "103201 wave 1 has 3 monsters");
        Assertions.assertEquals(3, battle.addRequestItems.size(), "all queued waiting to enter");

        battle.processRequests();
        Assertions.assertEquals(4, battle.queue.size(),
                "after entering they are in the action bar: 1 character + 3 monsters (the character joined the queue at construction)");
        Assertions.assertEquals(3, battle.targetableEnemies().size());
    }

    /**
     * The standard usage: enter a wave after {@code startBattle()}, and the action bar holds
     * "our side + this wave's monsters".
     */
    @Test
    public void wavesJoinTheQueueAlongsideTheParty() {
        Battle battle = waveBattle(103201);
        battle.startBattle();
        Assertions.assertEquals(1, battle.queue.size(), "at the start only our 1 character");

        battle.getWaveManager().nextWave();
        battle.processRequests();

        Assertions.assertEquals(4, battle.queue.size(), "1 character + 3 monsters");
        Assertions.assertEquals(3, battle.targetableEnemies().size());
    }

    /**
     * A single-wave stage: once entered, there is no next wave.
     */
    @Test
    public void singleWaveStageHasNoNextWave() {
        Battle battle = waveBattle(103201);
        WaveManager waves = battle.getWaveManager();

        Assertions.assertTrue(waves.nextWave());
        Assertions.assertFalse(waves.hasNextWave());
        Assertions.assertFalse(waves.nextWave(), "returns false when there is no next wave");
        Assertions.assertEquals(3, battle.enemies.size(), "monsters are not spawned twice");
    }

    /**
     * A multi-wave stage: enter them one at a time, and each time only that wave's monsters are added.
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
                "the second wave is **appended**, not a replacement");
        Assertions.assertEquals(1, waves.getWaveIndex());
    }

    /**
     * The core seam: **an empty enemy team ≠ a win**. While waves remain unentered, {@code checkResult()}
     * must not judge a win.
     *
     * <p>This is the easiest place to trip between P7-3 and P7-4: P7-3's criterion is "one side is
     * wiped out", whereas in wave mode "the enemy team is empty" merely means **this wave has not
     * entered yet**.
     */
    @Test
    public void pendingWavesDoNotCountAsAWonBattle() {
        Battle battle = waveBattle(310030);

        Assertions.assertTrue(battle.getWaveManager().hasPendingWaves());
        battle.startBattle();

        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus(),
                "the enemy team is empty, but waves remain unentered → must not judge a win");
        Assertions.assertEquals(Battle.Status.RUNNING, battle.checkResult());
    }

    /**
     * The battle is only judged won after the last wave is cleared.
     */
    @Test
    public void theBattleIsWonOnlyAfterTheLastWave() {
        Battle battle = waveBattle(103201);
        WaveManager waves = battle.getWaveManager();
        battle.startBattle();
        waves.nextWave();
        battle.processRequests();

        // Kill the whole wave
        for (Enemy enemy : battle.enemyUnits()) {
            enemy.takeDamage(999_999_999);
        }
        battle.processRequests();

        Assertions.assertFalse(waves.hasNextWave(), "there is only one wave and it has already been entered");
        Assertions.assertEquals(Battle.Status.WIN, battle.getStatus());
    }

    /**
     * In a multi-wave stage, wiping out wave 1 does **not** judge a win; after entering wave 2 the
     * battle continues.
     */
    @Test
    public void clearingAnIntermediateWaveDoesNotEndTheBattle() {
        Battle battle = waveBattle(310030);
        WaveManager waves = battle.getWaveManager();
        battle.startBattle();
        waves.nextWave();
        battle.processRequests();

        for (Enemy enemy : battle.enemyUnits()) {
            enemy.takeDamage(999_999_999);
        }
        battle.processRequests();

        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus(),
                "wave 2 is still to come → must not judge a win");
        Assertions.assertTrue(waves.isCurrentWaveCleared());

        int before = battle.enemies.size();
        Assertions.assertTrue(waves.nextWave());
        battle.processRequests();

        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus(), "a new wave has entered, keep fighting");
        Assertions.assertTrue(battle.enemies.size() > before);
        Assertions.assertFalse(waves.isCurrentWaveCleared(), "the new wave is alive");
        Assertions.assertFalse(waves.aliveEnemies().isEmpty());
    }

    /**
     * When our side is wiped out, it is a loss even if waves remain unentered — "should we enter
     * another wave" cannot save a team wipe.
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
     * Monsters entering mid-battle start from the **current action value**, rather than resetting to
     * 0 and starting a new round.
     */
    @Test
    public void lateWaveEnemiesEnterFromTheCurrentActionValue() {
        Battle battle = waveBattle(103201);
        WaveManager waves = battle.getWaveManager();
        battle.startBattle();

        battle.stepForward();
        battle.afterMove();                              // the clock has advanced a bit (the 132-speed enemy acts first)
        double elapsedBefore = battle.queue.getElapsed();
        Assertions.assertTrue(elapsedBefore > 0);

        waves.nextWave();
        battle.processRequests();

        for (Enemy enemy : battle.enemyUnits()) {
            double remaining = battle.queue.getTimeRemaining(signalOf(battle, enemy));
            Assertions.assertTrue(remaining > 0 && remaining < Double.MAX_VALUE,
                    "a new monster's remaining action value is positive and finite");
            Assertions.assertTrue(battle.queue.getElapsed() >= elapsedBefore,
                    "spawning monsters does not wind the clock back");
        }
    }

    /**
     * An ordinary battle with no wave manager is unaffected (P7-3's behavior is unchanged).
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
     * The stage table is parsed at most once; repeated access hits the cache.
     *
     * <p>The lazy-loading guardrail ("the static block does not parse stage.json") is in
     * {@link StageLazyLoadTest} — that is a separate class because other cases in this class would
     * load the table first.
     */
    @Test
    public void stageTableIsLoadedAtMostOnceAndCached() {
        Map<Integer, StageBean> first = Constant.stages();
        Map<Integer, StageBean> second = Constant.stages();

        Assertions.assertSame(first, second, "repeated access must hit the same cache, not re-parse");
        Assertions.assertEquals(1, Constant.stageLoadAttempts(),
                "stage.json is parsed only once, actually " + Constant.stageLoadAttempts() + " times");
    }

    /**
     * The stage table has at least 10,000 stages (a data-integrity sanity check).
     */
    @Test
    public void stageTableHasTheWholeDataSet() {
        Assumptions.assumeFalse(Constant.stages().isEmpty(), "stage.json has not been generated");

        Assertions.assertTrue(Constant.stages().size() > 10_000,
                "stage.json should have tens of thousands of stages, actual " + Constant.stages().size());
    }

    // ==================================================================

    /** Builds a battle with "an empty enemy team + a wave manager" (P7-4's standard usage). */
    private static Battle waveBattle(int stageId) {
        StageBean stage = stage(stageId);
        Battle battle = new Battle(List.of(character("hero", 100)), new ArrayList<>(), new Random(0));
        new WaveManager(battle, stage);
        return battle;
    }

    /** Fetches a stage; skips if there is no data (stage.json is produced by a generator — see the class docs). */
    private static StageBean stage(int stageId) {
        StageBean stage = Constant.stages().get(stageId);
        Assumptions.assumeFalse(Constant.stages().isEmpty(),
                "missing stage.json (generator output), skipping the stage-related assertions");
        Assertions.assertNotNull(stage, "stage.json should contain " + stageId);
        return stage;
    }

    /** Builds one wave in {@code Monster0..N} order (simulating the LinkedHashMap Gson reads). */
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
                .orElseThrow(() -> new AssertionError(enemy.getName() + " is not in the action bar"));
    }
}
