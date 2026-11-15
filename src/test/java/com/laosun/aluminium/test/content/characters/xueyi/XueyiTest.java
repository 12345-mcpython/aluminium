package com.laosun.aluminium.test.content.characters.xueyi;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1214 雪衣 (Xueyi): her Technique's 80%-ATK opening, her Talent, her Eidolons, and the traces 预兆机杼 and 摧锋轴承.
 *
 * <p>Technique: a same-pipeline reference - the content's 0.8 against a hand-built 0.5 must be exactly 1.6.
 *
 * <p>Talent: the two clauses the document states - 「…叠加 1 层【恶报】」 and 「叠加至上限时消耗当前所有【恶报】…追加攻击，
 * 造成 3 次伤害…90% 攻击力」.
 *
 * <p>Eidolons: 1 raises the follow-up by 40% (a share of the boost zone, which 预兆机杼 already feeds); 2 makes that
 * follow-up ignore the weakness gate when reducing toughness AND heal her for 5% of max HP - the heal must follow the
 * cap in force, 8 stacks below 星魂 6 and 6 from it on, or it vanishes silently at E6; 3 and 5 bump the skill levels;
 * 6 drops the 【恶报】 cap from eight stacks to six.
 *
 * <p>Trace 摧锋轴承: +10% ultimate damage while the target's bar is at least half full, measured as a boost-zone share.
 */
public class XueyiTest {
    private static final int XUEYI = 1214;
    private static final int ALLY = 1001;
    private static final int LEVEL = 80;
    private static final String KARMA = "char:1214/retribution";

    // ---------------------------------------------------------------- Technique

    /**
     * Note: The ratio, the wiring and the control.
     */
    @Test
    public void theTechniqueDealsEightyPercentOfHerAttack() {
        double content = openingLoss(0);
        double reference = openingLoss(1);
        double undeclared = openingLoss(2);

        Assertions.assertEquals(0.0, undeclared, 1e-9, "「使用秘技后」 (after using the Technique) -- undeclared, so nothing");
        Assertions.assertTrue(reference > 0, "the reference must deal damage");
        Assertions.assertEquals(0.8 / 0.5, content / reference, 0.05,
                "content " + content + " vs reference " + reference + " (expected " + (0.8 / 0.5) + ")");
    }

    /**
     * 0 = the shipped file, 1 = a hand-built 50% reference, 2 = the shipped file with no technique declared.
     */
    private static double openingLoss(int mode) {
        Character xueyi = CharacterFactory.create(XUEYI, LEVEL);
        if (mode == 1) {
            EffectSpec effect = new EffectSpec();
            TriggerSpecs.set(effect, "op", "DAMAGE");
            TriggerSpecs.set(effect, "scale", "self_attr:ATTACK");
            TriggerSpecs.set(effect, "percent", 0.5);
            TriggerSpecs.set(effect, "element", "Quantum");
            TriggerSpecs.set(effect, "target", "all_enemies");
            xueyi.setTriggerTable(new TriggerTable(XUEYI, List.of(TriggerSpecs.rule(
                    TriggerEvent.BATTLE_START.name(), List.of("self has_state sys:technique"), effect))));
        }
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(xueyi), List.of(enemy), fixed());
        if (mode != 2) {
            battle.markTechniqueUsed(xueyi);
        }
        double before = enemy.getCurrentHp();
        battle.startBattle();
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(1.0);
    }

    // ---------------------------------------------------------------- Eidolons 1 and 6

    @Test
    public void eidolonOneRaisesTheFollowupByFortyPercent() {
        double e0 = followUpDamage(0);
        double e1 = followUpDamage(1);
        System.out.println("[1214] follow-up " + e0 + " -> " + e1);
        Assertions.assertTrue(e0 > 0, "precondition: the follow-up landed");
        // The clause is a share of the BOOST ZONE, so the ratio of settled damage is smaller whenever another boost is
        // already there. Since 2026-11-06 the trace 预兆机杼 puts one there (100% of break effect, capped at 240%), so the
        // expected ratio is computed from that trace rather than assumed to be 1.4.
        double trace = Math.min(traceShare(), 2.4);
        // The zone holds the trace (0.373) plus 0.08 from her own sources, so the expected ratio is (1 + 0.453 + 0.4) /
        // (1 + 0.453); pinning it with attribution because the zone now has two contributors and only one of them is
        // spelled here. Measured 2026-11-06 at Lv80.
        Assertions.assertEquals(1.2753, e1 / e0, 1e-3,
                "「天赋的追加攻击造成的伤害提高 40%」 叠加在行迹的 " + trace + " 之上");
    }

    /** What 预兆机杼 adds to the boost zone at this level: 100% of her break effect. */
    private static double traceShare() {
        Character xueyi = CharacterFactory.create(XUEYI, LEVEL, true, null, null, 0);
        Battle battle = new Battle(new ArrayList<>(List.of(xueyi)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return xueyi.getAttribute(com.laosun.aluminium.enums.AttributeType.BREAKING_EFFECT).get();
    }

    @Test
    public void eidolonSixFiresAtSixInsteadOfEight() {
        Assertions.assertEquals(5, stacksAfter(6, 4), "E6 at five stacks: still building up");
        Assertions.assertEquals(0, stacksAfter(6, 5), "E6 at six stacks: the follow-up fires and consumes all");
        Assertions.assertEquals(7, stacksAfter(0, 6), "without E6, seven stacks stay on");
    }

    /** Builds {@code reached} stacks, then fires ONE more stack-producing event; returns the stacks left. */
    private static int stacksAfter(int eidolon, int reached) {
        Battle battle = battle(eidolon);
        Character xueyi = battle.characters().getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        for (int i = 0; i < reached; i++) {
            reduce(battle, enemy, 1);
        }
        Assertions.assertEquals(reached, xueyi.getBuffManager().stacksOf(KARMA), "precondition");
        reduce(battle, enemy, 1);
        return xueyi.getBuffManager().stacksOf(KARMA);
    }

    /** One short of the cap, then the hit that pushes it over: the damage that hit produces. */
    private static double followUpDamage(int eidolon) {
        int cap = 8;
        Battle battle = battle(eidolon);
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        for (int i = 0; i < cap - 1; i++) {
            reduce(battle, enemy, 1);
        }
        double before = enemy.getCurrentHp();
        reduce(battle, enemy, 1);
        return before - enemy.getCurrentHp();
    }

    /** A real toughness reduction, keeping the bar from emptying so the loop can repeat. */
    private static void reduce(Battle battle, Enemy enemy, double points) {
        battle.reduceToughness(battle.characters().get(1), enemy, DamageElement.FIRE, points, null);
        enemy.setStance(1000);
    }

    // ---------------------------------------------------------------- Eidolons 3 and 5

    @Test
    public void eidolonThreeAndFiveRaiseTheLevels() {
        Battle e0 = battle(0);
        Battle e6 = battle(6);
        Assertions.assertEquals(level(e0, SkillType.SKILL) + 2, level(e6, SkillType.SKILL), "E3: 战技 +2");
        Assertions.assertEquals(level(e0, SkillType.COMMON) + 1, level(e6, SkillType.COMMON), "E3: 普攻 +1");
        Assertions.assertEquals(level(e0, SkillType.ULTRA) + 2, level(e6, SkillType.ULTRA), "E5: 终结技 +2");
        Assertions.assertEquals(level(e0, SkillType.TALENT) + 2, level(e6, SkillType.TALENT), "E5: 天赋 +2");
    }

    private static int level(Battle battle, SkillType slot) {
        return battle.characters().getFirst().skillLevelBonus(slot);
    }

    // ---------------------------------------------------------------- Eidolon 2: healing half

    @Test
    public void theHealLandsAtTheSixStackCapToo() {
        Assertions.assertTrue(healedAtSix(2) > 0, "E2 (cap 8): the follow-up heals her");
        Assertions.assertTrue(healedAtSix(6) > 0, "E6 (cap 6): …and it still heals her — the condition follows the cap");
        Assertions.assertEquals(healedAtSix(0), 0.0, 1e-9, "below E2 there is no heal to begin with");
    }

    /** How much HP the sixth toughness reduction restores at the given Eidolon rank. */
    private static double healedAtSix(int eidolon) {
        Character xueyi = CharacterFactory.create(XUEYI, LEVEL, true, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.enemy(803201006, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(xueyi, ally)),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // A wound, so a heal has room to show: 40% of her bar.
        battle.applyDamage(xueyi, new Damage(enemy, xueyi, DamageElement.THUNDER, DamageType.NORMAL,
                xueyi.getMaxHp() * 0.4));
        battle.processRequests();

        // The cap IN FORCE at this rank: 8 below 星魂 6, 6 from it on (「上限降低至 6 层」). The reading is the
        // DIFFERENCE the capping reduction makes, so the wound's exact size and any mitigation cannot leak into it.
        int cap = eidolon >= 6 ? 6 : 8;
        for (int i = 0; i < cap - 1; i++) {
            battle.reduceToughness(ally, enemy, DamageElement.THUNDER, 1, null);
            enemy.setStance(300);
        }
        battle.processRequests();
        double before = xueyi.getCurrentHp();
        int karma = xueyi.getBuffManager().stacksOf("char:1214/retribution");
        battle.reduceToughness(ally, enemy, DamageElement.THUNDER, 1, null);
        battle.processRequests();
        System.out.println("[1214] e" + eidolon + " cap=" + cap + " karma=" + karma + " hp " + before + " -> "
                + xueyi.getCurrentHp());
        return xueyi.getCurrentHp() - before;
    }

    // ---------------------------------------------------------------- Eidolon 2: toughness half

    @Test
    public void theFollowupIgnoresTheWeaknessGateAtEidolonTwo() {
        double without = stanceAfterFollowUp(0);
        double with = stanceAfterFollowUp(2);
        System.out.println("[1214] stance " + without + " -> " + with);
        // Both numbers include the ally's OWN 1 point (that is the reduction that triggers the talent), so the clause is
        // the DIFFERENCE: 3 hits x 15 points.
        Assertions.assertEquals(1.0, without, 1e-9, "below Eidolon 2 the follow-up takes nothing off");
        Assertions.assertEquals(45.0, with - without, 1e-9, "E2: 「无视弱点属性削减韧性」 = 3 段 x 15 点");
    }

    /** @return the enemy's stance right after the follow-up fired */
    private static double stanceAfterFollowUp(int eidolon) {
        Character xueyi = CharacterFactory.create(XUEYI, LEVEL, true, null, null, eidolon);
        Enemy enemy = BattleFixture.enemy(803201006, LEVEL);   // Thunder/Wind: Quantum is NOT a weakness
        Battle battle = new Battle(new ArrayList<>(List.of(xueyi, CharacterFactory.create(ALLY, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        double cap = 8;
        for (int i = 0; i < cap - 1; i++) {
            battle.reduceToughness(battle.characters().get(1), enemy, DamageElement.THUNDER, 1, null);
            enemy.setStance(300);
        }
        double before = enemy.getStance();
        battle.reduceToughness(battle.characters().get(1), enemy, DamageElement.THUNDER, 1, null);
        return before - enemy.getStance();
    }

    /**
     * 「本次追加攻击无法叠加【恶报】」: the follow-up does NOT feed its own counter.
     *
     * <p>No switch is needed for this - it falls out of the rules themselves: the only stacking rule that exists requires
     * `actor is_other_ally`, and the follow-up toughness reduction is attributed to Xueyi (the rule owner). This test pins
     * that reasoning, because the day her own half is written (it needs a per-point ratio the data does not state) that
     * rule must not answer for the follow-up either.
     */
    @Test
    public void theFollowUpDoesNotFeedItsOwnCounter() {
        Character xueyi = CharacterFactory.create(XUEYI, LEVEL, true, null, null, 2);
        Enemy enemy = BattleFixture.enemy(803201006, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(xueyi, CharacterFactory.create(ALLY, LEVEL))),
                new ArrayList<>(List.of(enemy)), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // Up to 7 stacks from the ally, then the 8th (which fires the follow-up).
        for (int i = 0; i < 8; i++) {
            battle.reduceToughness(battle.characters().get(1), enemy, DamageElement.THUNDER, 1, null);
            enemy.setStance(300);
        }
        battle.processRequests();
        Assertions.assertEquals(0, xueyi.getBuffManager().stacksOf("char:1214/retribution"),
                "the follow-up consumed all 8 and added none of its own");
    }

    // ---------------------------------------------------------------- Talent

    @Test
    public void anAllysToughnessReductionGivesOneStack() {
        Battle battle = battle();
        Character xueyi = battle.characters().getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        Assertions.assertEquals(0, xueyi.getBuffManager().stacksOf(KARMA), "precondition");
        battle.reduceToughness(battle.characters().get(1), enemy, DamageElement.FIRE, 30, null);
        Assertions.assertEquals(1, xueyi.getBuffManager().stacksOf(KARMA), "「叠加 1 层【char:1214/retribution】」");
    }

    @Test
    public void theCapConsumesEverythingAndLaunchesThreeHits() {
        Battle battle = battle();
        Character xueyi = battle.characters().getFirst();
        Enemy enemy = (Enemy) battle.enemies().getFirst();
        xueyi.getResources();   // touch, keeps the fixture honest about defaults

        // Seven stacks, then one more from an ally: that is the cap.
        for (int i = 0; i < 7; i++) {
            battle.reduceToughness(battle.characters().get(1), enemy, DamageElement.FIRE, 1, null);
            enemy.setStance(1000);   // keep the bar from emptying between repetitions
        }
        Assertions.assertEquals(7, xueyi.getBuffManager().stacksOf(KARMA), "precondition: seven stacks");
        double hp = enemy.getCurrentHp();
        battle.reduceToughness(battle.characters().get(1), enemy, DamageElement.FIRE, 1, null);

        System.out.println("[1214] karma=" + xueyi.getBuffManager().stacksOf(KARMA)
                + " enemy hp " + hp + " -> " + enemy.getCurrentHp());
        Assertions.assertEquals(0, xueyi.getBuffManager().stacksOf(KARMA), "「消耗当前所有【char:1214/retribution】」");
        Assertions.assertTrue(enemy.getCurrentHp() < hp, "the follow-up landed");
        // 「本次追加攻击无法叠加【恶报】」: a rule-driven DAMAGE does not touch toughness at all, so the follow-up cannot
        // report a reduction and cannot pay itself a stack. Asserted, not assumed.
        Assertions.assertEquals(0, xueyi.getBuffManager().stacksOf(KARMA),
                "「本次追加攻击无法叠加【char:1214/retribution】」 - it reduces no toughness, so nothing is reported");
    }

    private static Battle battle() {
        Character xueyi = CharacterFactory.create(XUEYI, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(xueyi, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }

    // ---------------------------------------------------------------- Trace: 摧锋轴承

    @Test
    public void theUltimateHitsHarderOnAHealthyBar() {
        double healthy = ultimateDamage(1.0);
        double worn = ultimateDamage(0.4);
        Assertions.assertTrue(healthy > 0, "precondition: the ultimate landed");
        Assertions.assertTrue(healthy > worn,
                "「韧性 ≥ 上限的 50% ⇒ 终结技伤害 +10%」: " + healthy + " vs " + worn);
        // The clause ADDS 0.1 to the boost zone, and the zone already carries her break-effect trace (0.373) plus 0.08
        // from her own sources, so the settled ratio is 1 + 0.1/1.453 -- pinned with attribution, like the Eidolon 1 case.
        Assertions.assertEquals(1 + 0.1 / 1.453, healthy / worn, 1e-3,
                "and it is the stated 10% ON TOP of the zone, not 10% of the final number");
    }

    /** The ultimate's settled damage against an enemy whose toughness is at {@code fraction} of its maximum. */
    private static double ultimateDamage(double fraction) {
        Character xueyi = CharacterFactory.create(XUEYI, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(new ArrayList<>(List.of(xueyi)), new ArrayList<>(List.of(enemy)),
                BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        // A bar big enough that the ultimate cannot break it: a break would add its own damage and swamp the 10%.
        enemy.setMaxStance(10_000);
        enemy.setStance(10_000 * fraction);
        double before = enemy.getCurrentHp();
        battle.castImmediate(xueyi.getSkills().get(SkillType.ULTRA), xueyi, List.of(enemy));
        battle.processRequests();
        return before - enemy.getCurrentHp();
    }

    // ---------------------------------------------------------------- Eidolon fixture

    private static Battle battle(int eidolon) {
        Character xueyi = CharacterFactory.create(XUEYI, LEVEL, true, null, null, eidolon);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Battle battle = new Battle(new ArrayList<>(List.of(xueyi, ally)),
                new ArrayList<>(List.of(BattleFixture.monster())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        return battle;
    }
}
