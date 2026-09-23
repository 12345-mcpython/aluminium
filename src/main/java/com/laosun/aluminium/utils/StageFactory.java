package com.laosun.aluminium.utils;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.StageBean;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.WaveManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stage factory (P7-5): given a {@code stage_id}, assemble a {@link Battle} that is **ready to fight**.
 *
 * <pre>{@code
 * Battle battle = StageFactory.load(103201);
 * while (!battle.isOver()) {
 *     battle.stepForward();   // currentMove = the current actor
 *     battle.beforeMove();
 *     // …take the action…
 *     battle.afterMove();
 *     if (battle.getWaveManager().isCurrentWaveCleared()
 *             && battle.getWaveManager().hasNextWave()) {
 *         battle.getWaveManager().nextWave();
 *         battle.processRequests();
 *     }
 * }
 * }</pre>
 *
 * <p>Difficulty comes from the stage itself: {@code StageBean}'s {@code hardLevelGroup} +
 * {@code level} are fed straight to
 * {@link com.laosun.aluminium.models.EnemyFactory#create(int, int, int)}, so "the same monster is
 * not equally strong in different stages" is decided by the data and the caller does not need to
 * pass any multiplier.
 *
 * <p>⚠ <b>The team is temporary</b>: at the P7-5 stage there is no {@code CharacterFactory} yet
 * (P8-1), so {@link #load(int)} uses {@link #temporaryTeam()} to build a placeholder team.
 * Its lifetime ends at P8-5 — at that point it will be replaced by a real 4-character team. See
 * {@link #temporaryTeam()}.
 */
public final class StageFactory {
    /**
     * The placeholder characters' max energy: {@code CanHit.maxEnergy} defaults to 0, meaning "no
     * energy bar", and such a character can never cast an ultimate. Giving it a value is so that the
     * demo/stage can at least reach the ultimate branch.
     */
    private static final double TEMPORARY_MAX_ENERGY = 120;

    private StageFactory() {
    }

    /**
     * Assemble a **battle already under way** from a stage id: the team is in place, wave 1 has
     * entered and been ordered onto the action bar.
     *
     * <p>Callers who want a fixed seed should use {@link #load(int, List, Random)}.
     *
     * @param stageId stage id (see {@code StageBean})
     * @return a battle that can be {@code stepForward()}ed right away
     * @throws IllegalArgumentException if the stage does not exist (including the case where
     *                                  {@code stage.json} has not been generated)
     */
    public static Battle load(int stageId) {
        return load(stageId, temporaryTeam(), new Random());
    }

    /**
     * Assemble a battle from a stage id plus the given team/random source.
     *
     * @param stageId stage id
     * @param team    our team (must not be empty)
     * @param rng     random source; a fixed seed makes the whole battle reproducible
     * @return a battle that can be {@code stepForward()}ed right away
     * @throws IllegalArgumentException if the stage does not exist or the team is empty
     */
    public static Battle load(int stageId, List<Character> team, Random rng) {
        StageBean stage = requireStage(stageId);
        if (team == null || team.isEmpty()) {
            throw new IllegalArgumentException("the team must not be empty (stage " + stageId + ")");
        }
        // the enemy team starts as an empty list: monsters are appended wave by wave by WaveManager
        Battle battle = new Battle(team, new ArrayList<>(), rng == null ? new Random() : rng);
        WaveManager waves = new WaveManager(battle, stage);

        battle.startBattle();
        if (!waves.nextWave()) {
            throw new IllegalArgumentException("stage " + stageId + " has no monsters in any wave");
        }
        battle.processRequests();          // monsters entering is a "queued entry": it MUST be settled once before they go on the action bar
        return battle;
    }

    /**
     * Get the stage data.
     *
     * @param stageId stage id
     * @return the stage data
     * @throws IllegalArgumentException if the stage does not exist, or the data file was not generated
     */
    public static StageBean requireStage(int stageId) {
        StageBean stage = Constant.stages().get(stageId);
        if (stage == null) {
            throw new IllegalArgumentException(Constant.stages().isEmpty()
                    ? "stage data not loaded (stage.json is generator output; see the generator section of the README)"
                    : "unknown stage id: " + stageId);
        }
        return stage;
    }

    /**
     * Whether the stage exists (and its data has been generated).
     */
    public static boolean hasStage(int stageId) {
        return Constant.stages().containsKey(stageId);
    }

    /**
     * **Temporary** team: 3 placeholders, each with a different speed (so the action-bar order is
     * distinguishable).
     *
     * <pre>
     *   temporary character A  speed 100    —— baseline
     *   temporary character B  speed 134    —— acts first
     *   temporary character C  speed  90    —— acts last
     * </pre>
     *
     * <p>⚠ These are not "characters", only placeholder data that can stand in a battle: no light
     * cone, no relics, no real skills and no path ({@code fromAttributes} hands out
     * {@code DefaultSkill}, path {@code OTHER}).
     * P8-5 will replace it with a real 4-character team built by {@code CharacterFactory}, and this
     * method will be deleted then.
     *
     * <p>The stat magnitudes deliberately sit at the "can finish a stage" level (HP 10k / atk+def
     * 100) rather than a real character's stat sheet — the real stat sheet has to wait for P8-1.
     */
    public static List<Character> temporaryTeam() {
        List<Character> team = new ArrayList<>();
        team.add(temporaryCharacter("Temporary Character A", 100));
        team.add(temporaryCharacter("Temporary Character B", 134));
        team.add(temporaryCharacter("Temporary Character C", 90));
        return team;
    }

    private static Character temporaryCharacter(String name, int speed) {
        Character character = Character.fromAttributes(name, 10_000, 100, 100, speed);
        character.setMaxEnergy(TEMPORARY_MAX_ENERGY);   // otherwise it can never cast an ultimate
        character.setCurrentEnergy(0);
        return character;
    }
}
