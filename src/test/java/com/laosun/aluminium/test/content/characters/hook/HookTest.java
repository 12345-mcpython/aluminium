package com.laosun.aluminium.test.content.characters.hook;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.DotBuff;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1109 虎克: the burn his Skill lands and the energy his ultimate trace pays, the enhanced Skill his ultimate installs,
 * the Eidolons that move those readings, his talent's extra damage instance and his technique.
 *
 * <p>Merged from the former {@code HookTest}, {@code HookEnhancedSkillTest}, {@code HookClauseCoverageTest},
 * {@code HookEidolonTest}, {@code HookTalentTest} and {@code HookTechniqueTest}; every test, assertion, helper and
 * class-level note is preserved.
 */
public class HookTest {
    private static final int HOOK = 1109;
    private static final int LEVEL = 80;
    private static final double TALENT_ENERGY = 5;

    // ---------------------------------------------------------------------------------------------------------------
    // Former HookTest: the burn his Skill lands, and the trace that pays energy on the ultimate.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * Hook (虎克) (1109): his burn and the traces/eidolons that shipped with it.
     *
     * <p><b>Why a hand-made target.</b> The project's fixture monster resists controls outright (round 131), and the burn is rolled
     * too - `APPLY_DOT` runs `tryApplyDebuff` with the document's base chance - so an unresisting target is what makes the test
     * deterministic rather than lucky.
     */

    /**
     * Note: His skill's burn lands as a DOT with the document's magnitude, and the trace pays energy on the ultimate.
     */
    @Test
    public void hisBurnLandsAndHisUltimateTracePaysEnergy() {
        BurnFixture f = new BurnFixture(0);
        Assertions.assertFalse(f.enemy.getBuffManager().hasState("dot:burn"), "precondition: not burning (dot:burn) yet");

        f.battle.castImmediate(f.hook.getSkills().get(SkillType.SKILL), f.hook, List.of(f.enemy));

        Assertions.assertTrue(f.enemy.getBuffManager().hasState("dot:burn"),
                "「有100%的基础概率使其陷入dot:burn状态」 (100% base chance to put it in the burning state)");
        Assertions.assertFalse(f.enemy.getBuffManager().allBuffsOf(DotBuff.class).isEmpty(), "…as a damage-over-time state");

        double energyBefore = f.hook.getCurrentEnergy();
        f.battle.castImmediate(f.hook.getSkills().get(SkillType.ULTRA), f.hook, List.of(f.enemy));

        Assertions.assertTrue(f.hook.getCurrentEnergy() > energyBefore,
                "「施放终结技后，虎克的行动提前20%并额外恢复5点能量」 (after casting the ultimate, Hook advances 20% and restores 5 extra energy) — the trace pays on the cast");
    }

    private static final class BurnFixture {
        private final Character hook;
        private final Enemy enemy = Enemy.fromAttributes("Test Dummy", 20000, 100, 100, 90);
        private final Battle battle;

        private BurnFixture(int eidolon) {
            hook = CharacterFactory.create(HOOK, LEVEL, true, null, null, eidolon);
            battle = new Battle(List.of(hook), List.of(enemy), new Random() {
                @Override
                public double nextDouble() {
                    return 0.0;
                }
            });
            battle.startBattle();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former HookEnhancedSkillTest: the ultimate's REPLACE_SKILL swap, and the Blast row it installs.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * 1109 虎克's ultimate: 「施放终结技后，下一次施放的战技得到强化，强化后的战技能够同时对指定敌方单体及其相邻目标造成伤害」.
     *
     * <p>The enhancement is a {@code REPLACE_SKILL} swap: the SKILL slot is pointed at data row 110909, whose
     * {@code skill_effect} is <b>Blast</b> - so the adjacent targets are the data row's own shape, not a rule. The swap lives
     * `until: next_skill`, which is the sentence's "next Skill": a basic attack in between must NOT consume it.
     */

    /**
     * The normal Skill hits one enemy, the enhanced one hits the neighbour too, and the swap ends with that cast.
     */
    @Test
    public void theEnhancedSkillHitsTheNeighbourAndThenReverts() {
        EnhancedSkillFixture f = new EnhancedSkillFixture();
        f.castUltimate();
        // A basic attack between the ultimate and the Skill must not consume the swap.
        f.cast(SkillType.COMMON, f.main);
        f.castSkillAndMeasure(f.main);
        Assertions.assertTrue(f.neighbourLost(), "after the ultimate the Skill is Blast: 「及其相邻目标」");
        Assertions.assertTrue(f.mainLost(), "and the aimed enemy is still hit");

        f.reset();
        f.castSkillAndMeasure(f.main);
        Assertions.assertTrue(f.mainLost(), "the reverted Skill still hits its target");
        Assertions.assertFalse(f.neighbourLost(), "the swap ended with the first Skill - no second Blast");
    }

    /**
     * Without the ultimate the Skill is the ordinary single-target row.
     */
    @Test
    public void theOrdinarySkillHitsOneEnemy() {
        EnhancedSkillFixture f = new EnhancedSkillFixture();
        f.castSkillAndMeasure(f.main);
        Assertions.assertTrue(f.mainLost());
        Assertions.assertFalse(f.neighbourLost(), "the ordinary row is SingleAttack");
    }

    private static final class EnhancedSkillFixture {
        private final Character hook;
        private final Enemy main = BattleFixture.monster();
        private final Enemy neighbour = BattleFixture.monster();
        private final Battle battle;
        private double mainBefore;
        private double neighbourBefore;

        private EnhancedSkillFixture() {
            hook = CharacterFactory.create(HOOK, LEVEL);
            battle = new Battle(new ArrayList<>(List.of(hook)),
                    new ArrayList<>(List.of(main, neighbour)), BattleFixture.seeded());
            battle.startBattle();
        }

        private void castUltimate() {
            battle.castImmediate(hook.getSkills().get(SkillType.ULTRA), hook, List.of(main));
        }

        private void cast(SkillType slot, Enemy target) {
            battle.castImmediate(hook.getSkills().get(slot), hook, List.of(target));
        }

        private void reset() {
            mainBefore = main.getCurrentHp();
            neighbourBefore = neighbour.getCurrentHp();
        }

        private void castSkillAndMeasure(Enemy target) {
            reset();
            cast(SkillType.SKILL, target);
        }

        private boolean mainLost() {
            return main.getCurrentHp() < mainBefore;
        }

        private boolean neighbourLost() {
            return neighbour.getCurrentHp() < neighbourBefore;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former HookClauseCoverageTest: every section of the file, plus the two Eidolon readings nobody had written.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * 1109 虎克 clause coverage: every section of {@code 1109_虎克.md}, plus the readings that pin the two Eidolons nobody had written.
     *
     * <p><b>What was added on 2026-11-02</b> (a section-by-section scan found 星魂 1 and 星魂 4 had no rule at all, even though the
     * file's own gap text claimed only three approximations were left):
     * <ul>
     *   <li><b>Eidolon 1</b> — 「战技强化后造成的伤害提高 20%」. The gate is the enhanced form itself: {@code damage_skill_key == 9}
     *       (the loader's slot that {@code REPLACE_SKILL} installs). ⚠ {@code from_skill_id} does <b>not</b> work on
     *       {@code DEALING_DAMAGE}: its payload carries no cast facts — measured, the reading was identical at E0 and E1.</li>
     *   <li><b>Eidolon 4</b> — 「使指定敌方目标的相邻目标也陷入等同于战技的dot:burn状态」. Needed a new selector,
     *       {@code adjacent_enemies}: {@code random_adjacent_enemy} answers only one of the two neighbours.</li>
     * </ul>
     */

    /**
     * Eidolon 1: 「战技强化后造成的伤害提高 20%」 — measured on the enhanced Skill's main target.
     *
     * <p>E0 <b>1430.016034048001</b> → E1 <b>1640.755239065601</b>. Note the ratio is not 1.2: {@code BOOST_DAMAGE} adds into
     * the damage-boost zone rather than multiplying the settled hit (the same shape 1013 and 1412 record), so the honest
     * assertion is the two measured numbers, not a ratio.
     */
    @Test
    public void eidolon1BoostsOnlyTheEnhancedSkill() {
        Assertions.assertEquals(1430.016034048001, enhancedSkillLoss(0), 1e-6, "E0: the enhanced Skill's own row");
        Assertions.assertEquals(1640.755239065601, enhancedSkillLoss(1), 1e-6,
                "E1: 「战技强化后造成的伤害提高 20%」");

        // The other half: a PLAIN Skill (no Ultimate first) must not be boosted -- 「强化后」 says which form it is.
        Assertions.assertEquals(plainSkillLoss(0), plainSkillLoss(1), 1e-6,
                "a plain Skill is not the enhanced form, so Eidolon 1 leaves it alone");
    }

    /**
     * Eidolon 4: 「使指定敌方目标的相邻目标也陷入等同于战技的dot:burn状态」.
     *
     * <p>Measured with three enemies: after the Skill burns the middle one and a basic attack triggers the talent, both
     * neighbours are burning at E4 and neither is at E0.
     */
    @Test
    public void eidolon4BurnsTheNeighbours() {
        Assertions.assertArrayEquals(new boolean[]{false, false},
                neighboursBurning(0), "control: at E0 the neighbours are untouched");
        Assertions.assertArrayEquals(new boolean[]{true, true},
                neighboursBurning(4), "「相邻目标也陷入…dot:burn状态」 — both of them");
    }

    /**
     * Casts the Ultimate (which installs the enhanced Skill) then the Skill on the first enemy; returns the HP it lost.
     */
    private static double enhancedSkillLoss(int eidolon) {
        ClauseCoverageFixture f = new ClauseCoverageFixture(eidolon, 1);
        f.battle.castImmediate(f.hook.getSkills().get(SkillType.ULTRA), f.hook, List.of(f.enemies.getFirst()));
        f.battle.processRequests();
        return f.measureSkillHit(0);
    }

    private static double plainSkillLoss(int eidolon) {
        ClauseCoverageFixture f = new ClauseCoverageFixture(eidolon, 1);
        return f.measureSkillHit(0);
    }

    private static boolean[] neighboursBurning(int eidolon) {
        ClauseCoverageFixture f = new ClauseCoverageFixture(eidolon, 3);
        f.battle.castImmediate(f.hook.getSkills().get(SkillType.SKILL), f.hook, List.of(f.enemies.get(1)));
        f.battle.processRequests();
        f.battle.castImmediate(f.hook.getSkills().get(SkillType.COMMON), f.hook, List.of(f.enemies.get(1)));
        f.battle.processRequests();
        return new boolean[]{
                f.enemies.get(0).getBuffManager().hasState("dot:burn"),
                f.enemies.get(2).getBuffManager().hasState("dot:burn")};
    }

    private static final class ClauseCoverageFixture {
        private final Character hook;
        private final List<Enemy> enemies = new ArrayList<>();
        private final Battle battle;

        private ClauseCoverageFixture(int eidolon, int enemyCount) {
            hook = CharacterFactory.create(HOOK, 80, false, null, null, eidolon);
            for (int i = 0; i < enemyCount; i++) {
                enemies.add(BattleFixture.enemy(BattleFixture.MONSTER_ID, BattleFixture.MONSTER_LEVEL));
            }
            battle = new Battle(List.of(hook), List.copyOf(enemies), BattleFixture.seeded());
            battle.startBattle();
            battle.processRequests();
        }

        /** Casts the Skill at enemy {@code index} and returns the HP that enemy lost. */
        private double measureSkillHit(int index) {
            Enemy target = enemies.get(index);
            double before = target.getCurrentHp();
            battle.castImmediate(hook.getSkills().get(SkillType.SKILL), hook, List.of(target));
            battle.processRequests();
            return before - target.getCurrentHp();
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former HookEidolonTest: Eidolon 2 (a longer burn) and Eidolon 6 (more damage against a burning target).
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * 1109 虎克's Eidolon 2 and 6, the two clauses its file already states.
     *
     * <p>E2 「战技使敌方目标陷入的dot:burn状态持续时间增加 1 回合」 is a {@code MODIFY_RULE} on the burn rule, so the check is the
     * burn's own duration (2 → 3). E6 「对dot:burn状态下的敌方目标造成的伤害提高 20%」 is a {@code BOOST_DAMAGE} on the settling
     * instance, so the check is the damage ratio against a burning and a healthy enemy with otherwise identical stats.
     */

    /**
     * Eidolon 2: the Skill's burn lasts one turn longer.
     */
    @Test
    public void eidolonTwoLengthensTheBurn() {
        Assertions.assertEquals(2, burnTurns(0), "the base row states 2 turns");
        Assertions.assertEquals(3, burnTurns(2), "「持续时间增加 1 回合」");
    }

    /**
     * Eidolon 6: the same hit is 20% stronger against a burning target - compared across EIDOLON RANKS, not across
     * enemies. A burning enemy also draws the talent's extra instance (「对dot:burn目标追加 100% 攻击力」), so the two enemies'
     * totals differ by 1.71 for a reason that has nothing to do with E6; the rank-to-rank ratio cancels it.
     */
    @Test
    public void eidolonSixBoostsDamageAgainstBurningTargets() {
        Assertions.assertEquals(1.0, rankRatio(false), 1e-6, "a healthy target: rank 6 changes nothing");
        Assertions.assertEquals(1.2, rankRatio(true), 1e-6, "「对dot:burn状态下的敌方目标造成的伤害提高 20%」");
    }

    private static double rankRatio(boolean burning) {
        // Rank 6 against rank 5, not against rank 0: her other Eidolons also move the numbers, and this is the
        // clause under test.
        return damage(6, burning) / damage(5, burning);
    }

    private static double damage(int eidolon, boolean burning) {
        Character hook = CharacterFactory.create(HOOK, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(hook, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.99));
        battle.startBattle();
        if (burning) {
            enemy.getBuffManager().addBuff(new DotBuff(hook, DamageElement.FIRE, 10, 3, 0, "dot:burn"));
        }
        double before = enemy.getCurrentHp();
        battle.castImmediate(hook.getSkills().get(SkillType.COMMON), hook, List.of(enemy));
        return before - enemy.getCurrentHp();
    }

    private static int burnTurns(int eidolon) {
        Character hook = CharacterFactory.create(HOOK, LEVEL, true, null, null, eidolon);
        // A monster with NO Fire weakness: a Fire break would attach its own burn (3 turns) and blur the count.
        Enemy enemy = BattleFixture.enemy(100201602, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(hook, CharacterFactory.create(1001, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
        battle.startBattle();
        battle.castImmediate(hook.getSkills().get(SkillType.SKILL), hook, List.of(enemy));
        List<DotBuff> dots = enemy.getBuffManager().activeBuffs().stream()
                .filter(DotBuff.class::isInstance).map(DotBuff.class::cast)
                .filter(dot -> dot.getElement() == DamageElement.FIRE).toList();
        Assertions.assertEquals(1, dots.size(), "the Skill lands exactly one burn (no Fire break on this target)");
        return dots.getFirst().remainingTurns();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former HookTalentTest: the talent's extra instance, and the guard that stops it from re-triggering itself.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * 1109 Hook's talent: the extra damage instance, and the guard that stops it from re-triggering itself.
     *
     * <p>The clause reacts to a hit on a burning target by dealing one more Fire instance and granting 5 energy. That extra
     * instance is a damage instance too, so without a guard the rule would react to itself until the engine's trigger depth
     * limit threw. The guard is the positive condition `damage_is_attack`, which is TRUE for an ordinary attack and FALSE
     * for additional damage.
     *
     * <p>Note: <b>Why this file was rewritten (round 4 of the current goal).</b> It used to assert
     * {@code energy >= before + 5} after one basic attack -- but a basic attack grants energy on its own, so the assertion was
     * satisfied whether or not the talent fired. Measured: with {@code damage_is_attack} inverted (its implementation until
     * ) the talent did NOT fire on ordinary attacks, and this test stayed green. The reading is now the
     * <b>difference</b> between attacking a burning target and attacking a plain one: only the talent can produce it.
     *
     * <p>Note: <b>That this test finishes is itself the assertion about recursion.</b> If the guard were missing or
     * ineffective, the engine would raise "Trigger recursion exceeded" and the test would fail with that, not with a number.
     */

    /**
     * The energy one basic attack gains, with and without the burning target the talent needs.
     */
    private double energyFromOneBasicAttack(boolean burning) {
        Character hook = CharacterFactory.create(HOOK, LEVEL);
        Enemy enemy = Enemy.fromAttributes("Test Dummy", 20000, 100, 100, 90);
        Battle battle = new Battle(List.of(hook), List.of(enemy), new Random() {
            @Override
            public double nextDouble() {
                return 0.0;
            }
        });
        battle.startBattle();
        if (burning) {
            battle.castImmediate(hook.getSkills().get(SkillType.SKILL), hook, List.of(enemy));
            Assertions.assertTrue(enemy.getBuffManager().hasState("dot:burn"), "precondition: the target burns");
        }
        double before = hook.getCurrentEnergy();
        battle.castImmediate(hook.getSkills().get(SkillType.COMMON), hook, List.of(enemy));
        double gained = hook.getCurrentEnergy() - before;
        Assertions.assertTrue(hook.getMaxEnergy() > gained, "precondition: the bar is not clipped by the cap");
        return gained;
    }

    @Test
    public void theTalentPaysFiveEnergyThatTheAttackAloneDoesNot() {
        double plain = energyFromOneBasicAttack(false);
        double burning = energyFromOneBasicAttack(true);
        System.out.println("[1109] energy from one basic attack: burning=" + burning + " plain=" + plain
                + " difference=" + (burning - plain));
        Assertions.assertEquals(TALENT_ENERGY, burning - plain, 1e-6,
                "the talent's 5 energy is the DIFFERENCE -- the attack's own gain cancels out, so this reading is "
                        + "attributable to the talent and to nothing else");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Former HookTechniqueTest: 「进入战斗后对敌方随机单体造成 50% 攻击力的火属性伤害…」.
    // ---------------------------------------------------------------------------------------------------------------
    /*
     * 1109 虎克's technique: 「进入战斗后对敌方随机单体造成 50% 攻击力的火属性伤害，同时有 100% 基础概率使敌方每个单体目标陷入
     * dot:burn状态，持续 3 回合（每回合 50% 攻击力火伤）」.
     *
     * <p>Two halves, two different targets: the <b>damage</b> is one random enemy ({@code random_enemy}), the <b>burn</b> is
     * every enemy ({@code all_enemies}). The technique gate is the engine's own {@code 秘技} state, laid down before any
     * {@code BATTLE_START} rule runs.
     */

    /**
     * With the technique: one random enemy takes the hit, every enemy burns; without it, neither happens.
     */
    @Test
    public void theTechniqueHitsOneAndBurnsAll() {
        TechniqueFixture f = new TechniqueFixture(true);
        List<Enemy> hit = f.enemies.stream().filter(e -> e.getCurrentHp() < e.getMaxHp()).toList();
        Assertions.assertEquals(1, hit.size(), "「对敌方随机单体造成…火属性伤害」 - exactly one enemy is hit");
        for (Enemy enemy : f.enemies) {
            Assertions.assertEquals(1, enemy.getBuffManager().countBuffs(DotBuff.class),
                    enemy.getName() + ": 「使敌方每个单体目标陷入dot:burn状态」");
        }

        TechniqueFixture bare = new TechniqueFixture(false);
        for (Enemy enemy : bare.enemies) {
            Assertions.assertEquals(enemy.getMaxHp(), enemy.getCurrentHp(), 1e-9, "no technique, no damage");
            Assertions.assertEquals(0, enemy.getBuffManager().countBuffs(DotBuff.class), "no technique, no burn");
        }
    }

    private static final class TechniqueFixture {
        private final Character hook;
        private final List<Enemy> enemies = new ArrayList<>();
        private final Battle battle;

        private TechniqueFixture(boolean technique) {
            hook = CharacterFactory.create(HOOK, LEVEL);
            enemies.add(BattleFixture.monster());
            enemies.add(BattleFixture.monster());
            battle = new Battle(new ArrayList<>(List.of(hook)), new ArrayList<>(enemies), BattleFixture.seeded());
            if (technique) {
                battle.markTechniqueUsed(hook);
            }
            // The stated 100% base chance is rolled against effect resistance; a zero-resistance target makes the
            // fixture measure the clause, not the roll.
            for (Enemy enemy : enemies) {
                DoubleValue resistance = enemy.getAttribute(AttributeType.EFFECT_RESISTANCE);
                if (resistance != null && resistance.get() != 0) {
                    resistance.addModifier(DoubleValue.Modifier.pure(-resistance.get()));
                }
            }
            battle.startBattle();
        }
    }
}
