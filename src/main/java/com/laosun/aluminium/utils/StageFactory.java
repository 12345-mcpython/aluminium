package com.laosun.aluminium.utils;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.beans.StageBean;
import com.laosun.aluminium.beans.WeaponData;
import com.laosun.aluminium.enums.Path;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.RelicSuit;
import com.laosun.aluminium.models.WaveManager;
import com.laosun.aluminium.models.Weapon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * <p>⚠ <b>The team is real since P8-5</b>: {@link #load(int)} builds the 4-character team with
 * {@link #realTeam()}, which goes through {@code CharacterFactory} (real stat sheet, path, element,
 * energy cap, real skill slots), equips each character with a light cone of its own path from
 * {@code weapons.json}, and — since P10-3 — with a real relic suit whose 4-piece and 2-piece set bonuses
 * are applied ({@link #referenceRelics()}). Before P8-5 this method used a placeholder team built from
 * {@code Character.fromAttributes}.
 */
public final class StageFactory {

    /**
     * The level the assembled team is built at.
     *
     * <p>80 is the current cap, so the team is a "fully levelled" reference team: the point of P8-5 is
     * to fight with real sheets, and a mid-level team would make any disagreement with the data harder
     * to spot.
     */
    private static final int TEAM_LEVEL = 80;

    /**
     * The light cone level. Same reasoning as {@link #TEAM_LEVEL}.
     */
    private static final int WEAPON_LEVEL = 80;

    /**
     * The star rating of the reference team's relics: 5, because that is what the 5-star cavern and planar
     * sets come in and what a "reference" build means in this game.
     */
    private static final int RELIC_STAR = 5;

    /**
     * The relic level: 15 is the cap, matching {@link #TEAM_LEVEL} and {@link #WEAPON_LEVEL}.
     */
    private static final int RELIC_LEVEL = 15;

    /**
     * The cavern set the reference team wears: 102, <b>Musketeer of Wild Wheat</b>.
     *
     * <p>Chosen because both its tiers are plain stats in the data — 2 pieces grant +12% ATK, 4 pieces
     * another +6% SPD — so the team exercises a real 4-piece bonus end to end instead of a bonus that is
     * only a named ability the engine cannot run yet (most 4-piece bonuses are exactly that; see
     * {@code RelicSet.Effect#hasAbility()}).
     */
    private static final int CAVERN_SET = 102;

    /**
     * The planar ornament set: 301, <b>Space Sealing Station</b> (+12% ATK at 2 pieces).
     *
     * <p>A character-agnostic choice: the planar sets' second tier is an ability in every case, and of the
     * stat-only first tiers this one is the general-purpose one. See {@link RelicFactory} for why the
     * sphere's main attribute is ATK% here rather than an element boost.
     */
    private static final int PLANAR_SET = 301;

    /**
     * The P8-5 reference team: Jing Yuan / Seele / Clara / Natasha.
     *
     * <p>Chosen to cover four different paths (Erudition / Hunt / Destruction / Abundance) and four
     * different roles, so that a stage battle exercises more than one damage shape.
     *
     * <p>A fixed roster is deliberate **for now**: assembling a team from an arbitrary pool needs
     * team-building rules the project does not have, and the point of this task is "the stage uses
     * real characters", not "the game picks a team".
     */
    private static final int[] TEAM = {
            1204,   // Jing Yuan — Erudition, thunder, 130 energy
            1102,   // Seele — Hunt, quantum, 120
            1107,   // Clara — Destruction, physical, 110
            1105,   // Natasha — Abundance, physical, 90
    };

    private StageFactory() {
    }

    /**
     * Assemble a **battle already under way** from a stage id: the real team is in place, wave 1 has
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
        return load(stageId, realTeam(), new Random());
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
     * The **real** 4-character reference team (P8-5), each with a light cone of its own path and a real
     * relic suit.
     *
     * <p>Every member comes from {@link CharacterFactory#create(int, int)}, so it carries a real stat
     * sheet (level scaling, traces), path, element, aggro, energy cap, real skill slots and — since
     * P8-7/P8-8 — its data-driven trigger table and stack resources.
     *
     * <p><b>On the light cones</b>: the cone is picked by path (the same key
     * {@code Path.fromMt} uses for characters), and the <b>smallest matching id</b> wins. That rule is
     * arbitrary but deterministic, which matters more here than "which cone is best": a
     * non-deterministic pick would make every stage battle irreproducible. ⚠ Only the cone's
     * <b>panel</b> is applied — its passive is not (a weapon passive needs the buff system, P10-3).
     *
     * <p><b>On the relics (P10-3)</b>: every member wears the same {@link #referenceRelics()} build — four
     * pieces of Musketeer of Wild Wheat (set 102) plus two pieces of Space Sealing Station (set 301) — so
     * both a 4-piece and a 2-piece set bonus are live on every sheet. The build is a pure function of the
     * data ({@link RelicFactory}), with no rolled main attributes and no rolled sub-stats, because a
     * non-deterministic build would make every stage battle irreproducible in exactly the way the cone
     * choice above is designed to avoid. ⚠ An <b>ability</b>-type set bonus is still not applied: the
     * engine has no ability interpreter (see the class docs of {@code RelicSuit}).
     *
     * @return a fresh team; each call builds new characters and new relics, so two calls never share state
     */
    public static List<Character> realTeam() {
        List<Character> team = new ArrayList<>();
        for (int cid : TEAM) {
            // The cone is chosen **before** the character is built: the stat-sheet pipeline consumes
            // the weapon during build(), so assigning it afterwards would set the field but never
            // reach the sheet (see CharacterFactory.create's weapon overload).
            //
            // The path comes straight from the data rather than from a throwaway character just to
            // read it back.
            Path path = Path.fromMt(CharacterFactory.data(cid).mt());
            team.add(CharacterFactory.create(cid, TEAM_LEVEL, true, samePathWeapon(path), referenceRelics()));
        }
        return team;
    }

    /**
     * The relic build the reference team wears: a 4-piece cavern set plus a 2-piece planar ornament set,
     * both at {@link #RELIC_STAR}/{@link #RELIC_LEVEL}, from the real set data.
     *
     * <p>Public because "what is the reference team actually wearing" is something a test (or a reader
     * comparing two runs) should be able to ask without rebuilding it by hand; the sets themselves are
     * documented on {@link #CAVERN_SET} and {@link #PLANAR_SET}.
     *
     * @return a fresh suit on every call, so no two characters share mutable relic state
     */
    public static RelicSuit referenceRelics() {
        return RelicFactory.combinedSuit(RELIC_STAR, RELIC_LEVEL, CAVERN_SET, PLANAR_SET);
    }

    /**
     * The light cone used for a path: the **highest rarity**, ties broken by the smallest weapon id.
     *
     * <p>Rarity first because a level-80 team holding a 3-star starter cone would be a strange
     * reference team; the id tie-break then makes the choice deterministic, which matters more here
     * than "which cone is best" — a non-deterministic pick would make every stage battle
     * irreproducible.
     *
     * <p>⚠ Only the cone's <b>panel</b> is applied. Its passive is not: a weapon passive needs the
     * buff system (P10-3).
     *
     * @param path the character's path
     * @return the cone, or {@code null} when no weapon of that path exists (the caller then leaves the
     *         character unequipped rather than failing — a weapon is not required to fight)
     */
    static Weapon samePathWeapon(Path path) {
        if (path == null || path == Path.OTHER) {
            return null;
        }
        int bestId = -1;
        int bestRarity = Integer.MIN_VALUE;
        for (Map.Entry<Integer, WeaponData> entry : Constant.WEAPONS.entrySet()) {
            WeaponData data = entry.getValue();
            if (data == null || data.type() == null) {
                continue;
            }
            // `weapons.json` stores the path in `type`, using the same words as `Path`'s mt values
            // ("all"/"single"/"destruction"/"healing"/...), so Path is the join key rather than a
            // second hand-written table.
            if (Path.fromMt(data.type()) != path) {
                continue;
            }
            if (data.rarity() > bestRarity
                    || (data.rarity() == bestRarity && entry.getKey() < bestId)) {
                bestRarity = data.rarity();
                bestId = entry.getKey();
            }
        }
        return bestId < 0 ? null : Weapon.build(bestId, WEAPON_LEVEL);
    }
}
