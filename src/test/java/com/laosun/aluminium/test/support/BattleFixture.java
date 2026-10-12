package com.laosun.aluminium.test.support;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;

import java.util.List;
import java.util.Random;

/**
 * The facts a battle-building judge shares with every other one: which enemy is "the" test monster, which RNG the suite
 * rolls with, and how a battle is stood up.
 *
 * <p><b>Why this is a shared helper, measured.</b> The suite had 61 files declaring their own battle builder, and the
 * same two facts inside nearly all of them: {@code EnemyFactory.create(MONSTER, 90, 1)} where {@code MONSTER} was
 * {@code 1002011} in about 230 files, and {@code new Random(0)} (410 sites in {@code test/engine} alone). A fact copied
 * per file is a fact that drifts per file - this class is where it now lives once.
 *
 * <p><b>What this class does NOT do</b> - and the reason it is small.
 * <ul>
 *   <li>It does not decide <b>which</b> enemy a judge fights. The minority ids are real: {@code 1003010} and
 *       {@code 1002064} appear in engine judges that are about those enemies, and a blind sweep to {@link #monster()}
 *       would have silently changed four judges' premise. {@link #enemy(int, int)} is there for them.</li>
 *   <li>It does not build trigger tables, prime resources or drive turns. {@code setTriggerTable(...)},
 *       {@code Character.fromAttributes(...)} and "how this clause's premise comes about" stay in the judge that
 *       states them - {@link TestTurns} covers the one shared orchestration (a unit's own turn).</li>
 *   <li>It does not start the battle. {@code startBattle()}/{@code processRequests()} are visible on purpose: some
 *       judges measure before the battle's start rules have run.</li>
 * </ul>
 */
public final class BattleFixture {

    /**
     * The enemy the overwhelming majority of judges fight - {@code EnemyFactory.create(1002011, 90, 1)}.
     *
     * <p>Measured when this class was written: {@code MONSTER = 1002011} in about 230 test files.
     */
    public static final int MONSTER_ID = 1002011;

    /**
     * The level {@link #monster()} is created at, as the same files spelled it.
     */
    public static final int MONSTER_LEVEL = 90;

    private BattleFixture() {
    }

    /**
     * The standard test enemy: id {@value #MONSTER_ID} at level {@value #MONSTER_LEVEL}, one of it.
     */
    public static Enemy monster() {
        return EnemyFactory.create(MONSTER_ID, MONSTER_LEVEL, 1);
    }

    /**
     * The same factory call for a judge whose enemy is <b>not</b> the standard one.
     *
     * @param id    the enemy's data id, as the judge's own constant spells it
     * @param level the level
     */
    public static Enemy enemy(int id, int level) {
        return EnemyFactory.create(id, level, 1);
    }

    /**
     * A target that survives any single hit: {@code Enemy.fromAttributes("dummy", 1_000_000, 0, 100, 100)}.
     *
     * <p>⚠ Only this shape is shared. The other dummy in the suite - {@code fromAttributes("dummy", 100_000, 100, 100, 100)} -
     * has a different attack and stays where it is: its numbers are part of that judge's premise, not boilerplate.
     */
    public static Enemy dummy() {
        return Enemy.fromAttributes("dummy", 1_000_000, 0, 100, 100);
    }

    /**
     * The suite's standard RNG: {@code new Random(0)} - deterministic, and the same sequence for every judge that asks
     * for one. The argument is the seed, so a judge that needs a different sequence can say so here.
     */
    public static Random seeded() {
        return new Random(0);
    }

    /**
     * An RNG whose {@code nextDouble} always returns {@code value} - the shape judges use for "a fixed crit roll".
     *
     * <p>⚠ Only {@code nextDouble} is pinned: the inherited {@code nextInt}/{@code nextBoolean} still vary, which is
     * what the hand-written fixtures this replaces did too.
     */
    public static Random alwaysRoll(double value) {
        return new Random() {
            @Override
            public double nextDouble() {
                return value;
            }
        };
    }

    /**
     * A battle with the suite's standard RNG, not started: the judge decides when that happens.
     */
    public static Battle battle(List<Character> party, List<? extends CanHit> enemies) {
        return new Battle(party, enemies, seeded());
    }

    /**
     * A battle with the judge's own RNG.
     */
    public static Battle battle(List<Character> party, List<? extends CanHit> enemies, Random rng) {
        return new Battle(party, enemies, rng);
    }
}
