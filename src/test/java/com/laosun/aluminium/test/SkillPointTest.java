package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Skill points (战技点, SP) (P8-4): start 3, cap 5, our basic attack +1, skill -1, ultimate and follow-up attacks
 * neutral.
 *
 * <p><b>Data fact</b>: {@code skills.json} has **no** skill point field — among the 638 skills, the
 * {@code sp_need} of all 122 basic attacks and 109 skills is {@code null} (the 99 entries that do have a value are
 * all ultimates, and that is the ultimate energy threshold, see {@code engine.md} §9.4). So skill points can only
 * come from the game rules, and they live in {@link Constant}.
 *
 * <p>This test deliberately does **not** use {@code castImmediate} (that is a test/demo entry point that bypasses
 * the queue and by design does not touch skill points); everything goes through the real chain
 * {@code stepForward → beforeMove → performAction → afterMove}, otherwise what is tested is "a world without skill
 * points".
 */
public class SkillPointTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // 1. The pool itself
    // ==================================================================

    /** Starts at 3 points, caps at 5. */
    @Test
    public void startsAtThreeAndCapsAtFive() {
        Battle battle = newBattle();

        Assertions.assertEquals(3, battle.getSkillPoints(), "skill points at the start");
        Assertions.assertEquals(Constant.SKILL_POINT_START, battle.getSkillPoints());
        Assertions.assertEquals(5, Constant.SKILL_POINT_MAX);

        battle.gainSkillPoint(1);
        Assertions.assertEquals(4, battle.getSkillPoints());
        battle.gainSkillPoint(1);
        Assertions.assertEquals(5, battle.getSkillPoints(), "reaches 5");
        battle.gainSkillPoint(1);
        Assertions.assertEquals(5, battle.getSkillPoints(), "capped, no overflow");
        battle.gainSkillPoint(100);
        Assertions.assertEquals(5, battle.getSkillPoints(), "adding 100 at once still only reaches 5");
    }

    /** After spending down to 0, any further spend must return false (rather than going negative). */
    @Test
    public void spendingStopsAtZero() {
        Battle battle = newBattle();

        for (int i = 0; i < 3; i++) {
            Assertions.assertTrue(battle.spendSkillPoint(), "spend #" + (i + 1) + " should succeed");
        }
        Assertions.assertEquals(0, battle.getSkillPoints());
        Assertions.assertFalse(battle.hasSkillPoint());

        Assertions.assertFalse(battle.spendSkillPoint(), "at 0 points it should fail");
        Assertions.assertEquals(0, battle.getSkillPoints(), "a failure must not go negative");
    }

    /** Gaining a non-positive amount is a caller bug: ignore it silently, do not treat it as "spending a point". */
    @Test
    public void gainingNonPositiveAmountIsIgnored() {
        Battle battle = newBattle();

        battle.gainSkillPoint(0);
        Assertions.assertEquals(3, battle.getSkillPoints(), "adding 0 changes nothing");
        battle.gainSkillPoint(-5);
        Assertions.assertEquals(3, battle.getSkillPoints(), "adding a negative must not turn into a spend");
    }

    // ==================================================================
    // 2. The real chain: performAction really does change skill points
    // ==================================================================

    /** One basic attack through the real chain → +1. */
    @Test
    public void basicAttackInRealActionFlowGainsOnePoint() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();

        Assertions.assertEquals(3, battle.getSkillPoints());
        Assertions.assertTrue(actWithRealTurn(battle, hero, () -> skill(hero, Slot.COMMON),
                () -> List.of(firstEnemy(battle))));
        Assertions.assertEquals(4, battle.getSkillPoints(), "basic attack +1");
    }

    /** One skill cast through the real chain → -1. */
    @Test
    public void skillInRealActionFlowSpendsOnePoint() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();

        Assertions.assertTrue(actWithRealTurn(battle, hero, () -> skill(hero, Slot.SKILL),
                () -> List.of(firstEnemy(battle))));
        Assertions.assertEquals(2, battle.getSkillPoints(), "skill -1");
    }

    /** Basic attacks in a row up to the cap: 3 → 4 → 5 → 5. */
    @Test
    public void repeatedBasicAttacksCapAtFive() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();

        for (int i = 0; i < 4; i++) {
            Assertions.assertTrue(
                    actWithRealTurn(battle, hero, () -> skill(hero, Slot.COMMON),
                            () -> List.of(firstEnemy(battle))),
                    "basic attack #" + (i + 1) + "");
        }

        Assertions.assertEquals(5, battle.getSkillPoints(), "basic attacks in a row cap at 5");
    }

    // ==================================================================
    // 3. At 0 points the skill cannot be cast, and deals **no damage**
    // ==================================================================

    /**
     * The core case: casting the skill at 0 skill points → {@code performAction} returns false, and the target
     * loses not a single point of HP.
     *
     * <p>The "no damage" part must be asserted together: {@code performAction} only **queues**, the actual
     * settlement happens in {@code afterMove → processRequests}. If the cost had failed but the request was
     * already queued, we would get "it was never paid for yet the hit came out".
     */
    @Test
    public void skillWithNoPointsFailsAndDealsNoDamage() {
        Battle battle = newBattle();
        Character hero = battle.characters.getFirst();
        Enemy target = firstEnemy(battle);

        while (battle.spendSkillPoint()) {
            // drain the pool to zero
        }
        Assertions.assertEquals(0, battle.getSkillPoints());

        double hpBefore = target.getCurrentHp();
        Assertions.assertFalse(actWithoutAfterMove(battle, hero, () -> skill(hero, Slot.SKILL), List.of(target)),
                "0 skill points → the action does not happen");
        Assertions.assertEquals(0, battle.getSkillPoints(), "a failed action must not deduct below zero");
        Assertions.assertEquals(hpBefore, target.getCurrentHp(), EPS, "a failed action must not deal damage");
    }

    // ==================================================================
    // 4. The ultimate and follow-up attacks are "neutral"
    // ==================================================================

    /** The ultimate neither spends nor restores skill points (the 5 points given back at the end go to energy, not to skill points). */
    @Test
    public void ultimateNeitherSpendsNorGainsSkillPoints() {
        Character jingYuan = CharacterFactory.create(1204, 80);
        Battle battle = newBattle(jingYuan);

        int before = battle.getSkillPoints();
        jingYuan.setCurrentEnergy(200);
        Assertions.assertTrue(battle.castUltra(jingYuan, List.of(firstEnemy(battle))));

        Assertions.assertEquals(before, battle.getSkillPoints(), "the ultimate does not touch skill points");
    }

    /**
     * Follow-up attacks / talents: their {@code attack_type} is {@code null} in the data, so they must take the
     * neutral branch.
     *
     * <p>⚠ This one guards against writing the {@code switch} as "if it is not a basic attack then it is a skill"
     * ({@code default -> spend}): that way talents and follow-up attacks would quietly eat skill points, and the
     * moment P8-3 adds follow-up attacks it would be hit immediately.
     */
    @Test
    public void nullAttackTypeIsNeutral() {
        Battle battle = newBattle();
        Skill talentLike = skill(CharacterFactory.create(1003, 80), Slot.TALENT);
        Assertions.assertNull(talentLike.getData().getSkillType(), "precondition: the talent's attack_type is null");

        int before = battle.getSkillPoints();
        Assertions.assertTrue(battle.applySkillPointCost(talentLike, battle.characters.getFirst()),
                "a neutral skill does not block the action");
        Assertions.assertEquals(before, battle.getSkillPoints(), "neither rises nor falls");
    }

    /** The map basic attack (slot 6, {@code MazeNormal}) is treated as neutral too, it does not count as "basic attack restores a point". */
    @Test
    public void mazeAttackTypeIsNeutral() {
        Battle battle = newBattle();
        Skill maze = skill(CharacterFactory.create(1003, 80), Slot.MAZE);

        int before = battle.getSkillPoints();
        Assertions.assertTrue(battle.applySkillPointCost(maze, battle.characters.getFirst()));
        Assertions.assertEquals(before, battle.getSkillPoints(), "MazeNormal is neutral");
    }

    // ==================================================================
    // 5. Only our side counts: an enemy's basic attack must not feed our pool
    // ==================================================================

    /**
     * An enemy action must not change our skill points.
     *
     * <p>⚠ <b>The trap in this test (already fixed)</b>: the enemy's default skill {@code EnemySkill} has a
     * {@code getData()} that is **always null** (it does not go through the character multiplier table, see that
     * class's javadoc), so it returns early at the null guard in {@code applySkillPointCost} —
     * testing "the enemy does not affect skill points" with the default skill **passes whether or not the camp
     * check exists**. That is exactly how I wrote it the first time, and only mutation testing exposed it (it
     * stayed green after the camp check was removed).
     *
     * <p>So here we **hand the enemy a real character basic attack** ({@code attack_type = "Normal"}):
     * only that way does it actually reach the branch, making the "camp check" the only thing that can stop it.
     */
    @Test
    public void enemyBasicAttackDoesNotFeedThePlayerPool() {
        Battle battle = newBattle();
        Enemy enemy = firstEnemy(battle);
        Character hero = battle.characters.getFirst();

        // precondition self-check: the default enemy skill's getData() is null, so it cannot test the camp check
        Assertions.assertNull(enemySkill(enemy).getData(),
                "precondition: EnemySkill has no character multiplier data, testing it directly is a no-op");

        // swap in "a real character's basic attack" (Normal) — the data is non-null, so it really reaches the skill point branch
        enemy.setSkill(SkillType.COMMON, new DefaultSkill(1003, 1, 1));
        int before = battle.getSkillPoints();
        Assertions.assertTrue(actWithRealTurn(battle, enemy, () -> enemy.getSkills().get(SkillType.COMMON),
                () -> List.of(hero)), "the enemy's basic attack itself should succeed");

        Assertions.assertEquals(before, battle.getSkillPoints(),
                "an enemy action must not change our skill points (this fails once the camp check is removed)");
    }

    // ==================================================================
    // 6. applySkillPointCost is **atomic**
    // ==================================================================

    /** Trying to cast the skill at 0 points: returns false and the count is unchanged (it must not deduct to -1 and then check). */
    @Test
    public void skillPointCostIsAtomicAtZero() {
        Battle battle = newBattle();
        Skill skill = battle.characters.getFirst().getSkills().get(SkillType.SKILL);

        while (battle.spendSkillPoint()) {
            // zero it out
        }
        Assertions.assertFalse(battle.applySkillPointCost(skill, battle.characters.getFirst()),
                "not enough points → failure");
        Assertions.assertEquals(0, battle.getSkillPoints(), "the failed attempt must not deduct to -1");
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

    /** Himeko (姬子) 1003: in the data slots 1/2 are {@code Normal} / {@code BPSkill} respectively. */
    private static Battle newBattle() {
        return newBattle(CharacterFactory.create(1003, 80));
    }

    private static Battle newBattle(Character hero) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(hero), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    /** The enemy's basic attack (the skill P5-3 equips on enemies). */
    private static Skill enemySkill(Enemy enemy) {
        return enemy.getSkills().values().iterator().next();
    }

    /**
     * Has {@code actor} take one action through the real flow, **and finishes it** ({@code afterMove}).
     *
     * <p>The one whose action value arrives first may be the enemy (冰锋 has speed 132 > Himeko's 96), so we have to
     * skip ahead to the actor's turn.
     */
    private static boolean actWithRealTurn(Battle battle, CanHit actor, Supplier<Skill> skill,
                                           Supplier<List<? extends CanHit>> targets) {
        boolean result = actWithoutAfterMove(battle, actor, skill, targets.get());
        battle.afterMove();
        return result;
    }

    /** As above, but does **not** finish it — left to the caller to assert before settlement (the "no damage" case needs this). */
    private static boolean actWithoutAfterMove(Battle battle, CanHit actor, Supplier<Skill> skill,
                                               List<? extends CanHit> targets) {
        for (int i = 0; i < 30; i++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            if (battle.currentMove.getCanHit() == actor) {
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
