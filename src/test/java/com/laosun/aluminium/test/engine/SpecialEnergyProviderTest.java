package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.energy.EnergyProvider;
import com.laosun.aluminium.energy.NoConventionalEnergyProvider;
import com.laosun.aluminium.energy.StandardEnergyProvider;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Special energy-provider characters use an independent {@link EnergyProvider}
 * ({@link NoConventionalEnergyProvider}).
 *
 * <p>Background: Feixiao 1220 / Acheron 1308 / Castorice 140/ Phainon 1408 / Cyrene 1415 /
 * Silver Wolf LV.999 1506 accumulate, in the game, not energy but stacks / resources such as
 * [新蕊]/[火种]/[追忆].
 *
 * <p>Why all 5 hooks MUST be blocked: {@code castUltra}'s threshold is
 * {@code currentEnergy >= maxEnergy}, and these characters' caps are very low (Acheron 9,
 * Feixiao/Phainon 12). If only the two skill hooks were plugged, they would fill up after taking
 * one or two hits and cast an ultimate that should not exist (slot 3 really is {@code Ultra}).
 * This class is that guardrail.
 *
 * <p>Why the empty check does not live in {@link StandardEnergyProvider}: that is a design
 * classification rather than a single data fact, so as a matter of the three-way split it belongs to the
 * provider / assembly point (which is also the only place {@code cid} is allowed to appear).
 */
public class SpecialEnergyProviderTest {
    private static final double EPS = 1e-9;

    /**
     * Every character in the project that "uses a special resource", written out one by one (adding or removing one must be an explicit change).
     */
    private static final int[] SPECIAL = {1220, 1308, 1407, 1408, 1415, 1506};

    private final EnergyProvider standard = new StandardEnergyProvider();
    private final EnergyProvider none = new NoConventionalEnergyProvider();

    // ==================================================================
    // 1. The provider itself
    // ==================================================================

    /**
     * All 5 hooks credit nothing - this is the complete expression of "no source should grant energy".
     */
    @Test
    public void specialProviderGrantsNothingFromAnySource() {
        Character any = CharacterFactory.create(1308, 80);      // Acheron
        Skill skill = realSkill(1308, SkillType.COMMON);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Damage hit = new Damage(any, enemy, DamageElement.THUNDER, DamageType.NORMAL, 100);

        Assertions.assertAll(
                () -> Assertions.assertNull(none.onSkillCast(any, skill, Set.of()), "skill cast"),
                () -> Assertions.assertNull(none.onUltCast(any, realSkill(1308, SkillType.ULTRA)), "ultimate"),
                () -> Assertions.assertNull(none.onTakingHit(any, hit), "taking a hit"),
                () -> Assertions.assertNull(none.onKill(any, any), "kill"),
                () -> Assertions.assertNull(none.onBreak(any, any), "break"));
    }

    /**
     * Control: the standard provider's same 5 hooks all grant something (hit 10 / kill 5 / break 5),
     * and the skill-cast amount is the skill's <b>own</b> {@code sp_base} rather than a constant.
     *
     * <p>⚠ <b>CORRECTED (2026-10-17)</b>: the off-tier row used to be asserted the other way round - 20 for a
     * basic attack whose data says 30 - and its message recorded the constant as the deliberate choice,
     * 「fidelity to the off-tier value is left to datafication」. That datafication is what happened: the provider
     * now multiplies the row's own {@code sp_base} by the instance count, and the constants survive only as the
     * fallback for a row that states nothing. A case that pinned the pre-datafication number would have gone green
     * on the very regression this change is meant to remove.
     */
    @Test
    public void standardProviderGrantsFromEverySource() {
        Character regular = CharacterFactory.create(1204, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        // onTakingHit checks damage == null itself (that means "no damage event"), so give it a real damage instance here
        Damage hit = new Damage(regular, enemy, DamageElement.THUNDER, DamageType.NORMAL, 100);
        Character yaoGuang = CharacterFactory.create(1502, 80);   // Yao Guang: the basic-attack data says 30 (off-tier)

        Assertions.assertAll(
                () -> Assertions.assertEquals(20, standard.onSkillCast(regular,
                        realSkill(1204, SkillType.COMMON), Set.of()).amount(), EPS,
                        "basic attack uses the row's own sp_base (20 here)"),
                () -> Assertions.assertEquals(30, standard.onSkillCast(regular,
                        realSkill(1204, SkillType.SKILL), Set.of()).amount(), EPS,
                        "skill uses the row's own sp_base (30)"),
                () -> Assertions.assertEquals(5, standard.onUltCast(regular,
                        realSkill(1204, SkillType.ULTRA)).amount(), EPS,
                        "ultimate grants the flat 5"),
                () -> Assertions.assertEquals(10, standard.onTakingHit(regular, hit).amount(), EPS,
                        "taking a hit grants the flat 10"),
                () -> Assertions.assertEquals(5, standard.onKill(regular, regular).amount(), EPS,
                        "kill grants the flat 5"),
                () -> Assertions.assertEquals(5, standard.onBreak(regular, regular).amount(), EPS,
                        "break grants the flat 5"),
                () -> Assertions.assertEquals(30, standard.onSkillCast(yaoGuang,
                                realSkill(1502, SkillType.COMMON), Set.of()).amount(), EPS,
                        "the data's own sp_base (30) wins over the conventional 20"));
    }

    // ==================================================================
    // 2. The assembly point
    // ==================================================================

    /**
     * The assembly point picks the provider from the character's resource: {@code CharacterFactory} swaps in the
     * special provider for these 6 characters, while a regular character and a data-free placeholder entry point
     * both keep the standard provider.
     */
    @Test
    public void characterCreationAttachesTheProviderMatchingTheResource() {
        List<Executable> checks = new ArrayList<>();
        for (int cid : SPECIAL) {
            Character c = CharacterFactory.create(cid, 80);
            checks.add(() -> Assertions.assertTrue(c.getEnergyProvider() instanceof NoConventionalEnergyProvider,
                    "cid=" + cid + " should use the special provider, actual " + c.getEnergyProvider().getClass().getSimpleName()));
            checks.add(() -> Assertions.assertTrue(CharacterFactory.usesSpecialResource(cid),
                    "cid=" + cid + " should be classified as using a special resource"));
        }

        Character jingYuan = CharacterFactory.create(1204, 80);
        checks.add(() -> Assertions.assertTrue(jingYuan.getEnergyProvider() instanceof StandardEnergyProvider,
                "a regular character still uses the standard provider"));
        checks.add(() -> Assertions.assertFalse(CharacterFactory.usesSpecialResource(1204),
                "1204 is not a special-resource character"));

        // Placeholder entry points are unaffected: they do not look up character data, so there is no cid to judge by
        Character placeholder = Character.fromAttributes("hero", 10_000, 100, 100, 100);
        checks.add(() -> Assertions.assertTrue(placeholder.getEnergyProvider() instanceof StandardEnergyProvider,
                "a placeholder keeps the standard provider"));

        Assertions.assertAll(checks);
    }

    // ==================================================================
    // 3. End to end: this is the anti-regression part
    // ==================================================================

    /**
     * The core: no special-resource character gains a single point of energy in a real battle, Acheron included.
     *
     * <p>Her cap is only 9; before the fix one hit (+10) filled it and she could cast an ultimate
     * straight away, so she is run first (seed 0) and then again inside the exhaustive sweep (seed 1).
     */
    @Test
    public void specialResourceCharactersNeverGainEnergyInBattle() {
        List<Executable> checks = new ArrayList<>();

        Character acheron = runBattle(1308, 0L);
        checks.add(() -> Assertions.assertEquals(0, acheron.getCurrentEnergy(), EPS,
                "Acheron's energy should always be 0 (neither hits nor skills are credited), actual " + acheron.getCurrentEnergy()));
        checks.add(() -> Assertions.assertFalse(acheron.isEnergyFull(),
                "she should never be at full energy — being full lets her cast an ultimate that should not exist"));

        for (int cid : SPECIAL) {
            Character c = runBattle(cid, 1L);
            checks.add(() -> Assertions.assertEquals(0, c.getCurrentEnergy(), EPS,
                    "cid=" + cid + " (" + c.getName() + ")'s energy should always be 0"));
        }

        Assertions.assertAll(checks);
    }

    /**
     * Control: Jing Yuan takes the same hits, and his energy does rise (taking a hit grants 10 energy).
     */
    @Test
    public void regularCharacterStillGainsFromBeingHit() {
        Character jingYuan = runBattle(1204, 0L);

        Assertions.assertTrue(jingYuan.getCurrentEnergy() > 0,
                "a regular character should have energy credited, actual " + jingYuan.getCurrentEnergy());
    }

    // ==================================================================

    /**
     * Runs the shared end-to-end fixture: one character against one enemy, six moves alternating the enemy's
     * basic attack (aimed at the character) and the character's own basic attack (aimed at the enemy).
     */
    private static Character runBattle(int cid, long seed) {
        Character character = CharacterFactory.create(cid, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(character), List.of(enemy), new Random(seed));
        battle.startBattle();

        // The enemy has 132 speed and acts first
        for (int i = 0; i < 6 && !battle.isOver(); i++) {
            battle.stepForward();
            if (battle.currentMove() == null) {
                break;
            }
            CanHit actor = battle.currentMove().getCanHit();
            battle.beforeMove();
            if (actor == enemy) {
                battle.performAction(enemySkill(enemy), List.of(character));
            } else {
                battle.performAction(realSkill(cid, SkillType.COMMON), List.of(enemy));
            }
            battle.afterMove();
        }
        return character;
    }

    private static Skill realSkill(int cid, SkillType type) {
        int slot = switch (type) {
            case COMMON -> 1;
            case SKILL -> 2;
            case ULTRA -> 3;
            default -> throw new IllegalArgumentException("this test only uses slots 1/2/3");
        };
        return new DefaultSkill(cid, slot, 1);
    }

    /**
     * The enemy's basic attack (the skill attached to enemies at assembly).
     */
    private static Skill enemySkill(Enemy enemy) {
        return enemy.getSkills().values().iterator().next();
    }
}
