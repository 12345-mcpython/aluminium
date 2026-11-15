package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.buff.StunBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.energy.NoConventionalEnergyProvider;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

/**
 * Consistency cross-check between skill points and the game rules.
 *
 * <p>This class is not a functional test; it is a probe that "asks the engine one rule at a
 * time": each scenario notes the rule's source and the conclusion (consistent / inconsistent / not
 * implemented). Wherever the engine's behaviour differs from the rule, the assertion states the
 * engine's current behaviour and the comment marks the difference, so that next time nobody
 * mistakes it for already aligned.
 *
 * <p><b>Rule sources (verified)</b>:
 * <ul>
 *   <li>Party-wide - "skill points are a party-wide shared resource" (9game skill point mechanics
 *       explained);
 *   <li>Cap 5 / basic attack +1 - "each time a character uses a basic attack they restore one
 *       skill point; the skill point cap is 5";
 *   <li><b>Start at 3</b> - the player Q&amp;A states explicitly "normally every battle starts with
 *       three skill points";
 *   <li>The start value is variable - this project's own {@code RELICS.md}: the 4-piece
 *       Passerby set "immediately restores 1 skill point for our side at the start of battle", so
 *       a team wearing it starts at 4 (5 if two characters wear it);
 *   <li>The cap is variable - Sparkle's talent "additionally increases the skill point cap by 2",
 *       and in {@code WEAPONS.md} the Elation light cone "for each character on the Elation path,
 *       the skill point cap increases by 1, up to 3"; there is even a light cone whose trigger
 *       condition is "the skill point cap is greater than or equal to 6" to the cap is not a
 *       constant 5.
 * </ul>
 *
 * <p>Note: Note: this project's spec document {@code HSR.md} ({@code E:\code\blog\hsr\HSR.md})
 * has no skill point section at all - only one sentence in §6.1, "punchline: the counter above
 * the skill points", mentions it. So the rule set above is reverse-engineered from the game
 * mechanics and the character documents, not given by the spec.
 *
 * <p><b>L3:</b> the twelve single-clause entries are grouped into the seven scenarios below, one
 * {@link Assertions#assertAll} per fixture/clause/observable, so a failing clause still names
 * itself in its message. The two refusal clauses (0 points, controlled) stay out of every positive
 * scenario and share one refusal scenario; no rejection is folded into a positive clause.
 */
public class SkillPointGameParityTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // The pool: start, cap and gain; party-wide; fresh per battle
    // ==================================================================

    /**
     * The pool's start, cap and basic-attack gain are still plain constants, nothing external can move them,
     * and every battle starts over.
     *
     * <p>Rule: every battle starts with 3 points ({@code SKILL_POINT_START}); the cap is 5
     * ({@code SKILL_POINT_MAX}); a basic attack restores 1 ({@code SKILL_POINT_GAIN_BASIC}).
     *
     * <p>Note: <b>Not implemented</b>: the "starting skill point" of relics / light cones.
     * {@code RELICS.md}: 4-piece Passerby set "at the start of battle, immediately restore 1 skill point for
     * our side", so a team wearing it starts at 4 points (5 if two characters wear it). The engine's start value
     * is always {@code SKILL_POINT_START}, and relic set effects are not wired up at all
     * ({@code relic_sets.json} is not even loaded).
     *
     * <p>Likewise the two skill-point facts that are still constants: the party-level cap (Sparkle's talent +2
     * and the Elation light cone's +1~3 have no attachment point) and the basic attack's own gain (Bronya's
     * "50% chance +1" and Sushang's "hit a broken target +1" have no attachment point).
     *
     * <p><b>L3:</b> the four single-clause cases (start 3, fresh per battle, the constants, the relic start)
     * share one fixture - a one-character battle - and one observable, {@code battle.getSkillPoints()}, so they
     * are one scenario. The start clauses run first, before the {@code gainSkillPoint(100)} clause fills the
     * pool up to the cap.
     */
    @Test
    public void startCapAndBasicGainAreStillConstants() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));

        Assertions.assertAll("the pool's start, cap and basic gain are still constants",
                () -> Assertions.assertEquals(3, battle.getSkillPoints(), "a battle starts at 3"),
                () -> Assertions.assertEquals(3, Constant.SKILL_POINT_START,
                        "the start is the rule's constant 3"),
                () -> Assertions.assertEquals(Constant.SKILL_POINT_START, battle.getSkillPoints(),
                        "the start is always 3; the 4-piece Passerby set's +1 has no attachment point"),
                () -> {
                    Battle spent = newBattle(List.of(CharacterFactory.create(1003, 80)));
                    spent.spendSkillPoint();
                    spent.spendSkillPoint();
                    Assertions.assertEquals(1, spent.getSkillPoints(), "two spends take the pool to 1");

                    Battle second = newBattle(List.of(CharacterFactory.create(1003, 80)));
                    Assertions.assertEquals(3, second.getSkillPoints(),
                            "a new battle does not inherit the previous one's leftovers");
                },
                () -> {
                    battle.gainSkillPoint(100);
                    Assertions.assertEquals(Constant.SKILL_POINT_MAX, battle.getSkillPoints(),
                            "the cap is always the constant 5; characters / light cones cannot change it");
                },
                () -> Assertions.assertEquals(1, Constant.SKILL_POINT_GAIN_BASIC,
                        "the basic attack gain is always the constant 1; "
                                + "character-level extra point supply has no attachment point"));
    }

    /**
     * Rule: skill points are party-wide shared. Engine: the pool lives on {@code Battle}, not one per character.
     *
     * <p><b>L3:</b> all four clauses are one fixture (a two-character battle) and one observable (the single
     * pool count), so they are one scenario: A spends, the pool B sees falls, B spends, the same pool falls
     * again.
     */
    @Test
    public void skillPointsAreAPartyWidePool() {
        Character a = CharacterFactory.create(1003, 80);
        Character b = CharacterFactory.create(1001, 80);
        Battle battle = newBattle(List.of(a, b));

        // A spends 1 point, B spends 1 point - if the pool were "one per person", each deducting
        // their own would be indistinguishable.
        // Here we spend with A and then look at the pool shown when someone else acts, to prove
        // it is the same pool.
        Assertions.assertAll("skill points are one party-wide pool, not one per character",
                () -> Assertions.assertTrue(battle.spendSkillPoint(), "A spends 1 point"),
                () -> Assertions.assertEquals(2, battle.getSkillPoints(),
                        "the pool B sees also dropped by 1 → shared"),
                () -> Assertions.assertTrue(battle.spendSkillPoint(), "B spends 1 point"),
                () -> Assertions.assertEquals(1, battle.getSkillPoints(),
                        "the same pool falls again when B pays"));
    }

    // ==================================================================
    // What an action costs: the ultimate is neutral, the row decides the basic delta
    // ==================================================================

    /**
     * Rule: an ultimate does not spend skill points. Engine consistent.
     *
     * <p><b>L3:</b> the premise and the two observations share one fixture (Jing Yuan's battle) and follow one
     * another, so they are one {@code assertAll}; the cast itself stays between the "pool is at 1" clause and
     * the "still 1" clause.
     */
    @Test
    public void anUltimateNeitherSpendsNorGainsPoints() {
        Character hero = CharacterFactory.create(1204, 80);
        Battle battle = newBattle(List.of(hero));

        while (battle.getSkillPoints() > 1) {
            battle.spendSkillPoint();
        }
        hero.setCurrentEnergy(200);

        Assertions.assertAll("an ultimate is skill-point neutral",
                () -> Assertions.assertEquals(1, battle.getSkillPoints(),
                        "premise: the pool is down to 1 before the ultimate"),
                () -> Assertions.assertTrue(battle.castUltra(hero, List.of(firstEnemy(battle))),
                        "the ultimate is cast"),
                () -> Assertions.assertEquals(1, battle.getSkillPoints(),
                        "the ultimate spends no points"));
    }

    /**
     * The point a skill row moves the pool by is the row's own {@code bp_add}, not the attack type.
     *
     * <p>Note: <b>Resolved for the case the evidence settles</b> (2026-10-02, F-3). The engine no longer
     * hands out +1 from a blanket {@code attack_type == "Normal"}: it reads the skill row's own
     * {@code bp_add}, which {@code skills.json} carries from tbgd's {@code AvatarSkillConfig.BPAdd}.
     *
     * <p>Evidence: {@code 1315_波提欧.md}, "an enhanced basic attack cannot restore a skill
     * point, and can only target an enemy in [绝命对峙]". The enhanced basic attack of
     * {@code 1213_丹恒-饮月.md}, on the other hand, does not spend a skill point ("casting this
     * skill does not consume a skill point and is not treated as using a skill").
     *
     * <p>What is recorded, and what is not: a row's {@code bp_add} holds the value <b>only where the
     * evidence settles it</b> -- every ordinary basic attack (tbgd says {@code BPAdd 1}) and Boothill's
     * enhanced basic (its own text says "the enhanced basic cannot restore skill points", and tbgd
     * carries no {@code BPAdd} there). For every other row the field is absent on purpose, because
     * tbgd's {@code null} does <b>not</b> mean zero: Qingque's enhanced basic also has no
     * {@code BPAdd}, yet {@code 1201_青雀.md} states "after casting an enhanced basic attack, restore
     * 1 skill point". So the field cannot be filled in mechanically -- each character's prose decides,
     * which is why the register keeps that half open.
     *
     * <p>{@code BoothillEnhancedBasicTest} pins both sides of the resolved case, and flipping the row's
     * {@code bp_add} back to 1 turns it red.
     *
     * <p>The other half of the same clause: {@code MazeNormal} (the map basic attack) is neutral (+0) - a map
     * basic attack is used before entering battle, when skill points do not exist yet. This probe pins down
     * "why it is excluded", so that later nobody "casually" counts all non-Normal attack types as +1.
     *
     * <p><b>L3:</b> the two single-clause cases (Boothill's {@code Normal} row = +1, the map
     * {@code MazeNormal} row = 0) share one fixture shape - a one-character battle read through
     * {@code applySkillPointCost}, with its own battle per clause so no pool state leaks - and one observable,
     * the pool's delta. The differing character / skill row is the clause's input, not a different fixture.
     */
    @Test
    public void theSkillRowAloneDecidesThePointDelta() {
        Assertions.assertAll("applySkillPointCost follows the skill row's bp_add, not the attack type",
                () -> {
                    Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
                    Skill maze = new DefaultSkill(1003, 6, 1);
                    Assertions.assertEquals("MazeNormal", maze.getData().getSkillType(),
                            "premise: slot 6 of 1003 is the map basic attack");

                    int before = battle.getSkillPoints();
                    Assertions.assertTrue(battle.applySkillPointCost(maze, battle.characters().getFirst()),
                            "a neutral map basic attack does not block the action");
                    Assertions.assertEquals(before, battle.getSkillPoints(),
                            "a map basic attack produces no skill point");
                },
                () -> {
                    Battle battle = newBattle(List.of(CharacterFactory.create(1315, 80)));   // Boothill
                    Skill enhancedLikeBasic = new DefaultSkill(1315, 1, 1);
                    Assertions.assertEquals("Normal", enhancedLikeBasic.getData().getSkillType(),
                            "premise: in the data the enhanced basic attack is also marked Normal, "
                                    + "with no separate type");

                    int before = battle.getSkillPoints();
                    Assertions.assertTrue(
                            battle.applySkillPointCost(enhancedLikeBasic, battle.characters().getFirst()),
                            "the row states bp_add 1, so the action is not blocked");
                    Assertions.assertEquals(before + 1, battle.getSkillPoints(),
                            "the row states bp_add 1, so an ordinary basic attack restores one point");
                });
    }

    // ==================================================================
    // Refusals: an action that cannot run changes nothing
    // ==================================================================

    /**
     * An action that cannot run changes nothing: at 0 points the skill is refused and deals no damage, and a
     * controlled ({@code StunBuff}) actor can neither act nor have points deducted.
     *
     * <p>Rule: at 0 points a skill cannot be cast (the button greys out). Engine: {@code performAction} returns
     * false and does not queue to no damage. This one is consistent with the game (the game "cannot be
     * clicked", the engine "returns false").
     *
     * <p>While controlled {@code canAct()} blocks the action first, so it can neither act nor have points
     * deducted. In the game, being controlled means skipping the turn, so naturally no skill point is consumed
     * either; consistent.
     *
     * <p><b>L3:</b> both clauses are refusals on the same fixture (a one-character battle acting through
     * {@code actWithoutAfterMove}, slot 2) and observe that a refused action has no effect; no positive clause
     * is folded in. Each clause builds its own battle, so no precondition leaks into the other.
     */
    @Test
    public void anActionThatCannotRunSpendsNothingAndDealsNothing() {
        Assertions.assertAll("a refused action changes neither the pool nor the target's HP",
                () -> {
                    Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
                    Character hero = battle.characters().getFirst();
                    Enemy target = firstEnemy(battle);

                    while (battle.spendSkillPoint()) {
                        // drain to zero
                    }
                    double hp = target.getCurrentHp();
                    Assertions.assertFalse(
                            actWithoutAfterMove(battle, hero, () -> skill(hero, 2), List.of(target)),
                            "0 skill points → the skill is refused");
                    Assertions.assertEquals(hp, target.getCurrentHp(), EPS,
                            "a refused skill deals no damage");
                },
                () -> {
                    Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
                    Character hero = battle.characters().getFirst();
                    hero.getBuffManager().addBuff(new StunBuff(2));

                    int before = battle.getSkillPoints();
                    Assertions.assertFalse(actWithoutAfterMove(battle, hero, () -> skill(hero, 2),
                            List.of(firstEnemy(battle))), "controlled → cannot act");
                    Assertions.assertEquals(before, battle.getSkillPoints(),
                            "being controlled should not deduct points");
                });
    }

    // ==================================================================
    // Boundaries: they do not affect the engine's conclusions, but pin down "cases already thought about"
    // ==================================================================

    /**
     * Note: <b>Worth noting</b>: the engine judges sides with
     * {@link com.laosun.aluminium.enums.Camp}, not with "is this person player-controlled".
     *
     * <p>Consequence: if friendly summons / friendly NPCs are added later (memosprites
     * are units on our side but not "characters"), and they act with {@code Normal},
     * they will also add skill points for our side.
     * In the game, memosprite actions can likewise provide skill points, so this behaviour is
     * probably right - but it is currently a side effect rather than an explicit design, so it
     * is pinned down here.
     *
     * <p><b>L3:</b> both camp clauses are one fixture (one character versus one enemy) and one observable
     * ({@code getCamp()}), so they are one scenario.
     */
    @Test
    public void campPlayerIsTheGateSoFutureAlliesWouldAlsoGrantPoints() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Enemy enemy = firstEnemy(battle);

        Assertions.assertAll("the Camp is the gate: only the player side feeds the pool",
                () -> Assertions.assertEquals(com.laosun.aluminium.enums.Camp.ENEMY, enemy.getCamp(),
                        "the enemy is ENEMY → kept outside the gate"),
                () -> Assertions.assertEquals(com.laosun.aluminium.enums.Camp.PLAYER,
                        battle.characters().getFirst().getCamp(),
                        "the character is PLAYER → let through"));
    }

    /**
     * Characters that do not produce an energy bar (the 6 that use special resources) are still
     * subject to the skill point constraint - skill points are a party-level resource, unrelated
     * to an individual's energy bar / stack count.
     *
     * <p>In the game Acheron still has to spend skill points to cast a skill; consistent.
     *
     * <p><b>L3:</b> the premise (the provider type), the cast and the pool afterwards are one fixture and one
     * observable, so they are one scenario; the cast sits inside the {@code assertAll} so the pool clause sees
     * the settled turn.
     */
    @Test
    public void specialResourceCharactersStillPaySkillPoints() {
        Character acheron = CharacterFactory.create(1308, 80);
        Battle battle = newBattle(List.of(acheron));

        Assertions.assertAll("a special-resource character still pays skill points",
                () -> Assertions.assertTrue(acheron.getEnergyProvider() instanceof NoConventionalEnergyProvider,
                        "premise: Acheron uses the special resource provider"),
                () -> Assertions.assertTrue(actWithRealTurn(battle, acheron, () -> skill(acheron, 2),
                        () -> List.of(firstEnemy(battle))), "Acheron's skill should be castable"),
                () -> Assertions.assertEquals(2, battle.getSkillPoints(), "Acheron pays skill points too"));
    }

    // ==================================================================
    // Helpers (same as SkillPointTest, deliberately duplicated so the two test classes do not depend on each other)
    // ==================================================================

    private static Skill skill(Character hero, int slot) {
        return new DefaultSkill(hero.getCid(), slot, 1);
    }

    private static Battle newBattle(List<Character> team) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = BattleFixture.battle(team, List.of(enemy));
        battle.startBattle();
        return battle;
    }

    private static boolean actWithRealTurn(Battle battle, CanHit actor, Supplier<Skill> skill,
                                           Supplier<List<? extends CanHit>> targets) {
        boolean result = actWithoutAfterMove(battle, actor, skill, targets.get());
        battle.afterMove();
        return result;
    }

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
        Assertions.fail("did not reach " + actor.getName() + "'s turn within 30 steps");
        return false;
    }

    private static Enemy firstEnemy(Battle battle) {
        return battle.enemyUnits().getFirst();
    }
}
