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
import com.laosun.aluminium.models.buffs.StunBuff;
import com.laosun.aluminium.models.energy.NoConventionalEnergyProvider;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Consistency cross-check between skill points and the game rules (P8-4 re-verification).
 *
 * <p>This class is **not** a functional test; it is a probe that "asks the engine one rule at a
 * time": each entry notes the rule's source and the conclusion (consistent / inconsistent / not
 * implemented). Wherever the engine's behaviour differs from the rule, the assertion states the
 * **engine's current behaviour** and the comment marks the difference, so that next time nobody
 * mistakes it for already aligned.
 *
 * <p><b>Rule sources (verified)</b>:
 * <ul>
 *   <li>Party-wide — "skill points are a party-wide shared resource" (9game skill point mechanics
 *       explained);
 *   <li>Cap 5 / basic attack +1 — "each time a character uses a basic attack they restore one
 *       skill point; the skill point cap is 5";
 *   <li><b>Start at 3</b> — the player Q&A states explicitly "normally every battle starts with
 *       three skill points";
 *   <li>The start value is variable — this project's own {@code RELICS.md}: the 4-piece
 *       Passerby set "immediately restores 1 skill point for our side at the start of battle", so
 *       a team wearing it starts at 4 (5 if two characters wear it);
 *   <li>The cap is variable — Sparkle's talent "additionally increases the skill point cap by 2",
 *       and in {@code WEAPONS.md} the Elation light cone "for each character on the Elation path,
 *       the skill point cap increases by 1, up to 3"; there is even a light cone whose trigger
 *       condition is "the skill point cap is greater than or equal to 6" → **the cap is not a
 *       constant 5**.
 * </ul>
 *
 * <p>⚠ Note: this project's spec document {@code HSR.md} ({@code E:\code\blog\hsr\HSR.md})
 * **has no skill point section at all** — only one sentence in §6.1, "punchline: the counter above
 * the skill points", mentions it. So the rule set above is reverse-engineered from the game
 * mechanics and the character documents, not given by the spec.
 */
public class SkillPointGameParityTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // Consistent
    // ==================================================================

    /** Rule: skill points are **party-wide shared**. Engine: the pool lives on {@code Battle}, not one per character. */
    @Test
    public void poolIsPartyWideNotPerCharacter() {
        Character a = CharacterFactory.create(1003, 80);
        Character b = CharacterFactory.create(1001, 80);
        Battle battle = newBattle(List.of(a, b));

        // A spends 1 point, B spends 1 point — if the pool were "one per person", each deducting
        // their own would be indistinguishable.
        // Here we spend with A and then look at the pool shown when **someone else** acts, to prove
        // it is the same pool.
        Assertions.assertTrue(battle.spendSkillPoint(), "A spends 1 point");
        Assertions.assertEquals(2, battle.getSkillPoints(), "the pool B sees also dropped by 1 → shared");

        Assertions.assertTrue(battle.spendSkillPoint(), "B spends 1 point");
        Assertions.assertEquals(1, battle.getSkillPoints());
    }

    /** Rule: start at 3 points. Engine: {@code SKILL_POINT_START}. */
    @Test
    public void battleStartsAtThree() {
        Assertions.assertEquals(3, newBattle(List.of(CharacterFactory.create(1003, 80))).getSkillPoints());
        Assertions.assertEquals(3, Constant.SKILL_POINT_START);
    }

    /**
     * Rule: **every battle starts over** (skill points are not inherited across battles).
     *
     * <p>Engine: {@code skillPoints} is an instance field of {@code Battle}, so a new battle is
     * naturally 3.
     */
    @Test
    public void eachBattleStartsFresh() {
        Character hero = CharacterFactory.create(1003, 80);
        Battle first = newBattle(List.of(hero));
        first.spendSkillPoint();
        first.spendSkillPoint();
        Assertions.assertEquals(1, first.getSkillPoints());

        Battle second = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Assertions.assertEquals(3, second.getSkillPoints(), "a new battle does not inherit the previous one's leftovers");
    }

    /**
     * Rule: at 0 points a **skill cannot be cast** (the button greys out).
     *
     * <p>Engine: {@code performAction} returns false and does not queue → no damage.
     * This one is consistent with the game (the game "cannot be clicked", the engine "returns false").
     */
    @Test
    public void zeroPointsMeansSkillUnavailable() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Character hero = battle.characters.getFirst();
        Enemy target = firstEnemy(battle);

        while (battle.spendSkillPoint()) {
            // drain to zero
        }
        double hp = target.getCurrentHp();
        Assertions.assertFalse(actWithoutAfterMove(battle, hero, () -> skill(hero, 2), List.of(target)));
        Assertions.assertEquals(hp, target.getCurrentHp(), EPS);
    }

    /**
     * Rule: an ultimate **does not spend** skill points. Engine consistent.
     */
    @Test
    public void ultimateDoesNotSpendPoints() {
        Character hero = CharacterFactory.create(1204, 80);
        Battle battle = newBattle(List.of(hero));

        while (battle.getSkillPoints() > 1) {
            battle.spendSkillPoint();
        }
        Assertions.assertEquals(1, battle.getSkillPoints());

        hero.setCurrentEnergy(200);
        Assertions.assertTrue(battle.castUltra(hero, List.of(firstEnemy(battle))));
        Assertions.assertEquals(1, battle.getSkillPoints(), "the ultimate spends no points");
    }

    // ==================================================================
    // Inconsistent / worth noting
    // ==================================================================

    /**
     * ⚠ <b>Inconsistent</b>: in the rule, the body of "basic attack +1" is a **normal attack that
     * consumes 1 action's worth of action** — whereas the engine decides by
     * {@code attack_type == "Normal"} — that is, it is correct that **techniques do not enter
     * battle**, but {@code MazeNormal} (map basic attack) is excluded.
     *
     * <p>Engine's current behaviour: {@code MazeNormal} is **neutral** (+0). This is consistent with
     * the game — a map basic attack is used **before entering battle**, when skill points do not
     * exist yet.
     * This probe's purpose is to pin down "why it is excluded", so that later nobody "casually"
     * counts all non-Normal attack types as +1.
     */
    @Test
    public void mazeNormalIsNeutralBecauseItIsPreBattle() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Skill maze = new DefaultSkill(1003, 6, 1);
        Assertions.assertEquals("MazeNormal", maze.getData().getSkillType());

        int before = battle.getSkillPoints();
        Assertions.assertTrue(battle.applySkillPointCost(maze, battle.characters.getFirst()));
        Assertions.assertEquals(before, battle.getSkillPoints(), "a map basic attack produces no skill point");
    }

    /**
     * ⚠ <b>Not implemented</b>: characters / light cones / relics can all modify skill points — the
     * engine has only one party-wide {@code gainSkillPoint}, with no hook at all for "correct the
     * gain / cap by source".
     *
     * <p>Evidence (all from this project's own data documents):
     * <ul>
     *   <li>{@code 1101_布洛妮娅.md}: when casting a skill, 50% chance to restore 1 skill point (1-turn cooldown);
     *   <li>{@code 1201_青雀.md}: 争番 (Fight For All) "when casting a skill, restore 1 skill point; can only trigger once per battle";
     *   <li>{@code 1215_寒鸦.md}: after casting 2 basic attacks / skills / ultimates on a
     *       【承负】 target, restore 1 point for our side;
     *   <li>{@code 1223_貊泽.md}: after casting the talent's follow-up attack, restore 1 skill point (can trigger again after 1 turn);
     *   <li>{@code 1206_素裳.md}: after casting a skill on a target in the broken state, restore 1 skill point;
     *   <li>{@code 1312_米沙.md}: every time our whole side spends 1 skill point → Misha's next
     *       ultimate gains +1 segment, and Misha regains 2 energy
     *       (this one additionally has to listen for the act of "spending a skill point" itself).
     * </ul>
     *
     * <p>Ownership: P8-7 trigger table ({@code GAIN_SKILL_POINT} / per-character gain correction) +
     * a needed opening to "change the party-level resource cap". **This probe pins down "not done
     * yet"**, so that the current state is not misread as "skill points are already complete".
     */
    @Test
    public void characterAndGearSkillPointModifiersAreNotImplemented() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));

        // The cap is always the constant: Sparkle's talent +2 and the Elation light cone's +1~3
        // have no attachment point
        battle.gainSkillPoint(100);
        Assertions.assertEquals(Constant.SKILL_POINT_MAX, battle.getSkillPoints(),
                "the cap is always the constant 5; characters / light cones cannot change it");

        // The gain is always the constant too: Bronya's "50% chance +1" and Sushang's
        // "hit a broken target +1" have no attachment point
        Assertions.assertEquals(1, Constant.SKILL_POINT_GAIN_BASIC,
                "the basic attack gain is always the constant 1; character-level extra point supply has no attachment point");
    }

    /**
     * ⚠ <b>Known deviation</b>: the engine hands out +1 with a blanket
     * {@code attack_type == "Normal"}, but in the game there are exceptions where **an enhanced
     * basic attack does not restore a skill point**.
     *
     * <p>Evidence: {@code 1315_波提欧.md} "an enhanced basic attack **cannot restore a skill
     * point**, and can only target an enemy in 【绝命对峙】". The enhanced basic attack of
     * {@code 1213_丹恒•饮月.md}, on the other hand, **does not spend a skill point** ("casting this
     * skill does not consume a skill point and is not treated as using a skill").
     *
     * <p>Why it "happens to line up" right now: in this project's data the enhanced basic attacks
     * are also {@code "Normal"}, with no separate type (122 {@code Normal} entries measured =
     * 93 characters × 1 + the multi-tier enhanced basic attacks of Dan Heng • Imbibitor Lunae /
     * Jingliu / Qingque / Boothill), so the engine gives them +1. That is correct for Qingque
     * ({@code 1201_青雀.md} states explicitly "after casting an enhanced basic attack, restore
     * 1 skill point") and **wrong** for Boothill.
     *
     * <p>⚠ But it **MUST NOT be changed to "enhanced basic attacks are always +0"**: that would
     * break Qingque.
     * The real fix is "each skill carries its own skill point gain field", which is a data
     * completion task, not an engine logic problem.
     * This probe writes down the fact of the blanket rule, so that later nobody reads only
     * {@code engine.md} and assumes "who restores skill points" is already exact.
     */
    @Test
    public void normalAttackIsBlanketPlusOneSoEnhancedNormalsAlsoGain() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1315, 80)));   // Boothill
        Skill enhancedLikeBasic = new DefaultSkill(1315, 1, 1);
        Assertions.assertEquals("Normal", enhancedLikeBasic.getData().getSkillType(),
                "premise: in the data the enhanced basic attack is also marked Normal, with no separate type");

        int before = battle.getSkillPoints();
        Assertions.assertTrue(battle.applySkillPointCost(enhancedLikeBasic, battle.characters.getFirst()));
        Assertions.assertEquals(before + 1, battle.getSkillPoints(),
                "the engine's blanket +1 — correct for Qingque, a known deviation for Boothill (enhanced basic attack restores nothing)");
    }

    /**
     * ⚠ <b>Not implemented</b>: the "starting skill point" of relics / light cones.
     *
     * <p>{@code RELICS.md}: 4-piece Passerby set "at the start of battle, immediately restore
     * 1 skill point for our side" → a team wearing it starts at 4 points (5 if two characters
     * wear it).
     * The engine's start value is always {@code SKILL_POINT_START}, and **relic set effects are not
     * wired up at all** ({@code relic_sets.json} is not even loaded).
     */
    @Test
    public void relicBattleStartSkillPointIsNotImplemented() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Assertions.assertEquals(Constant.SKILL_POINT_START, battle.getSkillPoints(),
                "the start is always 3; the 4-piece Passerby set's +1 has no attachment point");
    }

    /**
     * ⚠ <b>Worth noting</b>: the engine judges sides with
     * {@link com.laosun.aluminium.enums.Camp}, not with "is this person player-controlled".
     *
     * <p>Consequence: if **friendly summons / friendly NPCs** are added later (memosprites are
     * P9-4; they are units on our side but not "characters"), and they act with {@code Normal},
     * they **will also add skill points for our side**.
     * In the game, memosprite actions can likewise provide skill points, so this behaviour is
     * probably right — but it is currently a **side effect** rather than an explicit design, so it
     * is pinned down here.
     */
    @Test
    public void campPlayerIsTheGateSoFutureAlliesWouldAlsoGrantPoints() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Enemy enemy = firstEnemy(battle);
        Assertions.assertEquals(com.laosun.aluminium.enums.Camp.ENEMY, enemy.getCamp(),
                "the enemy is ENEMY → kept outside the gate");
        Assertions.assertEquals(com.laosun.aluminium.enums.Camp.PLAYER,
                battle.characters.getFirst().getCamp(), "the character is PLAYER → let through");
    }

    // ==================================================================
    // Boundaries: they do not affect the engine's conclusions, but pin down "cases already thought about"
    // ==================================================================

    /**
     * While controlled ({@code StunBuff}) it **can neither act nor have points deducted** — because
     * {@code canAct()} blocks it first.
     *
     * <p>In the game, being controlled means skipping the turn, so naturally no skill point is
     * consumed either; consistent.
     */
    @Test
    public void stunnedActorNeitherActsNorSpendsPoints() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Character hero = battle.characters.getFirst();
        hero.getBuffManager().addBuff(new StunBuff(2));

        int before = battle.getSkillPoints();
        Assertions.assertFalse(actWithoutAfterMove(battle, hero, () -> skill(hero, 2),
                List.of(firstEnemy(battle))), "controlled → cannot act");
        Assertions.assertEquals(before, battle.getSkillPoints(), "being controlled should not deduct points");
    }

    /**
     * Characters that do not produce an energy bar (the 6 that use special resources) are **still
     * subject to the skill point constraint** — skill points are a party-level resource, unrelated
     * to an individual's energy bar / stack count.
     *
     * <p>In the game Acheron still has to spend skill points to cast a skill; consistent.
     */
    @Test
    public void specialResourceCharactersStillPaySkillPoints() {
        Character acheron = CharacterFactory.create(1308, 80);
        Assertions.assertTrue(acheron.getEnergyProvider() instanceof NoConventionalEnergyProvider,
                "premise: Acheron uses the special resource provider");

        Battle battle = newBattle(List.of(acheron));
        Assertions.assertTrue(actWithRealTurn(battle, acheron, () -> skill(acheron, 2),
                () -> List.of(firstEnemy(battle))), "Acheron's skill should be castable");
        Assertions.assertEquals(2, battle.getSkillPoints(), "Acheron pays skill points too");
    }

    // ==================================================================
    // Helpers (same as SkillPointTest, deliberately duplicated so the two test classes do not depend on each other)
    // ==================================================================

    private static Skill skill(Character hero, int slot) {
        return new DefaultSkill(hero.getCid(), slot, 1);
    }

    private static Battle newBattle(List<Character> team) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(team, List.of(enemy), new Random(0));
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
            if (battle.currentMove == null) {
                break;
            }
            if (battle.currentMove.getCanHit() == actor) {
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
