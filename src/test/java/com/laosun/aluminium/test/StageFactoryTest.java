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
 * P7-5 acceptance: {@code StageFactory.load(stageId)} assembles, from a stage, a battle that can be
 * fought immediately.
 *
 * <p>Covers:
 * <ol>
 *   <li>Difficulty comes from the stage ({@code hard_level_group} + {@code level} fed to
 *   {@code EnemyFactory});</li>
 *   <li>Wave 1 is already in play and **queued into the action bar** (spawning is queued entry, so
 *   {@code processRequests()} is easy to miss);</li>
 *   <li>The status is already {@code RUNNING} (not {@code NOT_STARTED}), so full turns can be run;</li>
 *   <li>The error paths: unknown stage / empty team.</li>
 * </ol>
 *
 * <p>⚠ Cases that depend on {@code stage.json} (generator output, not committed) fall back to assume.
 */
public class StageFactoryTest {

    /**
     * The acceptance in the plan: {@code load(103201)} can be fought, has 3 monsters, and its status
     * is no longer NOT_STARTED.
     */
    @Test
    public void loadBuildsARunnableBattle() {
        assumeStageData();
        Battle battle = StageFactory.load(103201);

        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus(),
                "the battle produced by load should already be underway");
        Assertions.assertEquals(3, battle.enemies.size(), "103201 wave 1 has 3 monsters");
        Assertions.assertEquals(0, battle.getWaveManager().getWaveIndex(), "wave 1 has been entered");

        int expectedQueueSize = battle.characters.size() + battle.enemies.size();
        Assertions.assertEquals(expectedQueueSize, battle.queue.size(),
                "our side + wave 1's monsters should all be in the action bar (missing processRequests loses people)");
    }

    /**
     * Difficulty comes from the stage: the same monster has different stat sheets in stages of
     * different level.
     *
     * <p>This is the core of P7-5 — "hard" is decided by the data, not by multipliers the caller
     * passes in.
     */
    @Test
    public void difficultyComesFromTheStage() {
        assumeStageData();
        // 103201: level 29; 103203: level 60 (both have hard_level_group 1)
        StageBean low = StageFactory.requireStage(103201);
        StageBean high = StageFactory.requireStage(103203);

        Assertions.assertTrue(high.level() > low.level(), "the two chosen stages should have different levels");

        Enemy lowEnemy = battle(103201).enemies.getFirst();
        Enemy highEnemy = battle(103203).enemies.getFirst();

        Assertions.assertTrue(highEnemy.getMaxHp() > lowEnemy.getMaxHp(),
                "a monster in a higher-level stage should have more HP (low: " + lowEnemy.getMaxHp()
                        + ", high: " + highEnemy.getMaxHp() + ")");
    }

    /**
     * The monsters in the stage really are the ids / count written in the stage data.
     */
    @Test
    public void enemiesMatchTheStageData() {
        assumeStageData();
        StageBean stage = StageFactory.requireStage(103201);
        Battle battle = StageFactory.load(103201, StageFactory.realTeam(), new Random(1));

        Assertions.assertEquals(stage.monsterIds(0).size(), battle.enemies.size());
        for (Enemy enemy : battle.enemies) {
            Assertions.assertFalse(enemy.isDeath());
            Assertions.assertTrue(enemy.getMaxHp() > 0);
            Assertions.assertEquals(stage.level(), enemy.getLevel(),
                    "the monster's level should come from the stage");
        }
    }

    /**
     * A fixed seed → the whole battle is reproducible (the same stage + the same seed gives the same
     * result from two assemblies).
     */
    @Test
    public void fixedSeedMakesTheBattleReproducible() {
        assumeStageData();
        Battle first = StageFactory.load(103201, StageFactory.realTeam(), new Random(42));
        Battle second = StageFactory.load(103201, StageFactory.realTeam(), new Random(42));

        Assertions.assertEquals(describe(first), describe(second),
                "two assemblies with the same seed should be identical");
    }

    /**
     * Runs several full turns without blowing up, with the status staying RUNNING / terminal.
     */
    @Test
    public void theBattleCanRunTurns() {
        assumeStageData();
        Battle battle = StageFactory.load(103201, StageFactory.realTeam(), new Random(7));

        for (int i = 0; i < 5 && !battle.isOver(); i++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            battle.beforeMove();
            battle.afterMove();
        }

        Assertions.assertTrue(battle.queue.getElapsed() > 0, "the clock should have advanced");
        Assertions.assertNotEquals(Battle.Status.NOT_STARTED, battle.getStatus());
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
        Assertions.assertEquals(0, waves.getWaveIndex());

        for (Enemy enemy : battle.enemies) {
            enemy.takeDamage(999_999_999);
        }
        battle.processRequests();

        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus(), "a next wave remains → no win judged");

        int before = battle.enemies.size();
        Assertions.assertTrue(waves.nextWave());
        battle.processRequests();

        Assertions.assertTrue(battle.enemies.size() > before, "wave 2 is appended");
        Assertions.assertEquals(1, waves.getWaveIndex());
        Assertions.assertEquals(Battle.Status.RUNNING, battle.getStatus());
    }

    /**
     * The reference team (P8-5): 4 real characters, each with an identity, an energy bar and a light
     * cone of its own path.
     *
     * <p>Replaces the old P7-5 assertion, which checked 3 placeholders with deliberately distinct
     * speeds. The real roster happens to have distinct speeds too, so the action bar stays meaningful.
     */
    @Test
    public void realTeamIsUsable() {
        List<Character> team = StageFactory.realTeam();

        Assertions.assertEquals(4, team.size(), "the reference team is 4 characters");
        Assertions.assertEquals(4, team.stream()
                        .map(c -> c.getAttribute(com.laosun.aluminium.enums.AttributeType.SPEED).get())
                        .distinct().count(),
                "the speeds should all differ, so the action bar is meaningful");
        for (Character c : team) {
            Assertions.assertTrue(c.getMaxHp() > 0);
            Assertions.assertNotNull(c.getElement(), c.getName() + " must have a real element");
            Assertions.assertNotNull(c.getPath(), c.getName() + " must have a real path");
            Assertions.assertTrue(c.hasEnergyBar(),
                    c.getName() + " should have an energy bar (otherwise no ultimate can be cast)");
            Assertions.assertFalse(c.isDeath());
            Assertions.assertNotNull(c.getWeapon(), c.getName() + " should carry a light cone");
        }
    }

    /**
     * The caller can supply their own team (P8-5 swapping in the real team goes this way).
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
     * The error paths: an unknown stage gives a self-explanatory message; an empty team is also rejected.
     */
    @Test
    public void invalidInputsAreRejected() {
        assumeStageData();

        IllegalArgumentException unknown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> StageFactory.load(-1));
        Assertions.assertTrue(unknown.getMessage().contains("-1"), unknown.getMessage());

        IllegalArgumentException emptyTeam = Assertions.assertThrows(IllegalArgumentException.class,
                () -> StageFactory.load(103201, List.of(), new Random(0)));
        Assertions.assertTrue(emptyTeam.getMessage().contains("team"), emptyTeam.getMessage());

        Assertions.assertFalse(StageFactory.hasStage(-1));
        Assertions.assertTrue(StageFactory.hasStage(103201));
    }

    // ==================================================================

    /** The stage data is not in the repository; skip when missing (see the generator section of the README). */
    private static void assumeStageData() {
        Assumptions.assumeFalse(Constant.stages().isEmpty(),
                "missing stage.json (generator output), skipping the StageFactory-related assertions");
    }

    private static Battle battle(int stageId) {
        return StageFactory.load(stageId, StageFactory.realTeam(), new Random(0));
    }

    /** Flattens a battle's visible state into a string, for comparing whether two assemblies are identical. */
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
