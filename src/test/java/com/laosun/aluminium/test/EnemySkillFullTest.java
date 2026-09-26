package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillEffectType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.EnemySkill;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * P9-2: an enemy skill reaches whoever its <b>shape</b> says, not always the main target.
 *
 * <p>Before this, {@code EnemySkill.execute} hit the first target {@link #SEGMENTS} times and nothing
 * else, so a multi-target enemy skill in the data had no way to reach a second character — an AoE would
 * silently under-hit and the only way to notice was to read the numbers. The dispatch now mirrors
 * {@code SkillExecutor}'s character-skill shapes.
 *
 * <p>⚠ {@code hits} in {@code enemy_skills.json} means <b>segments</b>, not targets (8013010
 * "Trampling Stomp" is two segments on one target). {@code ROADMAP}'s P9-1 plan uses the same field name
 * for a target count, so these tests pin the meaning from the shipped side: if the field is ever
 * re-pointed, {@link #singleTargetIsTheDefaultAndHitsOnlyTheMainTarget} and
 * {@link #everySegmentLandsOnEveryTargetReached} change meaning with it.
 */
public class EnemySkillFullTest {

    private static final int ICE_EDGE = 1002011;
    private static final int ENEMY_LEVEL = 90;
    private static final int SEGMENTS = 2;

    /** Enough HP that a skill never kills, so the assertions measure damage rather than overkill. */
    private static final double HERO_HP = 100_000;

    // ==================================================================

    /**
     * The default shape reaches one character — the behaviour every shipped entry relies on.
     *
     * <p>{@code null} effect is the default on purpose: the five entries in {@code enemy_skills.json} were
     * written before shapes existed, and adding the field must not move them.
     */
    @Test
    public void singleTargetIsTheDefaultAndHitsOnlyTheMainTarget() {
        Battle battle = newBattle();
        List<Character> team = battle.characters;
        Enemy attacker = battle.enemyUnits().getFirst();

        new EnemySkill(DamageElement.FIRE, 1.0, 1, DamageType.NORMAL)
                .execute(battle, attacker, List.of(team.getFirst()));

        Assertions.assertTrue(team.getFirst().getCurrentHp() < HERO_HP, "the main target was hit");
        Assertions.assertEquals(HERO_HP, team.get(1).getCurrentHp(), "the second character was not");
        Assertions.assertEquals(HERO_HP, team.get(2).getCurrentHp(), "nor the third");
    }

    /** An AoE skill reaches every living character on our side. */
    @Test
    public void anAoeSkillReachesEveryLivingCharacter() {
        Battle battle = newBattle();
        List<Character> team = battle.characters;
        Enemy attacker = battle.enemyUnits().getFirst();

        new EnemySkill(DamageElement.FIRE, 1.0, 1, DamageType.NORMAL, SkillEffectType.AOE_ATTACK)
                .execute(battle, attacker, List.of(team.getFirst()));

        for (Character member : team) {
            Assertions.assertTrue(member.getCurrentHp() < HERO_HP,
                    member.getName() + " should have been hit by the AoE");
        }
    }

    /** Blast reaches the main target and the characters next to it, but not the far one. */
    @Test
    public void blastReachesTheMainTargetAndItsNeighbours() {
        Battle battle = newBattle();
        List<Character> team = battle.characters;
        Enemy attacker = battle.enemyUnits().getFirst();

        new EnemySkill(DamageElement.FIRE, 1.0, 1, DamageType.NORMAL, SkillEffectType.BLAST)
                .execute(battle, attacker, List.of(team.get(1)));

        Assertions.assertTrue(team.get(0).getCurrentHp() < HERO_HP, "the left neighbour was hit");
        Assertions.assertTrue(team.get(1).getCurrentHp() < HERO_HP, "the main target was hit");
        Assertions.assertTrue(team.get(2).getCurrentHp() < HERO_HP, "the right neighbour was hit");
    }

    /**
     * Blast on the leftmost character has only one neighbour — the range is clipped, not wrapped.
     *
     * <p>Worth pinning separately: a naive index arithmetic would either throw or wrap around and hit a
     * character on the far side.
     */
    @Test
    public void blastOnTheEdgeCharacterDoesNotWrapAround() {
        Battle battle = newBattle();
        List<Character> team = battle.characters;
        Enemy attacker = battle.enemyUnits().getFirst();

        new EnemySkill(DamageElement.FIRE, 1.0, 1, DamageType.NORMAL, SkillEffectType.BLAST)
                .execute(battle, attacker, List.of(team.getFirst()));

        Assertions.assertTrue(team.get(0).getCurrentHp() < HERO_HP, "the main target was hit");
        Assertions.assertTrue(team.get(1).getCurrentHp() < HERO_HP, "its single neighbour was hit");
        Assertions.assertEquals(HERO_HP, team.get(2).getCurrentHp(),
                "the far character must NOT be hit -- a blast does not wrap around");
    }

    /**
     * Segments land on <b>every</b> target the shape reached: a 2-segment AoE deals two instances to each
     * of the three characters, not two instances in total.
     *
     * <p>Measured against the same skill with one segment, so the assertion is a ratio between two runs
     * rather than an absolute damage figure.
     */
    @Test
    public void everySegmentLandsOnEveryTargetReached() {
        Battle oneSegment = newBattle();
        new EnemySkill(DamageElement.FIRE, 1.0, 1, DamageType.NORMAL, SkillEffectType.AOE_ATTACK)
                .execute(oneSegment, oneSegment.enemyUnits().getFirst(),
                        List.of(oneSegment.characters.getFirst()));

        Battle twoSegments = newBattle();
        new EnemySkill(DamageElement.FIRE, 1.0, SEGMENTS, DamageType.NORMAL, SkillEffectType.AOE_ATTACK)
                .execute(twoSegments, twoSegments.enemyUnits().getFirst(),
                        List.of(twoSegments.characters.getFirst()));

        for (int i = 0; i < 3; i++) {
            double once = HERO_HP - oneSegment.characters.get(i).getCurrentHp();
            double twice = HERO_HP - twoSegments.characters.get(i).getCurrentHp();
            Assertions.assertTrue(once > 0, "character " + i + " was hit by the 1-segment AoE");
            Assertions.assertEquals(SEGMENTS * once, twice, once * 1e-6,
                    "character " + i + " should take " + SEGMENTS + " segments' worth of damage");
        }
    }

    /**
     * A character who is already down takes no damage, and the living ones still do.
     *
     * <p>The guard that decides this lives in the segment loop ({@code strike} breaks once the victim is
     * dead), not in the target selection — an earlier version had both, and mutation testing showed the
     * selection-side filter changed no observable outcome, so it was removed rather than left as an
     * untestable second guard.
     */
    @Test
    public void aDeadCharacterTakesNoDamageFromAnAoe() {
        Battle battle = newBattle();
        List<Character> team = battle.characters;
        Enemy attacker = battle.enemyUnits().getFirst();
        team.get(1).takeDamage(HERO_HP * 2);
        Assertions.assertTrue(team.get(1).isDeath(), "the premise: the second character is dead");

        new EnemySkill(DamageElement.FIRE, 1.0, 1, DamageType.NORMAL, SkillEffectType.AOE_ATTACK)
                .execute(battle, attacker, List.of(team.getFirst()));

        Assertions.assertTrue(team.getFirst().getCurrentHp() < HERO_HP, "the living ones were hit");
        Assertions.assertTrue(team.get(2).getCurrentHp() < HERO_HP);
    }

    // ==================================================================

    /** Three characters with a lot of HP against one enemy, with crit switched off for determinism. */
    private static Battle newBattle() {
        List<Character> team = List.of(
                Character.fromAttributes("left", HERO_HP, 100, 100, 100),
                Character.fromAttributes("middle", HERO_HP, 100, 100, 100),
                Character.fromAttributes("right", HERO_HP, 100, 100, 100));
        Enemy enemy = EnemyFactory.create(ICE_EDGE, ENEMY_LEVEL, 1);
        Battle battle = new Battle(team, List.of(enemy), new Random(0));
        battle.startBattle();
        enemy.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        return battle;
    }
}
