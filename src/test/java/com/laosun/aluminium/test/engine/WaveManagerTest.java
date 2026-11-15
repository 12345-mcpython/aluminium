package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.StageBean;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.battle.WaveManager;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.test.data.StageLazyLoadTest;
import com.laosun.aluminium.test.support.BattleFixture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.laosun.aluminium.Signal;

/**
 * P-4 acceptance: stage data ({@code stage.json}) + waves.
 *
 * <p>Covers two kinds of thing:
 * <ol>
 *   <li>The data layer: how {@link StageBean} is read (a wave = an element of {@code monster}; the
 *   order within a wave = the N order of {@code MonsterN});</li>
 *   <li>The flow layer: {@link WaveManager} spawning monsters wave by wave, and an empty enemy
 *   team does not equal a win (the seam between P-3 x P-4).</li>
 * </ol>
 *
 * <p>Each test method below is one scenario: the clauses that share a fixture, a clause or an
 * observed object are merged into one method and their assertions run together inside
 * {@link Assertions#assertAll}. Sub-scenarios that build their own battle still get their own
 * battle, so no clause is weakened by borrowing another clause's state.
 *
 * <p>Note: Tests that depend on {@code stage.json} fall back to {@link Assumptions#assumeFalse}:
 * that table is produced by a generator, and when it has not been generated
 * {@link Constant#stages()} is an empty table (deliberately designed not to drag down the whole
 * test suite - see the notes on {@code Constant.stages()}).
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

        Assertions.assertAll("StageBean reads monster elements as waves",
                () -> Assertions.assertEquals(2, stage.waveCount(), "two elements of monster = two waves"),
                () -> Assertions.assertEquals(List.of(10, 20, 30), stage.monsterIds(0),
                        "wave 0 keeps the MonsterN order"),
                () -> Assertions.assertEquals(List.of(40), stage.monsterIds(1),
                        "wave 1 keeps the MonsterN order"),
                () -> Assertions.assertEquals(List.of(), stage.monsterIds(2),
                        "out of range gives an empty list, does not throw"),
                () -> Assertions.assertEquals(List.of(), stage.monsterIds(-1),
                        "a negative index also gives an empty list"));
    }

    /**
     * The data layer: the real {@code stage.json} has the expected shape - stage 103201 is 1 wave of
     * 3 at level 29 / hard level group 1, and 310030 is a multi-wave stage whose elements are waves.
     */
    @Test
    public void stageJsonReadsTheExpectedWaveShape() {
        StageBean singleWave = stage(103201);
        StageBean multiWave = stage(310030);

        Assertions.assertAll("stage.json wave shape",
                () -> Assertions.assertEquals(1, singleWave.waveCount(), "103201 is a single wave"),
                () -> Assertions.assertEquals(3, singleWave.monsterIds(0).size(),
                        "103201 wave 0 has 3 monsters"),
                () -> Assertions.assertEquals(29, singleWave.level(), "103201 level is 29"),
                () -> Assertions.assertEquals(1, singleWave.hardLevelGroup(),
                        "103201 hard level group is 1"),
                () -> Assertions.assertTrue(multiWave.waveCount() >= 2,
                        "310030 should be a multi-wave stage"),
                () -> Assertions.assertFalse(multiWave.monsterIds(0).isEmpty(),
                        "310030 wave 0 is not empty"),
                () -> Assertions.assertFalse(multiWave.monsterIds(1).isEmpty(),
                        "310030 wave 1 is not empty"));
    }

    /**
     * Entering wave 1 of a single-wave stage (103201): the monsters are created, added to the enemy
     * list and queued to enter; after entry they sit in the action bar next to the party. The same
     * wave is also entered after {@code startBattle()}, where the party is alone at first.
     */
    @Test
    public void enteringTheFirstWaveQueuesItAlongsideTheParty() {
        Battle battle = waveBattle(103201);
        WaveManager waves = battle.getWaveManager();

        Assertions.assertAll("before the first wave is entered",
                () -> Assertions.assertTrue(waves.hasNextWave(), "wave 1 has not been entered yet"),
                () -> Assertions.assertEquals(-1, waves.getWaveIndex(),
                        "not a single wave has been entered yet"));

        Assertions.assertTrue(waves.nextWave(), "there is a wave 1 to enter");
        Assertions.assertAll("entering wave 1 spawns and queues its monsters",
                () -> Assertions.assertEquals(0, waves.getWaveIndex(),
                        "the first entered wave sets the index to 0"),
                () -> Assertions.assertEquals(3, battle.enemies().size(),
                        "103201 wave 1 has 3 monsters"),
                () -> Assertions.assertEquals(3, battle.pendingCombatants().size(),
                        "all queued waiting to enter"));

        battle.processRequests();
        Assertions.assertAll("the entered wave is in the action bar",
                () -> Assertions.assertEquals(4, battle.queue().size(),
                        "after entering they are in the action bar: 1 character + 3 monsters (the character joined the queue at construction)"),
                () -> Assertions.assertEquals(3, battle.targetableEnemies().size(),
                        "all 3 monsters are targetable"));

        Battle startedBattle = waveBattle(103201);
        startedBattle.startBattle();
        Assertions.assertAll("the party is alone in the queue before any wave",
                () -> Assertions.assertEquals(1, startedBattle.queue().size(),
                        "at the start only our 1 character"));

        startedBattle.getWaveManager().nextWave();
        startedBattle.processRequests();
        Assertions.assertAll("the wave joins the queue alongside the party",
                () -> Assertions.assertEquals(4, startedBattle.queue().size(),
                        "1 character + 3 monsters"),
                () -> Assertions.assertEquals(3, startedBattle.targetableEnemies().size(),
                        "the 3 monsters of the wave are targetable"));
    }

    /**
     * A single-wave stage (103201): once its only wave is entered there is no next wave, and the
     * battle is judged won only after that wave is cleared.
     */
    @Test
    public void singleWaveStageHasNoNextWaveAndWinsWhenCleared() {
        Battle battle = waveBattle(103201);
        WaveManager waves = battle.getWaveManager();

        Assertions.assertTrue(waves.nextWave(), "the only wave is entered");
        Assertions.assertFalse(waves.nextWave(), "returns false when there is no next wave");
        Assertions.assertAll("the single wave is the last wave",
                () -> Assertions.assertFalse(waves.hasNextWave(),
                        "there is no next wave after the only one"),
                () -> Assertions.assertEquals(3, battle.enemies().size(),
                        "monsters are not spawned twice"));

        Battle winBattle = waveBattle(103201);
        WaveManager winWaves = winBattle.getWaveManager();
        winBattle.startBattle();
        winWaves.nextWave();
        winBattle.processRequests();

        // Kill the whole wave
        for (Enemy enemy : winBattle.enemyUnits()) {
            enemy.takeDamage(999_999_999);
        }
        winBattle.processRequests();

        Assertions.assertAll("the battle is won only after the last wave is cleared",
                () -> Assertions.assertFalse(winWaves.hasNextWave(),
                        "there is only one wave and it has already been entered"),
                () -> Assertions.assertEquals(Battle.Status.WIN, winBattle.getStatus(),
                        "the only wave is cleared, so the battle is won"));
    }

    /**
     * A multi-wave stage (310030) from spawn to result: one wave is entered at a time and appended,
     * pending waves are not a win, clearing an intermediate wave keeps the battle running, and a
     * wiped party still loses while waves remain.
     */
    @Test
    public void multiWaveStageSpawnsOneWaveAtATimeAndJudgesTheResult() {
        // Waves are spawned one at a time; each nextWave() appends its own monsters.
        Battle spawnBattle = waveBattle(310030);
        WaveManager spawnWaves = spawnBattle.getWaveManager();

        StageBean stage = Constant.stages().get(310030);
        int firstWaveSize = stage.monsterIds(0).size();
        int secondWaveSize = stage.monsterIds(1).size();

        Assertions.assertTrue(spawnWaves.nextWave(), "wave 1 is entered");
        Assertions.assertEquals(firstWaveSize, spawnBattle.enemies().size(),
                "after wave 1 only wave 1's monsters are on the field");
        Assertions.assertTrue(spawnWaves.nextWave(), "wave 2 is entered");
        Assertions.assertAll("the second wave is appended to the first",
                () -> Assertions.assertEquals(firstWaveSize + secondWaveSize, spawnBattle.enemies().size(),
                        "the second wave is **appended**, not a replacement"),
                () -> Assertions.assertEquals(1, spawnWaves.getWaveIndex(),
                        "after the second wave the wave index is 1"));

        // The seam: pending waves are not a win.
        Battle pendingBattle = waveBattle(310030);
        pendingBattle.startBattle();
        Assertions.assertAll("pending waves do not count as a won battle",
                () -> Assertions.assertTrue(pendingBattle.getWaveManager().hasPendingWaves(),
                        "310030 still has un-entered waves"),
                () -> Assertions.assertEquals(Battle.Status.RUNNING, pendingBattle.getStatus(),
                        "the enemy team is empty, but waves remain un-entered → must not judge a win"),
                () -> Assertions.assertEquals(Battle.Status.RUNNING, pendingBattle.checkResult(),
                        "checkResult() still judges RUNNING while waves remain"));

        // Clearing wave 1 does not end the battle; entering wave 2 continues it.
        Battle clearBattle = waveBattle(310030);
        WaveManager clearWaves = clearBattle.getWaveManager();
        clearBattle.startBattle();
        clearWaves.nextWave();
        clearBattle.processRequests();

        for (Enemy enemy : clearBattle.enemyUnits()) {
            enemy.takeDamage(999_999_999);
        }
        clearBattle.processRequests();

        Assertions.assertAll("clearing an intermediate wave does not end the battle",
                () -> Assertions.assertEquals(Battle.Status.RUNNING, clearBattle.getStatus(),
                        "wave 2 is still to come → must not judge a win"),
                () -> Assertions.assertTrue(clearWaves.isCurrentWaveCleared(), "wave 1 is cleared"));

        int before = clearBattle.enemies().size();
        Assertions.assertTrue(clearWaves.nextWave(), "wave 2 can be entered");
        clearBattle.processRequests();

        Assertions.assertAll("a new wave has entered and the battle continues",
                () -> Assertions.assertEquals(Battle.Status.RUNNING, clearBattle.getStatus(),
                        "a new wave has entered, keep fighting"),
                () -> Assertions.assertTrue(clearBattle.enemies().size() > before,
                        "the field holds more monsters than before wave 2"),
                () -> Assertions.assertFalse(clearWaves.isCurrentWaveCleared(),
                        "the new wave is alive"),
                () -> Assertions.assertFalse(clearWaves.aliveEnemies().isEmpty(),
                        "the new wave has living enemies"));

        // A wiped party loses even with waves still pending.
        Battle wipeBattle = waveBattle(310030);
        wipeBattle.startBattle();
        for (Character c : wipeBattle.characters()) {
            c.takeDamage(999_999_999);
        }
        wipeBattle.processRequests();

        Assertions.assertAll("a wiped party still loses with pending waves",
                () -> Assertions.assertEquals(Battle.Status.LOSE, wipeBattle.getStatus(),
                        "party wiped → LOSE even though waves remain un-entered"));
    }

    /**
     * Monsters entering mid-battle start from the current action value, rather than resetting to
     * 0 and starting a new round.
     */
    @Test
    public void lateWaveEnemiesEnterFromTheCurrentActionValue() {
        Battle battle = waveBattle(103201);
        WaveManager waves = battle.getWaveManager();
        battle.startBattle();

        battle.stepForward();
        battle.afterMove();                              // the clock has advanced a bit (the 132-speed enemy acts first)
        double elapsedBefore = battle.queue().getElapsed();
        Assertions.assertAll("the clock has already advanced",
                () -> Assertions.assertTrue(elapsedBefore > 0,
                        "some action value has elapsed before the wave enters"));

        waves.nextWave();
        battle.processRequests();

        for (Enemy enemy : battle.enemyUnits()) {
            double remaining = battle.queue().getTimeRemaining(signalOf(battle, enemy));
            Assertions.assertAll("monster " + enemy.getName() + " enters from the current action value",
                    () -> Assertions.assertTrue(remaining > 0 && remaining < Double.MAX_VALUE,
                            "a new monster's remaining action value is positive and finite"),
                    () -> Assertions.assertTrue(battle.queue().getElapsed() >= elapsedBefore,
                            "spawning monsters does not wind the clock back"));
        }
    }

    /**
     * An ordinary battle with no wave manager is unaffected (P-3's behavior is unchanged).
     */
    @Test
    public void battlesWithoutWavesBehaveAsBefore() {
        Battle battle = new Battle(List.of(character()),
                List.of(EnemyFactory.create(1002011, 90, 1)), BattleFixture.seeded());

        Assertions.assertNull(battle.getWaveManager(), "a hand-built battle has no wave manager");
        battle.startBattle();

        Assertions.assertAll("a battle without waves behaves as before",
                () -> Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus(),
                        "with no pending waves the ordinary result check applies"),
                () -> Assertions.assertEquals(1, battle.enemies().size(),
                        "the enemy given at construction is still on the field"));
    }

    /**
     * The stage table is parsed at most once; repeated access hits the cache, and the table holds the
     * whole data set.
     *
     * <p>The lazy-loading guardrail ("the static block does not parse stage.json") is in
     * {@link StageLazyLoadTest} - that is a separate class because other cases in this class would
     * load the table first.
     *
     * <p>The cache clauses run before the "whole data set" premise, so a missing {@code stage.json}
     * only skips the size clause, not the cache clauses.
     */
    @Test
    public void stageTableIsLoadedAtMostOnceAndHasTheWholeDataSet() {
        Map<Integer, StageBean> first = Constant.stages();
        Map<Integer, StageBean> second = Constant.stages();

        Assertions.assertAll("the stage table is loaded at most once and cached",
                () -> Assertions.assertSame(first, second,
                        "repeated access must hit the same cache, not re-parse"),
                () -> Assertions.assertEquals(1, Constant.stageLoadAttempts(),
                        "stage.json is parsed only once, actually " + Constant.stageLoadAttempts() + " times"));

        Assumptions.assumeFalse(Constant.stages().isEmpty(), "stage.json has not been generated");

        Assertions.assertAll("the stage table has the whole data set",
                () -> Assertions.assertTrue(Constant.stages().size() > 10_000,
                        "stage.json should have tens of thousands of stages, actual " + Constant.stages().size()));
    }

    // ==================================================================

    /**
     * Builds a battle with "an empty enemy team + a wave manager" (P-4's standard usage).
     */
    private static Battle waveBattle(int stageId) {
        StageBean stage = stage(stageId);
        Battle battle = new Battle(List.of(character()), new ArrayList<>(), BattleFixture.seeded());
        new WaveManager(battle, stage);
        return battle;
    }

    /**
     * Fetches a stage; skips if there is no data (stage.json is produced by a generator - see the class docs).
     */
    private static StageBean stage(int stageId) {
        StageBean stage = Constant.stages().get(stageId);
        Assumptions.assumeFalse(Constant.stages().isEmpty(),
                "missing stage.json (generator output), skipping the stage-related assertions");
        Assertions.assertNotNull(stage, "stage.json should contain " + stageId);
        return stage;
    }

    /**
     * Builds one wave in {@code Monster0..N} order (simulating the LinkedHashMap Gson reads).
     */
    private static Map<String, Integer> wave(int... ids) {
        Map<String, Integer> wave = new LinkedHashMap<>();
        for (int i = 0; i < ids.length; i++) {
            wave.put("Monster" + i, ids[i]);
        }
        return wave;
    }

    private static Character character() {
        return Character.fromAttributes("hero", 10_000, 100, 100, 100);
    }

    private static com.laosun.aluminium.Signal signalOf(Battle battle, Enemy enemy) {
        return battle.queue().getHeap().stream()
                .filter(s -> s.getCanHit() == enemy)
                .findFirst()
                .orElseThrow(() -> new AssertionError(enemy.getName() + " is not in the action bar"));
    }
}
