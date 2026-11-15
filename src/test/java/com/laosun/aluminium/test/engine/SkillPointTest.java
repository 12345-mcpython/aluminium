package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

/**
 * Skill points (战技点, SP): start 3, cap 5, our basic attack +1, skill -1, ultimate and follow-up attacks
 * neutral.
 *
 * <p><b>Data fact</b>: {@code skills.json} has no skill point field - among the 638 skills, the
 * {@code sp_need} of all 122 basic attacks and 109 skills is {@code null} (the 99 entries that do have a value are
 * all ultimates, and that is the ultimate energy threshold, see {@code SkillPointGameParityTest}). So skill points can only
 * come from the game rules, and they live in {@link Constant}.
 *
 * <p>This test deliberately does not use {@code castImmediate} (that is a test/demo entry point that bypasses
 * the queue and by design does not touch skill points); everything goes through the real chain
 * {@code stepForward to beforeMove to performAction to afterMove}, otherwise what is tested is "a world without skill
 * points".
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the scenarios below, one {@code assertAll} per
 * fixture/observable, so a failing clause still names itself. The 0-point refusal stays its own test: a
 * rejection path is not a positive clause.
 */
public class SkillPointTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // 1. The pool itself
    // ==================================================================

    /**
     * {@code gainSkillPoint}: starts at 3, one gain is +1, the pool caps at 5, and a non-positive amount is
     * ignored rather than turned into a spend.
     *
     * <p>All clauses are on the same fixture (a bare battle) and the same observable (the pool count), so they
     * share one scenario. The non-positive probes run first, while the pool is still at its start value.
     */
    @Test
    public void gainSkillPointRules() {
        Battle battle = newBattle();

        Assertions.assertAll("gainSkillPoint: start, cap and non-positive amounts",
                () -> Assertions.assertEquals(3, battle.getSkillPoints(), "skill points at the start"),
                () -> Assertions.assertEquals(Constant.SKILL_POINT_START, battle.getSkillPoints(),
                        "the start value is the rule's constant"),
                () -> Assertions.assertEquals(5, Constant.SKILL_POINT_MAX, "the cap is the rule's constant"),
                () -> {
                    battle.gainSkillPoint(0);
                    Assertions.assertEquals(3, battle.getSkillPoints(), "adding 0 changes nothing");
                },
                () -> {
                    battle.gainSkillPoint(-5);
                    Assertions.assertEquals(3, battle.getSkillPoints(),
                            "adding a negative must not turn into a spend");
                },
                () -> {
                    battle.gainSkillPoint(1);
                    Assertions.assertEquals(4, battle.getSkillPoints(), "one gain is +1");
                },
                () -> {
                    battle.gainSkillPoint(1);
                    Assertions.assertEquals(5, battle.getSkillPoints(), "reaches 5");
                },
                () -> {
                    battle.gainSkillPoint(1);
                    Assertions.assertEquals(5, battle.getSkillPoints(), "capped, no overflow");
                },
                () -> {
                    battle.gainSkillPoint(100);
                    Assertions.assertEquals(5, battle.getSkillPoints(),
                            "adding 100 at once still only reaches 5");
                });
    }

    /**
     * Spending at 0 points is refused by both entry points ({@code spendSkillPoint} and
     * {@code applySkillPointCost}), and a failed deduction must not take the pool negative - it is atomic.
     */
    @Test
    public void spendingStopsAtZeroAndTheCostIsAtomic() {
        Battle battle = newBattle();
        Character hero = battle.characters().getFirst();
        Skill skill = hero.getSkills().get(SkillType.SKILL);

        Assertions.assertAll("spending never goes below zero",
                () -> {
                    for (int i = 0; i < 3; i++) {
                        Assertions.assertTrue(battle.spendSkillPoint(),
                                "spend #" + (i + 1) + " should succeed");
                    }
                    Assertions.assertEquals(0, battle.getSkillPoints(), "the pool is empty after three spends");
                },
                () -> Assertions.assertFalse(battle.hasSkillPoint(),
                        "with 0 points hasSkillPoint is false"),
                () -> Assertions.assertFalse(battle.spendSkillPoint(), "at 0 points it should fail"),
                () -> Assertions.assertEquals(0, battle.getSkillPoints(), "a failure must not go negative"),
                () -> Assertions.assertFalse(battle.applySkillPointCost(skill, hero),
                        "not enough points → failure"),
                () -> Assertions.assertEquals(0, battle.getSkillPoints(),
                        "the failed attempt must not deduct to -1"));
    }

    // ==================================================================
    // 2. The real chain: performAction really does change skill points
    // ==================================================================

    /**
     * The real turn chain ({@code stepForward to beforeMove to performAction to afterMove}) is what moves the
     * pool: a basic attack gains +1 (and repeated basic attacks cap at 5), a skill spends -1.
     *
     * <p>The three clauses are on the same fixture and the same observable; each sub-scenario starts from its
     * own battle so no clause borrows another clause's pool state (the skill clause needs the start value of 3
     * to assert the 3 to 2 drop).
     */
    @Test
    public void realActionChainAppliesTheSkillPointRules() {
        Assertions.assertAll("the real action chain changes the pool",
                () -> {
                    Battle battle = newBattle();
                    Character hero = battle.characters().getFirst();

                    Assertions.assertEquals(3, battle.getSkillPoints(), "starting pool before the basic attack");
                    Assertions.assertTrue(actWithRealTurn(battle, hero, () -> skill(hero, Slot.COMMON),
                            () -> List.of(firstEnemy(battle))), "the basic attack itself should succeed");
                    Assertions.assertEquals(4, battle.getSkillPoints(), "basic attack +1");
                },
                () -> {
                    Battle battle = newBattle();
                    Character hero = battle.characters().getFirst();

                    for (int i = 0; i < 4; i++) {
                        Assertions.assertTrue(
                                actWithRealTurn(battle, hero, () -> skill(hero, Slot.COMMON),
                                        () -> List.of(firstEnemy(battle))),
                                "basic attack #" + (i + 1) + "");
                    }
                    Assertions.assertEquals(5, battle.getSkillPoints(), "basic attacks in a row cap at 5");
                },
                () -> {
                    Battle battle = newBattle();
                    Character hero = battle.characters().getFirst();

                    Assertions.assertTrue(actWithRealTurn(battle, hero, () -> skill(hero, Slot.SKILL),
                            () -> List.of(firstEnemy(battle))), "the skill itself should succeed");
                    Assertions.assertEquals(2, battle.getSkillPoints(), "skill -1");
                });
    }

    // ==================================================================
    // 3. At 0 points the skill cannot be cast, and deals no damage
    // ==================================================================

    /**
     * The core case: casting the skill at 0 skill points to {@code performAction} returns false, and the target
     * loses not a single point of HP.
     *
     * <p>The "no damage" part must be asserted together: {@code performAction} only queues, the actual
     * settlement happens in {@code afterMove to processRequests}. If the cost had failed but the request was
     * already queued, we would get "it was never paid for yet the hit came out".
     *
     * <p>This is a refusal path, so it stays out of the positive scenarios above.
     */
    @Test
    public void skillWithNoPointsFailsAndDealsNoDamage() {
        Battle battle = newBattle();
        Character hero = battle.characters().getFirst();
        Enemy target = firstEnemy(battle);

        while (battle.spendSkillPoint()) {
            // drain the pool to zero
        }
        double hpBefore = target.getCurrentHp();
        boolean acted = actWithoutAfterMove(battle, hero, () -> skill(hero, Slot.SKILL), List.of(target));

        Assertions.assertAll("at 0 points the skill is refused and deals no damage",
                () -> Assertions.assertEquals(0, battle.getSkillPoints(), "the pool is empty before the attempt"),
                () -> Assertions.assertFalse(acted, "0 skill points → the action does not happen"),
                () -> Assertions.assertEquals(0, battle.getSkillPoints(),
                        "a failed action must not deduct below zero"),
                () -> Assertions.assertEquals(hpBefore, target.getCurrentHp(), EPS,
                        "a failed action must not deal damage"));
    }

    // ==================================================================
    // 4. The ultimate and follow-up attacks are "neutral"
    // ==================================================================

    /**
     * The ultimate neither spends nor restores skill points (the 5 points given back at the end go to energy, not to skill points).
     */
    @Test
    public void ultimateNeitherSpendsNorGainsSkillPoints() {
        Character jingYuan = CharacterFactory.create(1204, 80);
        Battle battle = newBattle(jingYuan);

        int before = battle.getSkillPoints();
        jingYuan.setCurrentEnergy(200);

        Assertions.assertAll("the ultimate is skill-point neutral",
                () -> Assertions.assertTrue(battle.castUltra(jingYuan, List.of(firstEnemy(battle))),
                        "precondition: the ultimate is cast"),
                () -> Assertions.assertEquals(before, battle.getSkillPoints(),
                        "the ultimate does not touch skill points"));
    }

    /**
     * Follow-up attacks / talents: their {@code attack_type} is {@code null} in the data, so they must take the
     * neutral branch; the map basic attack (slot 6, {@code MazeNormal}) is neutral too and does not count as
     * "basic attack restores a point".
     *
     * <p>Note: This one guards against writing the {@code switch} as "if it is not a basic attack then it is a skill"
     * ({@code default -> spend}): that way talents and follow-up attacks would quietly eat skill points, and the
     * moment follow-up attacks are added it would be hit immediately.
     */
    @Test
    public void neutralAttackTypesDoNotChangeThePool() {
        Battle battle = newBattle();
        Character hero = battle.characters().getFirst();
        Skill talentLike = skill(CharacterFactory.create(1003, 80), Slot.TALENT);
        Skill maze = skill(CharacterFactory.create(1003, 80), Slot.MAZE);

        int before = battle.getSkillPoints();

        Assertions.assertAll("neutral attack types do not change the pool",
                () -> Assertions.assertNull(talentLike.getData().getSkillType(),
                        "precondition: the talent's attack_type is null"),
                () -> Assertions.assertTrue(battle.applySkillPointCost(talentLike, hero),
                        "a neutral skill does not block the action"),
                () -> Assertions.assertEquals(before, battle.getSkillPoints(), "neither rises nor falls"),
                () -> Assertions.assertTrue(battle.applySkillPointCost(maze, hero),
                        "MazeNormal does not block the action"),
                () -> Assertions.assertEquals(before, battle.getSkillPoints(), "MazeNormal is neutral"));
    }

    // ==================================================================
    // 5. Only our side counts: an enemy's basic attack must not feed our pool
    // ==================================================================

    /**
     * An enemy action must not change our skill points.
     *
     * <p>Note: <b>The trap in this test (already fixed)</b>: the enemy's default skill {@code EnemySkill} has a
     * {@code getData()} that is always null (it does not go through the character multiplier table, see that
     * class's javadoc), so it returns early at the null guard in {@code applySkillPointCost} -
     * testing "the enemy does not affect skill points" with the default skill passes whether or not the camp
     * check exists. That is exactly how I wrote it the first time, and only mutation testing exposed it (it
     * stayed green after the camp check was removed).
     *
     * <p>So here we hand the enemy a real character basic attack ({@code attack_type = "Normal"}):
     * only that way does it actually reach the branch, making the "camp check" the only thing that can stop it.
     */
    @Test
    public void enemyBasicAttackDoesNotFeedThePlayerPool() {
        Battle battle = newBattle();
        Enemy enemy = firstEnemy(battle);
        Character hero = battle.characters().getFirst();

        // precondition self-check: the default enemy skill's getData() is null, so it cannot test the camp check
        Skill defaultEnemySkill = enemySkill(enemy);

        // swap in "a real character's basic attack" (Normal) - the data is non-null, so it really reaches the skill point branch
        enemy.setSkill(SkillType.COMMON, new DefaultSkill(1003, 1, 1));
        int before = battle.getSkillPoints();
        boolean acted = actWithRealTurn(battle, enemy, () -> enemy.getSkills().get(SkillType.COMMON),
                () -> List.of(hero));

        Assertions.assertAll("an enemy action must not feed our pool",
                () -> Assertions.assertNull(defaultEnemySkill.getData(),
                        "precondition: EnemySkill has no character multiplier data, testing it directly is a no-op"),
                () -> Assertions.assertTrue(acted, "the enemy's basic attack itself should succeed"),
                () -> Assertions.assertEquals(before, battle.getSkillPoints(),
                        "an enemy action must not change our skill points (this fails once the camp check is removed)"));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private enum Slot {
        COMMON(1), SKILL(2), TALENT(4), MAZE(6);

        private final int slot;

        Slot(int slot) {
            this.slot = slot;
        }
    }

    private static Skill skill(Character hero, Slot slot) {
        return new DefaultSkill(hero.getCid(), slot.slot, 1);
    }

    /**
     * Himeko (姬子) 1003: in the data slots 1/2 are {@code Normal} / {@code BPSkill} respectively.
     */
    private static Battle newBattle() {
        return newBattle(CharacterFactory.create(1003, 80));
    }

    private static Battle newBattle(Character hero) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = BattleFixture.battle(List.of(hero), List.of(enemy));
        battle.startBattle();
        return battle;
    }

    /**
     * The enemy's basic attack (the skill enemies are equipped with).
     */
    private static Skill enemySkill(Enemy enemy) {
        return enemy.getSkills().values().iterator().next();
    }

    /**
     * Has {@code actor} take one action through the real flow, and finishes it ({@code afterMove}).
     *
     * <p>The one whose action value arrives first may be the enemy (Ice Edge (冰锋) has speed 132 > Himeko's 96), so we have to
     * skip ahead to the actor's turn.
     */
    private static boolean actWithRealTurn(Battle battle, CanHit actor, Supplier<Skill> skill,
                                           Supplier<List<? extends CanHit>> targets) {
        boolean result = actWithoutAfterMove(battle, actor, skill, targets.get());
        battle.afterMove();
        return result;
    }

    /**
     * As above, but does not finish it - left to the caller to assert before settlement (the "no damage" case needs this).
     */
    private static boolean actWithoutAfterMove(Battle battle, CanHit actor, Supplier<Skill> skill,
                                               List<? extends CanHit> targets) {
        for (int i = 0; i < 30; i++) {
            battle.stepForward();
            if (battle.currentMove() == null) {
                break;
            }
            if (battle.currentMove().getCanHit() == actor) {
                battle.beforeMove();
                return battle.performAction(skill.get(), targets);
            }
            battle.afterMove();
        }
        Assertions.fail("it was not " + actor.getName() + "'s turn within 30 steps");
        return false;
    }

    private static Enemy firstEnemy(Battle battle) {
        return battle.enemyUnits().getFirst();
    }
}
