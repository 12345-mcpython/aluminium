package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DefaultSkill;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Skill;
import com.laosun.aluminium.models.skillpoint.SkillPointPolicy;
import com.laosun.aluminium.models.skillpoint.StandardSkillPointPolicy;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * **Purpose verification** for the refactor: can the engine really be extended by character mechanics
 * while itself "knowing nothing about character mechanics"?
 *
 * <p><b>F-8</b> in {@code DOC_VS_CODE.md} §F says "the skill point policy must be pulled out of
 * {@code Battle}", and this class is the acceptance test for that sentence — **without changing a single
 * line of the engine**, it only swaps {@link Battle#skillPointPolicy} and sees whether the engine follows
 * the new rules.
 *
 * <p>The three assertions correspond to three kinds of real-world needs in the future:
 * <ol>
 *   <li>{@link #customPolicyChangesTheBasicAttackGain()} — character-level point provision
 *       (Sparkle "every 3 basic attacks +1 extra", Sushang "+1 when hitting a broken target");</li>
 *   <li>{@link #customPolicyRaisesTheCap()} — cap-type modifications
 *       (Sparkle's talent +2, the Elation light cone +1 per Elation character, corresponding to F-1 in §F);</li>
 *   <li>{@link #customPolicyCanChangeTheStartingValue()} — start-of-battle modifications
 *       (the 4-piece Passerby set "at the start of battle +1", corresponding to F-2 in §F).</li>
 * </ol>
 *
 * <p>⚠ These subclasses are **test doubles**, not character implementations to be delivered — when
 * characters are really implemented they should be driven by the P8-7 trigger table ({@code cid} only
 * appears at an assembly point (装配点) or in an effect table, the P8-0 three-way split (三分法)).
 * What this class proves is "the hooks on the engine side are sufficient", not "the characters are
 * already done".
 */
public class SkillPointPolicyExtensibilityTest {
    private static final double EPS = 1e-9;

    // ==================================================================
    // 1. Character-level point provision: swap the policy → the engine follows the new rules
    // ==================================================================

    /**
     * Custom policy: a basic attack restores **2** points (instead of 1).
     *
     * <p>Simulates effects like "Sparkle is on the team, basic attacks give +1 extra".
     */
    private static final class DoubleGainPolicy extends StandardSkillPointPolicy {
        @Override
        protected int gainForCast(CanHit user, Skill skill, SkillCategory category) {
            return 2;
        }
    }

    @Test
    public void customPolicyChangesTheBasicAttackGain() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Character hero = battle.characters.getFirst();

        // Swap the policy — this is the only "wiring" action, and not one line of Battle was changed
        battle.skillPointPolicy = new DoubleGainPolicy();
        Assertions.assertEquals(3, battle.getSkillPoints(), "the start is still 3 (the policy's initial value)");

        // ⚠ The policy starts at 3 and one basic attack gives +2 → 4 (capped at 5), so assert
        // "it grew by at least 2 rather than 1"
        Assertions.assertTrue(actWithRealTurn(battle, hero, () -> skill(hero, 1),
                () -> List.of(firstEnemy(battle))));
        Assertions.assertEquals(5, battle.getSkillPoints(),
                "3 + 2 = 5 (it would be 4 if the built-in +1 were still in effect)");
    }

    // ==================================================================
    // 2. Cap-type modifications: the cap is no longer the constant 5
    // ==================================================================

    /**
     * Custom policy: cap **7**, start 3 (the effect of Sparkle's talent +2).
     *
     * <p>Corresponds to <b>F-1</b> in {@code DOC_VS_CODE.md} §F: the engine used to hard-code the cap in
     * {@code Constant.SKILL_POINT_MAX}, so it could not be raised by team configuration.
     */
    @Test
    public void customPolicyRaisesTheCap() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        battle.skillPointPolicy = new StandardSkillPointPolicy(7, 3);

        Assertions.assertEquals(7, battle.getSkillPointMax(), "the cap follows the policy, no longer the constant 5");
        Assertions.assertEquals(3, battle.getSkillPoints());

        battle.gainSkillPoint(100);
        Assertions.assertEquals(7, battle.getSkillPoints(), "capped at the new cap of 7");
    }

    // ==================================================================
    // 3. Start-of-battle modifications: the start value is no longer the constant 3
    // ==================================================================

    /**
     * Custom policy: start **4**, cap 5 (the effect of the 4-piece Passerby set "at the start of battle +1").
     *
     * <p>Corresponds to <b>F-2</b> in {@code DOC_VS_CODE.md} §F.
     */
    @Test
    public void customPolicyCanChangeTheStartingValue() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        battle.skillPointPolicy = new StandardSkillPointPolicy(5, 4);

        Assertions.assertEquals(4, battle.getSkillPoints(), "the start is 4, no longer the constant 3");
        Assertions.assertEquals(5, battle.getSkillPointMax());
    }

    // ==================================================================
    // 4. The default policy must not be bypassed: the interface is the only entry point
    // ==================================================================

    /**
     * Under the default policy, {@code Battle}'s read/write accessors and the policy are **always
     * consistent** — there is no "second, unmanaged copy of the skill point state inside the engine".
     */
    @Test
    public void battleFacadeNeverDivergesFromThePolicy() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        SkillPointPolicy policy = battle.skillPointPolicy;

        battle.gainSkillPoint(1);
        Assertions.assertEquals(policy.getValue(), battle.getSkillPoints());
        Assertions.assertEquals(policy.getMax(), battle.getSkillPointMax());

        battle.spendSkillPoint();
        Assertions.assertEquals(policy.getValue(), battle.getSkillPoints());
        Assertions.assertEquals(policy.canAfford(), battle.hasSkillPoint());

        while (battle.spendSkillPoint()) {
            // drain it
        }
        Assertions.assertEquals(0, battle.getSkillPoints());
        Assertions.assertFalse(battle.hasSkillPoint());
        Assertions.assertEquals(0, policy.getValue());
    }

    /**
     * The default policy is exactly the base game rule, not one word changed — a refactor must not change
     * behaviour.
     */
    @Test
    public void defaultPolicyIsStillTheVanillaRule() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));

        Assertions.assertTrue(battle.skillPointPolicy instanceof StandardSkillPointPolicy);
        Assertions.assertEquals(Constant.SKILL_POINT_START, battle.getSkillPoints());
        Assertions.assertEquals(Constant.SKILL_POINT_MAX, battle.getSkillPointMax());

        Character hero = battle.characters.getFirst();
        Assertions.assertTrue(actWithRealTurn(battle, hero, () -> skill(hero, 1),
                () -> List.of(firstEnemy(battle))));
        Assertions.assertEquals(Constant.SKILL_POINT_START + Constant.SKILL_POINT_GAIN_BASIC,
                battle.getSkillPoints(), "basic attack +1 (default policy)");
    }

    // ==================================================================
    // 5. Policy injection also holds for "enemy" semantics
    // ==================================================================

    /**
     * The custom policy **itself** decides whether to check the camp — the engine no longer judges on its
     * behalf.
     *
     * <p>This pins down the boundary of responsibility: whether an enemy action counts towards skill points
     * is the **policy's** business ({@code StandardSkillPointPolicy} checks {@code Camp.PLAYER}), not
     * {@code Battle}'s. When "friendly summons" (P9-4) are added later and this rule has to be adjusted,
     * what changes is the policy, not the engine.
     */
    @Test
    public void campJudgementBelongsToThePolicyNotTheBattle() {
        Battle battle = newBattle(List.of(CharacterFactory.create(1003, 80)));
        Enemy enemy = firstEnemy(battle);

        // Deliberately swap in a policy that does not check the camp: an enemy basic attack should gain a
        // point too (proving the policy is in charge, not the engine)
        battle.skillPointPolicy = new StandardSkillPointPolicy() {
            @Override
            public boolean onSkillCast(CanHit user, Skill skill) {
                if (skill != null && skill.getData() != null
                        && skill.getData().getCategory() == SkillCategory.NORMAL) {
                    gain(1);
                }
                return true;
            }
        };

        int before = battle.getSkillPoints();
        Assertions.assertTrue(actWithRealTurn(battle, enemy,
                () -> new DefaultSkill(1003, 1, 1), () -> List.of(battle.characters.getFirst())));
        Assertions.assertEquals(before + 1, battle.getSkillPoints(),
                "with a policy that does not check the camp, an enemy basic attack gains a point too → so the camp judgement lives in the policy, not in Battle");
    }

    // ==================================================================
    // helpers
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
        Assertions.fail("the turn of " + actor.getName() + " did not come up within 30 steps");
        return false;
    }

    private static Enemy firstEnemy(Battle battle) {
        return battle.enemyUnits().getFirst();
    }
}
