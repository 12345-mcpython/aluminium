package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.Skill;
import com.laosun.aluminium.models.energy.EnergyProvider;
import com.laosun.aluminium.models.energy.NoConventionalEnergyProvider;
import com.laosun.aluminium.models.energy.StandardEnergyProvider;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Special energy-provider characters use an **independent {@link EnergyProvider}**
 * ({@link NoConventionalEnergyProvider}).
 *
 * <p>Background: Feixiao 1220 / Acheron 1308 / Castorice 1407 / Phainon 1408 / Cyrene 1415 /
 * Silver Wolf LV.999 1506 accumulate, in the game, not energy but stacks / resources such as
 * 【新蕊】/【火种】/【追忆】.
 *
 * <p>Why all 5 hooks MUST be blocked: {@code castUltra}'s threshold is
 * {@code currentEnergy >= maxEnergy}, and these characters' caps are very low (Acheron **9**,
 * Feixiao/Phainon **12**). If only the two skill hooks were plugged, they would fill up after taking
 * one or two hits and **cast an ultimate that should not exist** (slot 3 really is {@code Ultra}).
 * This class is that guardrail.
 *
 * <p>Why the empty check does not live in {@link StandardEnergyProvider}: that is a **design
 * classification** rather than a single data fact, so per P8-0's three-way split it belongs to the
 * provider / assembly point (which is also the only place {@code cid} is allowed to appear).
 */
public class SpecialEnergyProviderTest {
    private static final double EPS = 1e-9;

    /** Every character in the project that "uses a special resource", written out one by one (adding or removing one must be an explicit change). */
    private static final int[] SPECIAL = {1220, 1308, 1407, 1408, 1415, 1506};

    private final EnergyProvider standard = new StandardEnergyProvider();
    private final EnergyProvider none = new NoConventionalEnergyProvider();

    // ==================================================================
    // 1. The provider itself
    // ==================================================================

    /** All 5 hooks credit nothing — this is the complete expression of "no source should grant energy". */
    @Test
    public void specialProviderGrantsNothingFromAnySource() {
        Character any = CharacterFactory.create(1308, 80);      // Acheron
        Skill skill = realSkill(1308, SkillType.COMMON);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        var hit = new com.laosun.aluminium.models.Damage(any, enemy,
                com.laosun.aluminium.enums.DamageElement.THUNDER,
                com.laosun.aluminium.enums.DamageType.NORMAL, 100);

        Assertions.assertNull(none.onSkillCast(any, skill, Set.of()), "skill cast");
        Assertions.assertNull(none.onUltCast(any, realSkill(1308, SkillType.ULTRA)), "ultimate");
        Assertions.assertNull(none.onTakingHit(any, hit), "taking a hit");
        Assertions.assertNull(none.onKill(any, any), "kill");
        Assertions.assertNull(none.onBreak(any, any), "break");
    }

    /** Control: the standard provider's same 5 hooks all grant something (hit 10 / kill 5 / break 5). */
    @Test
    public void standardProviderGrantsFromEverySource() {
        Character regular = CharacterFactory.create(1204, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        // onTakingHit checks damage == null itself (that means "no damage event"), so give it a real damage instance here
        var hit = new com.laosun.aluminium.models.Damage(regular, enemy,
                com.laosun.aluminium.enums.DamageElement.THUNDER,
                com.laosun.aluminium.enums.DamageType.NORMAL, 100);

        Assertions.assertEquals(20, standard.onSkillCast(regular,
                realSkill(1204, SkillType.COMMON), Set.of()).amount(), EPS);
        Assertions.assertEquals(30, standard.onSkillCast(regular,
                realSkill(1204, SkillType.SKILL), Set.of()).amount(), EPS);
        Assertions.assertEquals(5, standard.onUltCast(regular,
                realSkill(1204, SkillType.ULTRA)).amount(), EPS);
        Assertions.assertEquals(10, standard.onTakingHit(regular, hit).amount(), EPS);
        Assertions.assertEquals(5, standard.onKill(regular, regular).amount(), EPS);
        Assertions.assertEquals(5, standard.onBreak(regular, regular).amount(), EPS);
    }

    /** The standard provider still uses the constants (20/30/5), matching ROADMAP P3-0's standard tier — it does not depend on skill data. */
    @Test
    public void standardProviderUsesTheConventionalConstants() {
        Character yaoGuang = CharacterFactory.create(1502, 80);   // Yao Guang: the basic-attack data says 30 (off-tier)
        Assertions.assertEquals(20, standard.onSkillCast(yaoGuang,
                        realSkill(1502, SkillType.COMMON), Set.of()).amount(), EPS,
                "the standard provider grants the constant 20 (fidelity to the off-tier value is left to P3-4's datafication)");
    }

    // ==================================================================
    // 2. The assembly point
    // ==================================================================

    /** {@code CharacterFactory} swaps in the special provider for these 6 characters. */
    @Test
    public void factoryInjectsTheSpecialProvider() {
        for (int cid : SPECIAL) {
            Character c = CharacterFactory.create(cid, 80);
            Assertions.assertTrue(c.getEnergyProvider() instanceof NoConventionalEnergyProvider,
                    "cid=" + cid + " should use the special provider, actual " + c.getEnergyProvider().getClass().getSimpleName());
            Assertions.assertTrue(CharacterFactory.usesSpecialResource(cid));
        }
        Character jingYuan = CharacterFactory.create(1204, 80);
        Assertions.assertTrue(jingYuan.getEnergyProvider() instanceof StandardEnergyProvider,
                "a regular character still uses the standard provider");
        Assertions.assertFalse(CharacterFactory.usesSpecialResource(1204));
    }

    /** Placeholder entry points are unaffected (it does not look up character data, so there is no cid to judge by). */
    @Test
    public void placeholderCharactersKeepTheStandardProvider() {
        Character placeholder = Character.fromAttributes("hero", 10_000, 100, 100, 100);
        Assertions.assertTrue(placeholder.getEnergyProvider() instanceof StandardEnergyProvider);
    }

    // ==================================================================
    // 3. End to end: this is the anti-regression part
    // ==================================================================

    /**
     * The core: Acheron gains **not a single point of energy** from being hit.
     *
     * <p>Her cap is only 9; before the fix one hit (+10) filled it and she could cast an ultimate
     * straight away.
     */
    @Test
    public void acheronGainsNothingFromBeingHit() {
        Character acheron = CharacterFactory.create(1308, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(acheron), List.of(enemy), new Random(0));
        battle.startBattle();

        // Let the enemy hit her a few times: the enemy has 132 speed and acts first
        for (int i = 0; i < 6 && !battle.isOver(); i++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            CanHit actor = battle.currentMove.getCanHit();
            battle.beforeMove();
            if (actor == enemy) {
                battle.performAction(enemySkill(enemy), List.of(acheron));
            } else {
                battle.performAction(realSkill(1308, SkillType.COMMON), List.of(enemy));
            }
            battle.afterMove();
        }

        Assertions.assertEquals(0, acheron.getCurrentEnergy(), EPS,
                "Acheron's energy should always be 0 (neither hits nor skills are credited), actual " + acheron.getCurrentEnergy());
        Assertions.assertFalse(acheron.isEnergyFull(),
                "she should never be at full energy — being full lets her cast an ultimate that should not exist");
    }

    /**
     * Control: Jing Yuan takes the same hits, and his energy does rise (taking a hit grants 10 energy).
     */
    @Test
    public void regularCharacterStillGainsFromBeingHit() {
        Character jingYuan = CharacterFactory.create(1204, 80);
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        Battle battle = new Battle(List.of(jingYuan), List.of(enemy), new Random(0));
        battle.startBattle();

        for (int i = 0; i < 6 && !battle.isOver(); i++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            CanHit actor = battle.currentMove.getCanHit();
            battle.beforeMove();
            if (actor == enemy) {
                battle.performAction(enemySkill(enemy), List.of(jingYuan));
            } else {
                battle.performAction(realSkill(1204, SkillType.COMMON), List.of(enemy));
            }
            battle.afterMove();
        }

        Assertions.assertTrue(jingYuan.getCurrentEnergy() > 0,
                "a regular character should have energy credited, actual " + jingYuan.getCurrentEnergy());
    }

    /**
     * The 6 special characters have **energy constantly at 0** in a real battle (exhaustive, to avoid
     * protecting only one of them).
     */
    @Test
    public void everySpecialResourceCharacterStaysAtZeroEnergy() {
        for (int cid : SPECIAL) {
            Character c = CharacterFactory.create(cid, 80);
            Enemy enemy = EnemyFactory.create(1002011, 90, 1);
            Battle battle = new Battle(List.of(c), List.of(enemy), new Random(1));
            battle.startBattle();

            for (int i = 0; i < 6 && !battle.isOver(); i++) {
                battle.stepForward();
                if (battle.currentMove == null) {
                    break;
                }
                CanHit actor = battle.currentMove.getCanHit();
                battle.beforeMove();
                if (actor == enemy) {
                    battle.performAction(enemySkill(enemy), List.of(c));
                } else {
                    battle.performAction(realSkill(cid, SkillType.COMMON), List.of(enemy));
                }
                battle.afterMove();
            }
            Assertions.assertEquals(0, c.getCurrentEnergy(), EPS,
                    "cid=" + cid + " (" + c.getName() + ")'s energy should always be 0");
        }
    }

    // ==================================================================

    private static Skill realSkill(int cid, SkillType type) {
        int slot = switch (type) {
            case COMMON -> 1;
            case SKILL -> 2;
            case ULTRA -> 3;
            default -> throw new IllegalArgumentException("this test only uses slots 1/2/3");
        };
        return new DefaultSkill(cid, slot, 1);
    }

    /** The enemy's basic attack (the skill P5-3 attaches to enemies). */
    private static Skill enemySkill(Enemy enemy) {
        return enemy.getSkills().values().iterator().next();
    }
}
