package com.laosun.aluminium.test.content.characters.danheng;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.data.SkillData;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Dan Heng (丹恒) 1002: how his damage is dealt in <b>segments</b>, the 行迹 that halves his aggro weight below half
 * HP, and the Remembrance Trailblazer pair's memosprite.
 *
 * <h2>What the document states</h2>
 * 「官方最大命中次数」 is <b>2</b> for the basic attack and <b>4</b> for the Skill, with the split ratios
 * {@code 0.45 + 0.55} and {@code 0.30 + 0.15 + 0.15 + 0.40} (both summing to 1). The <b>total is therefore
 * unchanged</b> - what changes is the number of damage <b>instances</b>, and that is observable in three places:
 * <ul>
 *   <li>each instance rolls its <b>own crit</b> - the game's 「战技造成的伤害<b>触发暴击时</b>…减速」 is rolled per
 *       instance, so four chances instead of one;</li>
 *   <li>each instance settles its own {@code DEALING_DAMAGE} / {@code CRIT_DEALT} / {@code TAKING_HIT};</li>
 *   <li>the toughness is one number for the cast, so it is spread over the instances (the cast's total is unchanged).</li>
 * </ul>
 *
 * <p>Before this, the engine settled <b>one</b> instance of the whole multiplier - the total damage was right and the
 * trigger count was not, which is the kind of difference a damage-only assertion cannot see.
 *
 * <h2>潜龙: the low-HP aggro trace</h2>
 * 行迹 <b>潜龙</b>: 「若当前生命值百分比小于等于 50%，则被敌方目标攻击的概率降低。」 It is the only rule of his kit with
 * no case of its own until now - and the one whose number is easiest to get wrong silently: a soft aggro weight of
 * <b>-50%</b> means the weight becomes {@code base x 0.5}, not "he is never targeted" (a weight of zero would take a
 * living unit out of the draw, which {@code Battle.aggroOf} deliberately refuses).
 *
 * <p>Provenance of the two numbers: {@code ExcelOutput/AvatarSkillTreeConfig.json}, {@code PointID 1002101},
 * {@code ParamList = [{0.5}, {0.5}]} - the first is the HP threshold the prose states, the second the ratio (they are
 * equal, so their order cannot matter), and the ability {@code Avatar_DanHeng_00_SkillTree_AggroDown} writes
 * {@code AggroAddedRatio} as <b>0 − parameter</b>, i.e. −0.5.
 *
 * <p>Both halves: the rule fires on {@code HP_LOST} at ≤ 50%, and a second rule takes the state off on {@code HEALED}
 * above 50%. Asserting only the first half would leave "he stays untargetable forever" invisible.
 *
 * <p>⚠ The damage is dealt through {@code Battle.applyDamage}, not {@code CanHit.takeDamage}: the latter is the raw HP
 * subtraction and fires no events, so a rule listening on {@code HP_LOST} would never see it (measured: the first
 * version of this case read 0.0 and looked like a missing rule).
 *
 * <h2>The Remembrance Trailblazer pair</h2>
 * The first summon shipped since 1402/1413, because the document states its SPEED. Round 163 measured that a
 * memosprite file MUST state SPEED ("the action bar cannot schedule a unit with 0 speed"), which is why 1409's
 * memosprite is still unwritten. Here both numbers are in the text - 130 SPD and 80% of the Trailblazer's Max HP plus
 * 640 - so the panel can be stated and asserted.
 *
 * <p>Merged from the former {@code DanHengSegmentsTest}, {@code DanHengAggroDownTest} and
 * {@code RemembranceTrailblazerTest}; every assertion and Javadoc of measurement is preserved, the single-clause cases
 * consolidated into scenario tests.
 */
public class DanHengCombatMechanicsTest {
    private static final int DANHENG = 1002;
    private static final int ALLY = 1201;
    private static final int LEVEL = 80;

    // Two tolerances lived under the same name in the two source classes (1e-9 in the aggro case, 1e-6 in the
    // segment arithmetic); both values are preserved, the second under a sharper name.
    private static final double EPS = 1e-9;
    private static final double SEGMENTS_EPS = 1e-6;

    // ==================================================================
    // From DanHengSegmentsTest: the cast's instances and their own crits
    // ==================================================================

    // ==================================================================
    // 1. How many instances, and do they still add up
    // ==================================================================

    /**
     * The cast's segments, in one scenario: the basic attack settles two instances and the Skill four; the ultimate one
     * (its table states a single segment) - and the split does not change the total, because the instances' base layer
     * sums to the whole multiplier.
     *
     * <p>Note: A ratio <b>of the row's own multiplier</b> is the document's own reading (分裂比合计 1.0), so this case
     * pins "the sum is the number the single instance used to carry" rather than any absolute damage - zones, crit and
     * resistance all sit downstream of it. Both halves read the same observed object - the settled instances of a cast -
     * off the same fixture.
     */
    @Test
    public void theCastSettlesTheDocumentedSegmentsAndTheySumToTheWholeMultiplier() {
        Cast cast = castAt(SkillType.SKILL, 10);
        var row = SkillData.init(DANHENG, 2).getSkills().get(9);
        double expectedBase = cast.attacker.getAttribute(AttributeType.ATTACK).get() * row.getFirst();

        double sum = cast.segments.stream().mapToDouble(Damage::getSkillBaseValue).sum();
        Assertions.assertAll("「官方最大命中次数」",
                () -> Assertions.assertEquals(2, instancesOf(SkillType.COMMON, 10),
                        "「官方最大命中次数 2」 for the basic attack (0.45 + 0.55)"),
                () -> Assertions.assertEquals(4, instancesOf(SkillType.SKILL, 10),
                        "「官方最大命中次数 4」 for the Skill (0.30 + 0.15 + 0.15 + 0.40)"),
                () -> Assertions.assertEquals(1, instancesOf(SkillType.ULTRA, 10),
                        "the ultimate's table states one segment"),
                () -> Assertions.assertEquals(expectedBase, sum, SEGMENTS_EPS,
                        "0.30 + 0.15 + 0.15 + 0.40 of the row's 2.6 is still ATK x 2.6"),
                () -> {
                    for (int i = 0; i < cast.segments.size(); i++) {
                        double ratio = new double[]{0.3, 0.15, 0.15, 0.4}[i];
                        Assertions.assertEquals(expectedBase * ratio, cast.segments.get(i).getSkillBaseValue(),
                                SEGMENTS_EPS, "instance " + (i + 1) + " carries its own fraction of the row");
                    }
                });
    }

    // ==================================================================
    // 2. Each instance rolls its own crit - the half the totals cannot see
    // ==================================================================

    /**
     * Every instance rolls crit on its own, so the Skill offers the 「触发暴击时…减速」 clause <b>four</b> chances instead
     * of one.
     *
     * <p>Note: The rolls are forced rather than sampled ({@code alwaysRoll(0.0)} = every roll crits), so "four chances"
     * is measured, not inferred from a probability. The slow then lands as <b>one</b> modifier refreshed four times, not
     * four stacked slows - which is the game's own behaviour for re-applying the same debuff.
     */
    @Test
    public void everySegmentRollsItsOwnCrit() {
        Cast allCrit = castAt(SkillType.SKILL, 10, BattleFixture.alwaysRoll(0.0));
        Assertions.assertEquals(4, allCrit.crits(), 4 + " instances, every roll a crit");

        Cast noCrit = castAt(SkillType.SKILL, 10, BattleFixture.alwaysRoll(1.0));
        Assertions.assertEquals(0, noCrit.crits(), "no roll crits, so the clause gets no chance at all");
        Assertions.assertEquals(4, noCrit.segments.size(), "…and the instances are still four");

        // The consequence in the character's own kit: one slow modifier, refreshed, not four of them.
        Assertions.assertEquals(-0.12, allCrit.attacker == null ? 0 : slowOn(allCrit.enemy), 1e-9,
                "four crits land ONE speed reduction of -12% (a re-application refreshes, it does not stack)");
    }

    // ==================================================================
    // From DanHengAggroDownTest: 潜龙 halves his aggro weight below half HP
    // ==================================================================

    /**
     * Dropping below half HP halves his aggro weight; healing back above it restores it - and the control that makes
     * the threshold meaningful: <b>above</b> half HP nothing happens, even on an HP loss.
     *
     * <p>⚠ One scenario: every observation is the same object (AggroAddedRatio / the soft aggro weight) under the same
     * clause's gate (「小于等于 50%」), so the two halves are assertions of one experiment rather than two cases.
     */
    @Test
    public void theLowHpTraceHalvesHisAggroWeightOnlyAtOrBelowHalfHp() {
        Character danHeng = CharacterFactory.create(DANHENG, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(danHeng, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.seeded());
        battle.startBattle();

        double before = battle.aggroOf(danHeng);
        double untouched = danHeng.getAttribute(AttributeType.AGGRO_ADDED_RATIO).get();

        drainTo(battle, enemy, danHeng, 0.45);
        boolean atOrBelowHalf = hpRatio(danHeng) <= 0.5;
        double atLowHp = danHeng.getAttribute(AttributeType.AGGRO_ADDED_RATIO).get();
        double lowHpWeight = battle.aggroOf(danHeng);

        // Back above half HP: the cleanup rule takes the state off, so the weight returns.
        battle.heal(ally, danHeng, danHeng.getAttribute(AttributeType.HEALTH).get());
        boolean aboveHalfAfterHeal = hpRatio(danHeng) > 0.5;
        double afterHeal = danHeng.getAttribute(AttributeType.AGGRO_ADDED_RATIO).get();
        double healedWeight = battle.aggroOf(danHeng);

        Character control = CharacterFactory.create(DANHENG, LEVEL);
        Enemy controlEnemy = BattleFixture.monster();
        Battle controlBattle = new Battle(new ArrayList<>(List.of(control)),
                new ArrayList<>(List.of(controlEnemy)), BattleFixture.seeded());
        controlBattle.startBattle();

        controlBattle.applyDamage(control, new Damage(controlEnemy, control, DamageElement.PHYSICAL,
                DamageType.NORMAL, control.getAttribute(AttributeType.HEALTH).get() * 0.2,
                SkillCategory.UNSPECIFIED));
        boolean stillAboveHalf = hpRatio(control) > 0.5;
        double controlRatio = control.getAttribute(AttributeType.AGGRO_ADDED_RATIO).get();

        Assertions.assertAll("「若当前生命值百分比小于等于 50%，则被敌方目标攻击的概率降低」",
                () -> Assertions.assertEquals(0.0, untouched, EPS,
                        "precondition: nothing has moved his weight yet"),
                () -> Assertions.assertTrue(atOrBelowHalf, "precondition: he is at or below half HP"),
                () -> Assertions.assertEquals(-0.5, atLowHp, EPS,
                        "「被敌方目标攻击的概率降低」 ⇒ AggroAddedRatio = -0.5 (0 − parameter)"),
                () -> Assertions.assertEquals(before * 0.5, lowHpWeight, EPS,
                        "a SOFT weight: base x (1 − 0.5), not zero and not \"untargetable\""),
                () -> Assertions.assertTrue(aboveHalfAfterHeal, "precondition: the heal took him above half HP"),
                () -> Assertions.assertEquals(0.0, afterHeal, EPS, "above half HP the trace no longer applies"),
                () -> Assertions.assertEquals(before, healedWeight, EPS, "and the weight is back to what it was"),
                () -> Assertions.assertFalse(danHeng.getBuffManager().hasState("char:1002/hidden_dragon"),
                        "the state itself is gone (the cleanup rule names it), so a later read cannot see a stale mark"),
                () -> Assertions.assertTrue(stillAboveHalf, "precondition: still above half HP"),
                () -> Assertions.assertEquals(0.0, controlRatio, EPS,
                        "「小于等于 50%」 is a gate, not a welcome: the rule must not fire here"));
    }

    // ==================================================================
    // From RemembranceTrailblazerTest: the memosprite's panel, summon and Ultimate
    // ==================================================================

    /**
     * Note: The memorisprite arrives with the document's own panel, for both ids - and casting again does not create a
     * second memosprite: the first is still the one on the field (「若迷迷已在场」). One scenario, because both halves
     * observe the same object (the memosprite summoned by the Trailblazer's Skill) off the same fixture.
     */
    @Test
    public void theSummonArrivesWithTheDocumentedPanelAndIsNotSummonedTwice() {
        for (int cid : new int[]{8007, 8008}) {
            Character tb = CharacterFactory.create(cid, LEVEL);
            Enemy enemy = BattleFixture.monster();
            Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
            battle.startBattle();

            battle.castImmediate(tb.getSkills().get(SkillType.SKILL), tb, List.of(enemy));

            var mem = battle.memospriteOf(tb);
            double expected = tb.getMaxHp() * 0.8 + 640;
            Assertions.assertAll("cid " + cid + ": 「召唤忆灵迷迷」",
                    () -> Assertions.assertNotNull(mem, "cid " + cid + ": \"summons the memosprite Mem\" (「召唤忆灵迷迷」)"),
                    () -> Assertions.assertEquals(130, mem.getAttribute(AttributeType.SPEED).get(), 1e-9,
                            "cid " + cid + ": \"the memosprite Mem starts with 130 SPD\" (「忆灵迷迷初始拥有130点速度」)"),
                    () -> Assertions.assertEquals(expected, mem.getMaxHp(), expected * 0.02,
                            "cid " + cid + ": 「等同于开拓者80%生命上限+640的生命上限」 (Max HP equal to 80% of the Trailblazer's Max HP + 640): expected " + expected + ", got " + mem.getMaxHp()));
        }

        Character tb = CharacterFactory.create(8007, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(tb), List.of(enemy), fixed());
        battle.startBattle();

        battle.castImmediate(tb.getSkills().get(SkillType.SKILL), tb, List.of(enemy));
        var first = battle.memospriteOf(tb);
        battle.castImmediate(tb.getSkills().get(SkillType.SKILL), tb, List.of(enemy));
        var second = battle.memospriteOf(tb);

        Assertions.assertAll("「若迷迷已在场」",
                () -> Assertions.assertSame(first, second, "「若迷迷已在场」 (if Mem is already on the field) -- the same memosprite, not a second one"));
    }


    /**
     * 终结技:「召唤忆灵迷迷…随后**使迷迷**对敌方全体造成等同于迷迷 240% 攻击力的冰属性伤害」, and the toughness the same
     * instance carries: 终结技的韧性 60（数据 `stance_list.all`）：被交接的施放不削韧性，所以它必须由规则那一发带着。
     *
     * <p><b>Two defects lived here until 2026-11-09.</b> The Ultimate's row is {@code Ultra/AoEAttack}, so without a hand-over it
     * swung a second instance on top of the rule's - and scaled off the TRAILBLAZER's attack instead of Mem's (measured:
     * full = 1464.2 against the rule's 509.7). Worse, the rule's {@code scale: "summon_attr:ATTACK"} was <b>refused by DAMAGE</b>,
     * so the Ultimate <b>threw on every cast</b>. Both are fixed together; this criterion pins that the row no longer swings by
     * comparing a real cast with the same event fired by hand (the rule alone) - equality is the whole assertion.
     *
     * <p>⚠ One scenario: every observation is the same {@code Scene(cid)} fight's response to Mem's Ultimate - the HP it
     * took away and the toughness it cut.
     */
    @Test
    public void theUltimateSettlesOneInstanceOfMemsOwnDamageAndTakesSixtyToughness() {
        for (int cid : new int[]{8007, 8008}) {
            double cast = ultimateDamage(cid, true);
            double ruleOnly = ultimateDamage(cid, false);

            Scene scene = new Scene(cid);
            // Toughness only comes off an enemy that is WEAK to the element (the gate `reduceToughness` itself keeps) - Mem's is Ice.
            scene.enemy.addWeakness(com.laosun.aluminium.enums.DamageElement.ICE);
            double before = scene.enemy.getStance();
            scene.battle.castImmediate(scene.unit.getSkills().get(SkillType.ULTRA), scene.unit, List.of(scene.enemy));
            scene.battle.processRequests();
            double stance = scene.enemy.getStance();

            Assertions.assertAll("cid " + cid + ": 「使迷迷对敌方全体造成…」",
                    () -> Assertions.assertTrue(ruleOnly > 0, "cid " + cid + ": the rule's instance settled (" + ruleOnly + ")"),
                    () -> Assertions.assertEquals(ruleOnly, cast, 1e-6,
                            "cid " + cid + ": 「使迷迷对敌方全体造成…」 settles ONE instance - the row's own swing is handed away ("
                                    + cast + " vs " + ruleOnly + ")"),
                    () -> Assertions.assertEquals(before - 60, stance, 1e-6,
                            "cid " + cid + ": 「破韧值 全体 60」 rides on the rule's instance"));
        }
    }

    // ==================================================================
    // Fixtures and helpers
    // ==================================================================

    /**
     * The instances a cast settled, plus the battle they settled into.
     */
    private record Cast(Character attacker, Enemy enemy, List<Damage> segments, Random rng) {

        private int crits() {
            return (int) segments.stream().filter(Damage::isCrit).count();
        }
    }

    private static int instancesOf(SkillType slot, int level) {
        return castAt(slot, level).segments.size();
    }

    private static Cast castAt(SkillType slot, int level) {
        return castAt(slot, level, BattleFixture.alwaysRoll(1.0));
    }

    /**
     * Casts the slot at the given level against one big enemy and collects the settled instances.
     */
    private static Cast castAt(SkillType slot, int level, Random rng) {
        Character danHeng = CharacterFactory.create(DANHENG, LEVEL);
        Enemy enemy = BattleFixture.monster();
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(5_000_000));
        enemy.heal(5_000_000);
        Battle battle = new Battle(List.of(danHeng), List.of(enemy), rng);

        List<Damage> settled = new ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));

        SkillExecutor.execute(battle, new com.laosun.aluminium.skill.DefaultSkill(DANHENG, slotNumber(slot), level),
                danHeng, List.of(enemy));
        return new Cast(danHeng, enemy, settled, rng);
    }

    private static int slotNumber(SkillType slot) {
        return switch (slot) {
            case COMMON -> 1;
            case SKILL -> 2;
            case ULTRA -> 3;
            default -> throw new IllegalArgumentException("no data slot for " + slot);
        };
    }

    /**
     * The speed reduction currently on the enemy, as a single number (0 when nothing is applied).
     */
    private static double slowOn(Enemy enemy) {
        DoubleValue speed = enemy.getAttribute(AttributeType.SPEED);
        double base = speed.baseValue();
        return base == 0 ? 0 : (speed.get() / base) - 1;
    }

    /**
     * Deals damage in small steps until he is at or below the given HP ratio - the engine's own path, so {@code HP_LOST}
     * really fires. Small steps because a settled hit goes through defence and resistance.
     */
    private static void drainTo(Battle battle, Enemy attacker, Character victim, double targetRatio) {
        double step = victim.getAttribute(AttributeType.HEALTH).get() * 0.1;
        for (int i = 0; i < 20 && hpRatio(victim) > targetRatio; i++) {
            battle.applyDamage(victim, new Damage(attacker, victim, DamageElement.PHYSICAL, DamageType.NORMAL,
                    step, SkillCategory.UNSPECIFIED));
            Assertions.assertFalse(victim.isDeath(), "precondition: the drain must not kill him");
        }
    }

    private static double hpRatio(Character character) {
        return character.getCurrentHp() / character.getAttribute(AttributeType.HEALTH).get();
    }

    /** Enemy HP lost to one Ultimate, either through a real cast or through the rule's own event. */
    private static double ultimateDamage(int cid, boolean throughTheCast) {
        Scene scene = new Scene(cid);
        double before = scene.enemy.getCurrentHp();
        if (throughTheCast) {
            scene.battle.castImmediate(scene.unit.getSkills().get(SkillType.ULTRA), scene.unit, List.of(scene.enemy));
        } else {
            // The rule's own event: no cast token, so no row can swing - this IS the rule's single instance.
            scene.battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                    com.laosun.aluminium.enums.TriggerEvent.ULT_CAST, scene.unit, scene.enemy, 1, 0));
        }
        scene.battle.processRequests();
        return before - scene.enemy.getCurrentHp();
    }

    /** A fight with a toughness bar big enough that a 60-point hit cannot break it (breaking adds a BREAK instance). */
    private static final class Scene {
        private final Character unit;
        private final Enemy enemy;
        private final Battle battle;

        private Scene(int cid) {
            unit = CharacterFactory.create(cid, LEVEL);
            enemy = BattleFixture.monster();
            enemy.setMaxStance(100_000);
            enemy.setStance(100_000);
            battle = new Battle(List.of(unit), List.of(enemy), fixed());
            battle.startBattle();
        }
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }
}
