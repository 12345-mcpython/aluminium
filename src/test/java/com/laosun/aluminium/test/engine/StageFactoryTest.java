package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.StageBean;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.battle.WaveManager;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.utils.StageFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Acceptance: {@code StageFactory.load(stageId)} assembles, from a stage, a battle that can be
 * fought immediately.
 *
 * <p>Covers:
 * <ol>
 *   <li>Difficulty comes from the stage ({@code hard_level_group} + {@code level} fed to
 *   {@code EnemyFactory});</li>
 *   <li>Wave 1 is already in play and queued into the action bar (spawning is queued entry, so
 *   {@code processRequests()} is easy to miss);</li>
 *   <li>The status is already {@code RUNNING} (not {@code NOT_STARTED}), so full turns can be run;</li>
 *   <li>Assembly is reproducible from a fixed seed;</li>
 *   <li>The reference team is usable, and a caller-supplied team is honoured;</li>
 *   <li>The error paths: unknown stage / empty team.</li>
 * </ol>
 *
 * <p>Scenario tests group only the clauses that share a fixture: every stage-103201 clause is one
 * scenario (one assembled battle family), while the multi-wave stage, the team-building cases and the
 * rejection paths keep their own fixtures.
 *
 * <p>Note: Cases that depend on {@code stage.json} (generator output, not committed) fall back to assume.
 */
public class StageFactoryTest {

    /**
     * Scenario: loading stage 103201 with the reference team gives a battle that is already under way,
     * whose monsters are the ones the stage data declares, that is reproducible from a fixed seed and
     * that can be advanced through full turns.
     *
     * <p>Merged from the four single-clause cases that share the stage-103201 fixture and observe the
     * same assembled battle: the runnable-battle acceptance case, the stage-data case, the fixed-seed
     * case and the run-full-turns case. Each clause is checked in {@code assertAll} with its own
     * message.
     */
    @Test
    public void loadedBattleIsReadyReproducibleAndPlayable() {
        assumeStageData();

        // Clause 1: the acceptance in the plan - load(103201) can be fought, has 3 monsters, and its
        // status is no longer NOT_STARTED.
        Battle battle = StageFactory.load(103201);
        // Summons stand on our side and take a queue entry too, so the count is per COMBATANT, not per character.
        int expectedQueueSize = battle.allies().size() + battle.enemies().size();

        // Clause 2: the monsters in the stage really are the ids / count / level written in the stage data.
        StageBean stage = StageFactory.requireStage(103201);
        Battle stageBattle = StageFactory.load(103201, StageFactory.realTeam(), new Random(1));

        Assertions.assertAll("load(103201): the battle is already under way and its content comes from the stage",
                () -> Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus(),
                        "the battle produced by load should already be underway"),
                () -> Assertions.assertEquals(3, battle.enemies().size(), "103201 wave 1 has 3 monsters"),
                () -> Assertions.assertEquals(0, battle.getWaveManager().getWaveIndex(), "wave 1 has been entered"),
                () -> Assertions.assertEquals(expectedQueueSize, battle.queue().size(),
                        "our side + wave 1's monsters should all be in the action bar (missing processRequests loses people)"),
                () -> Assertions.assertEquals(stage.monsterIds(0).size(), stageBattle.enemies().size(),
                        "wave 1 should hold exactly the monster count the stage data declares"),
                () -> {
                    for (Enemy enemy : stageBattle.enemyUnits()) {
                        Assertions.assertAll(enemy.getName(),
                                () -> Assertions.assertFalse(enemy.isDeath(),
                                        "a monster should be alive when the battle starts"),
                                () -> Assertions.assertTrue(enemy.getMaxHp() > 0,
                                        "a monster should have positive max HP"),
                                () -> Assertions.assertEquals(stage.level(), enemy.getLevel(),
                                        "the monster's level should come from the stage"));
                    }
                });

        // Clause 3: a fixed seed to the whole battle is reproducible (the same stage + the same seed
        // gives the same result from two assemblies).
        Battle first = StageFactory.load(103201, StageFactory.realTeam(), new Random(42));
        Battle second = StageFactory.load(103201, StageFactory.realTeam(), new Random(42));

        Assertions.assertAll("load(103201): a fixed seed makes the battle reproducible",
                () -> Assertions.assertEquals(describe(first), describe(second),
                        "two assemblies with the same seed should be identical"));

        // Clause 4: runs several full turns without blowing up, with the status staying RUNNING / terminal.
        Battle played = StageFactory.load(103201, StageFactory.realTeam(), new Random(7));
        for (int i = 0; i < 5 && !played.isOver(); i++) {
            played.stepForward();
            if (played.currentMove() == null) {
                break;
            }
            played.beforeMove();
            played.afterMove();
        }

        Assertions.assertAll("load(103201): the battle can run turns",
                () -> Assertions.assertTrue(played.queue().getElapsed() > 0, "the clock should have advanced"),
                () -> Assertions.assertNotEquals(Battle.Status.NOT_STARTED, played.getStatus(),
                        "the status should stay RUNNING (or become terminal) once turns have run"));
    }

    /**
     * Difficulty comes from the stage: the same monster has different stat sheets in stages of
     * different level.
     *
     * <p>This is the core requirement - "hard" is decided by the data, not by multipliers the caller
     * passes in.
     */
    @Test
    public void difficultyComesFromTheStage() {
        assumeStageData();
        // 103201: level 29; 103203: level 60 (both have hard_level_group 1)
        StageBean low = StageFactory.requireStage(103201);
        StageBean high = StageFactory.requireStage(103203);

        Enemy lowEnemy = battle(103201).enemyUnits().getFirst();
        Enemy highEnemy = battle(103203).enemyUnits().getFirst();

        Assertions.assertAll("difficulty is decided by the stage data",
                () -> Assertions.assertTrue(high.level() > low.level(),
                        "the two chosen stages should have different levels"),
                () -> Assertions.assertTrue(highEnemy.getMaxHp() > lowEnemy.getMaxHp(),
                        "a monster in a higher-level stage should have more HP (low: " + lowEnemy.getMaxHp()
                                + ", high: " + highEnemy.getMaxHp() + ")"));
    }

    /**
     * A multi-wave stage: after clearing wave 1, wave 2 can be entered, and no win is judged.
     */
    @Test
    public void multiWaveStageCanAdvanceToTheNextWave() {
        assumeStageData();
        StageBean stage = StageFactory.requireStage(310030);
        Assumptions.assumeTrue(stage.waveCount() >= 2, "310030 should be a multi-wave stage");

        Battle battle = StageFactory.load(310030, StageFactory.realTeam(), new Random(3));
        WaveManager waves = battle.getWaveManager();
        int initialWaveIndex = waves.getWaveIndex();

        for (Enemy enemy : battle.enemyUnits()) {
            enemy.takeDamage(999_999_999);
        }
        battle.processRequests();

        Battle.Status statusBeforeNextWave = battle.getStatus();
        int enemiesBeforeNextWave = battle.enemies().size();

        Assertions.assertAll("310030: wave 1 is cleared and no win is judged",
                () -> Assertions.assertEquals(0, initialWaveIndex, "the battle starts in wave 1"),
                () -> Assertions.assertEquals(Battle.Status.RUNNING, statusBeforeNextWave,
                        "a next wave remains → no win judged"));

        boolean advanced = waves.nextWave();
        battle.processRequests();
        int enemiesAfterNextWave = battle.enemies().size();
        int waveIndexAfterNextWave = waves.getWaveIndex();
        Battle.Status statusAfterNextWave = battle.getStatus();

        Assertions.assertAll("310030: wave 2 is entered",
                () -> Assertions.assertTrue(advanced, "nextWave() should enter wave 2"),
                () -> Assertions.assertTrue(enemiesAfterNextWave > enemiesBeforeNextWave, "wave 2 is appended"),
                () -> Assertions.assertEquals(1, waveIndexAfterNextWave, "the wave index should advance to wave 2"),
                () -> Assertions.assertEquals(Battle.Status.RUNNING, statusAfterNextWave,
                        "the battle keeps running in wave 2"));
    }

    /**
     * The reference team: 4 real characters, each with an identity, an energy bar and a light
     * cone of its own path.
     *
     * <p>Replaces the old assertion, which checked 3 placeholders with deliberately distinct
     * speeds. The real roster happens to have distinct speeds too, so the action bar stays meaningful.
     *
     * <p>Kept out of the stage scenario on purpose: it needs no {@code stage.json}, so it must never
     * become a skipped test.
     */
    @Test
    public void referenceTeamIsUsable() {
        List<Character> team = StageFactory.realTeam();

        Assertions.assertAll("the reference team is usable",
                () -> Assertions.assertEquals(4, team.size(), "the reference team is 4 characters"),
                () -> Assertions.assertEquals(4, team.stream()
                                .map(c -> c.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get())
                                .distinct().count(),
                        "the speeds should all differ, so the action bar is meaningful"),
                () -> {
                    for (Character c : team) {
                        Assertions.assertAll(c.getName(),
                                () -> Assertions.assertTrue(c.getMaxHp() > 0, "max HP should be positive"),
                                () -> Assertions.assertNotNull(c.getElement(), c.getName() + " must have a real element"),
                                () -> Assertions.assertNotNull(c.getPath(), c.getName() + " must have a real path"),
                                () -> Assertions.assertTrue(c.hasEnergyBar(),
                                        c.getName() + " should have an energy bar (otherwise no ultimate can be cast)"),
                                () -> Assertions.assertFalse(c.isDeath(), "a fresh character should not be dead"),
                                () -> Assertions.assertNotNull(c.getWeapon(), c.getName() + " should carry a light cone"));
                    }
                });
    }

    /**
     * The caller can supply their own team (this is how the real team is swapped in).
     *
     * <p>Its own fixture: a one-character placeholder team, not the reference roster, so it stays out
     * of both the stage scenario and the reference-team case.
     */
    @Test
    public void callersCanSupplyTheirOwnTeam() {
        assumeStageData();
        List<Character> team = new ArrayList<>();
        team.add(Character.fromAttributes("solo", 50_000, 200, 200, 150));

        Battle battle = StageFactory.load(103201, team, new Random(0));

        Assertions.assertAll("load(103201, caller team): the caller-supplied team is honoured",
                () -> Assertions.assertEquals(1, battle.characters().size(),
                        "only the caller's single character is fielded"),
                () -> Assertions.assertEquals("solo", battle.characters().getFirst().getName(),
                        "the caller's character is the one fighting"),
                () -> Assertions.assertEquals(1 + battle.enemies().size(), battle.queue().size(),
                        "the caller's character + wave 1's monsters should all be in the action bar"));
    }

    /**
     * The error paths: an unknown stage gives a self-explanatory message; an empty team is also rejected.
     *
     * <p>Rejection paths stay in their own test, never mixed into the positive scenarios above.
     */
    @Test
    public void invalidInputsAreRejected() {
        assumeStageData();

        IllegalArgumentException unknown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> StageFactory.load(-1), "an unknown stage id should be rejected");
        IllegalArgumentException emptyTeam = Assertions.assertThrows(IllegalArgumentException.class,
                () -> StageFactory.load(103201, List.of(), new Random(0)), "an empty team should be rejected");

        Assertions.assertAll("invalid inputs are rejected",
                () -> Assertions.assertTrue(unknown.getMessage().contains("-1"), unknown.getMessage()),
                () -> Assertions.assertTrue(emptyTeam.getMessage().contains("team"), emptyTeam.getMessage()),
                () -> Assertions.assertFalse(StageFactory.hasStage(-1), "hasStage(-1) should be false"),
                () -> Assertions.assertTrue(StageFactory.hasStage(103201), "hasStage(103201) should be true"));
    }

    // ==================================================================

    /**
     * The stage data is not in the repository; skip when missing (see the generator section of the README).
     */
    private static void assumeStageData() {
        Assumptions.assumeFalse(Constant.stages().isEmpty(),
                "missing stage.json (generator output), skipping the StageFactory-related assertions");
    }

    private static Battle battle(int stageId) {
        return StageFactory.load(stageId, StageFactory.realTeam(), new Random(0));
    }

    /**
     * Flattens a battle's visible state into a string, for comparing whether two assemblies are identical.
     */
    private static String describe(Battle battle) {
        StringBuilder text = new StringBuilder();
        text.append("elapsed=").append(battle.queue().getElapsed())
                .append(" status=").append(battle.getStatus())
                .append(" round=").append(battle.getRound())
                .append('\n');
        for (var signal : battle.queue().snapshot()) {
            text.append(signal.getCanHit().getName())
                    .append('@').append(signal.getNextActionTime())
                    .append(" speed=").append(signal.getSpeed())
                    .append('\n');
        }
        for (Enemy enemy : battle.enemyUnits()) {
            text.append(enemy.getName()).append(" hp=").append(enemy.getMaxHp()).append('\n');
        }
        return text.toString();
    }
}
