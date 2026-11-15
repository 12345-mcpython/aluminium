package com.laosun.aluminium.test.content.characters.phainon;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.energy.EnergyGain;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.skill.SkillExecutor;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TestTurns;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1408 Phainon (白厄): the base kit around the transformation - the [火种] pool and its ten-cast ceiling, the technique, the
 * trace that pays damage for a teammate's heal or shield, and the talent that pays Kindling and crit damage when he becomes a
 * skill target.
 *
 * <p>Consolidated (P2) from four sibling classes; each source's class doc is kept, as a plain comment, at the head of the group
 * it supplied. L3 then folded the cases that read one clause on one board into scenario tests ({@code Assertions.assertAll}),
 * one message per clause reading; every expectation, tolerance and message is unchanged.
 */
public class PhainonKitTest {
    private static final int PHAINON = 1408;
    private static final int ALLY = 1002;
    private static final int HEALER = 1204;
    private static final int SUPPORT = 1414;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-9;
    private static final String COREFLAME = "char:1408/ember";

    // ==================================================================
    // Former class: PhainonTest
    // ==================================================================
    /*
     * 1408 Phainon, from his own file: the [火种] resource his transformation kit still lets us declare.
     */

    /**
     * Note: Two Coreflame per Skill, and the document's cap of 12 enforced by exceeding it.
     */
    @Test
    public void theSkillFeedsCoreflameUpToTwelve() {
        Character phainon = CharacterFactory.create(PHAINON, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(phainon, ally), List.of(enemy), fixed());
        battle.startBattle();

        // Note: Updated: the document DOES state one -- her trace 1408101 "战斗开始时，获得 1 点[char:1408/ember]", which is
        // now written, so the pool opens at one. The reading is unchanged in kind: it starts where the sentences say.
        Assertions.assertEquals(1, coreflameOf(phainon),
                "「战斗开始时，获得 1 点【char:1408/ember】」 (at the start of battle, gains 1 point of Kindling (【char:1408/ember】))");
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, phainon, enemy, 0, 0));
        Assertions.assertEquals(3, coreflameOf(phainon),
                "「获得2点【char:1408/ember】」 (gains 2 points of Kindling) -- one from the battle start, two from the cast");

        for (int i = 0; i < 9; i++) {
            battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.SKILL_CAST, phainon, enemy, 0, 0));
        }
        // Note: 12 -> 15. This expectation was written while the file declared only `max: 12`, and the
        // document's sentence does not stop there: "[char:1408/ember]达到 12 点时可激活终结技，达到上限后还可最多溢出 3 点".
        // The test's INTENT is untouched -- the pool is capped, and ten casts cannot run past the ceiling -- but the ceiling now
        // quotes the whole sentence. The allowance itself is pinned from both sides by `CoreflameOverflowTest`.
        Assertions.assertEquals(15, coreflameOf(phainon),
                "「达到上限后还可最多溢出 3 点」 -- ten casts (20 points) stop at 12 + 3");
    }

    /**
     * Note: The technique restores the TEAM's energy (not hers) and grants one Skill Point.
     */
    @Test
    public void theTechniqueFeedsTheTeamButNotHerself() {
        Character phainon = CharacterFactory.create(PHAINON, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(phainon, ally), List.of(enemy), fixed());
        battle.markTechniqueUsed(phainon);
        battle.startBattle();
        int points = battle.getSkillPoints();

        // A grant cannot be clamped upward, so the claim is an equality -- and it is about the TEAM, not about her.
        double allyBefore = ally.getCurrentEnergy();
        double herBefore = phainon.getCurrentEnergy();

        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BATTLE_START, phainon, ally, 0, 0));

        Assertions.assertEquals(25.0, ally.getCurrentEnergy() - allyBefore, 1e-6,
                "「为我方队友恢复25点能量」 (restores 25 Energy to allied teammates)");
        Assertions.assertEquals(0.0, phainon.getCurrentEnergy() - herBefore, 1e-6,
                "the target is `other_allies`: the energy goes to the TEAM, not to her");
        Assertions.assertEquals(Math.min(points + 1, battle.getSkillPointMax()), battle.getSkillPoints(),
                "「获得1个战技点」 (clamped by the pool's ceiling)");
    }

    /**
     * The declared resource's value, read through the combatant's own manager.
     */
    private static int coreflameOf(Character unit) {
        return unit.getResources().get("char:1408/ember").getValue();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    // ==================================================================
    // Former class: CoreflameOverflowTest
    // ==================================================================
    /*
     * 1408："[火种]达到 12 点时可激活终结技，达到上限后还可最多溢出 3 点".
     *
     * <p>BOTH ENDS ARE PINNED: a value of 14 proves the pool really goes past its max of 12, and a ceiling of 15 proves the
     * overflow allowance is exactly three ("最多溢出 3 点"). Her skill grants two points per cast, so the casts are the dial.
     */

    /**
     * Both ends of the allowance, on one board and one dial (how many casts): six casts reach thirteen - which a cap of twelve
     * could never allow - and ten casts stop at fifteen, because the allowance is exactly three.
     *
     * <p>Note: Updated: "战斗开始时，获得 1 点[火种]" is now written (trace 1408101), so the pool opens at one. Seven casts
     * would then sit exactly ON the ceiling of 15 and stop isolating "past twelve" -- six reads 1 + 12 = 13, which is the same
     * reading the judge was written for.
     */
    @Test
    public void thePoolOverflowsItsCapByExactlyThree() {
        double afterSix = afterCasts(6);
        double afterTen = afterCasts(10);
        Assertions.assertAll("「达到上限后还可最多溢出 3 点」",
                () -> Assertions.assertEquals(13.0, afterSix, 1e-9,
                        "「达到上限后还可溢出」 (can overflow past the ceiling)-- 1 + 12 = 13 is above the declared max of 12"),
                () -> Assertions.assertEquals(15.0, afterTen, 1e-9,
                        "「最多溢出 3 点」 (overflows at most 3 points)-- 12 + 3 is the ceiling"));
    }

    private static double afterCasts(int casts) {
        Character owner = CharacterFactory.create(PHAINON, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        Skill skill = owner.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: she has a skill");
        for (int i = 0; i < casts; i++) {
            // Note: AIMED AT AN ENEMY. The first version of this judge aimed her skill at HERSELF, and that single choice made the
            // resource read a constant 3 for every cast count: the first self-aimed cast landed (+2 for the skill, +1 for item 38's
            // "being targeted" rule) and the later ones did nothing at all. Measured step by step, aimed at an enemy, the pool is
            // 3, 5, , 9, 11, 13 -- the battle-start one plus exactly two per cast, up to and past its declared cap.
            SkillExecutor.execute(battle, skill, owner, List.of(battle.enemies().get(0)));
            battle.processRequests();
        }
        // Note: `owner.getResources().value(...)`, and NOT `battle.partyResource(...)`: Coreflame is not a party resource, so the
        // party view is null. The reader itself is innocent -- measured against 1412's Charge in the same probe, which accumulates
        // exactly (+1 per cast).
        return owner.getResources().value(COREFLAME);
    }

    // ==================================================================
    // Former class: PhainonTraceBideInFlamesTest
    // ==================================================================
    /*
     * Character 1408 (Phainon 白厄), trace 1408102 【身承炎炬万千】: 「受到<b>队友</b>提供的治疗效果或护盾时，造成的伤害提高
     * <b>45%</b>，持续 <b>4</b> 回合。该效果<b>单个回合内不可重复触发</b>。」 - and its second sentence, which is NOT written.
     *
     * <p>Four readings pin the four things that can go wrong with this sentence, each of them a different way to be wrong:
     * the share (0.45), the duration (gone after the fourth of his own turns, still there after the third), <b>who</b> counts as
     * the giver (a teammate yes, himself no), and the second source (a shield, not only a heal).
     *
     * <p>⚠ What is NOT asserted here, and why: 「单个回合内不可重复触发」 ({@code per_turn: 1}). Two applications inside one
     * turn produce the SAME number (the second one refreshes 0.45 to 0.45), so no reading of the attribute can tell them
     * apart; the field's own judge is the engine-level {@code TriggerLimitTest}. What this class pins instead is that the rule
     * carries the limit at all - see {@link #theRuleCarriesTheOncePerTurnLimit()}.
     *
     * <p>⚠ The second sentence (「受到队友提供的能量恢复的技能效果时，获得 1 点【火种】」) is a <b>witness</b>:
     * {@code ENERGY_GAINED} fires with the RECEIVER as its actor and carries no granter, so "from a teammate's ability" cannot
     * be asked today. {@link #theEnergyHalfIsStillANoOp()} pins today's (wrong-by-omission) reading so the gap cannot quietly
     * change, and the rule's own note registers it.
     */

    /**
     * 「受到<b>队友</b>提供的治疗效果或护盾时，造成的伤害提高 <b>45%</b>，持续 <b>4</b> 回合」 - the whole sentence on its own
     * board: a teammate's heal pays 45% and still pays it after his third own turn but not after his fourth, a teammate's SHIELD
     * is the sentence's second source, and his own heal is not "a teammate's".
     */
    @Test
    public void aTeammatesHealingOrShieldRaisesHisDamageForFourTurns() {
        // Scene 1: a teammate's heal, and the four-turn duration.
        Battle healBattle = newBattle();
        Character healed = healBattle.characters().getFirst();
        Character healer = healBattle.characters().get(1);
        healed.consumeHp(healed.getMaxHp() * 0.3);

        double healBefore = boost(healed);
        healBattle.heal(healer, healed, healed.getMaxHp() * 0.1);
        healBattle.processRequests();
        double healedGain = boost(healed) - healBefore;
        System.out.println("[1408] 队友治疗后的增伤: +" + healedGain);

        TestTurns.take(healBattle, healed);
        TestTurns.take(healBattle, healed);
        TestTurns.take(healBattle, healed);
        double afterThreeTurns = boost(healed) - healBefore;
        TestTurns.take(healBattle, healed);
        double afterFourTurns = boost(healed) - healBefore;
        System.out.println("[1408] 第 4 个自己的回合开始后的增伤: +" + afterFourTurns);

        // Scene 2: a teammate's SHIELD, the sentence's second source.
        Battle shieldBattle = newBattle();
        Character shielded = shieldBattle.characters().getFirst();
        Character shielder = shieldBattle.characters().get(1);
        double shieldBefore = boost(shielded);
        shieldBattle.grantShield(shielder, shielded, 200);
        shieldBattle.processRequests();
        double shieldGain = boost(shielded) - shieldBefore;
        System.out.println("[1408] 队友护盾后的增伤: +" + shieldGain);

        // Scene 3: his own heal - 「队友提供的」 is the gate.
        Battle selfBattle = newBattle();
        Character selfHealed = selfBattle.characters().getFirst();
        selfHealed.consumeHp(selfHealed.getMaxHp() * 0.3);
        double selfBefore = boost(selfHealed);
        selfBattle.heal(selfHealed, selfHealed, selfHealed.getMaxHp() * 0.1);
        selfBattle.processRequests();
        double selfGain = boost(selfHealed) - selfBefore;
        System.out.println("[1408] 自己治疗后的增伤: +" + selfGain);

        Assertions.assertAll("1408102 「受到队友提供的治疗效果或护盾时，造成的伤害提高 45%，持续 4 回合」",
                () -> Assertions.assertEquals(0.45, healedGain, 1e-9, "「受到队友提供的治疗效果…造成的伤害提高 45%」"),
                () -> Assertions.assertEquals(0.45, afterThreeTurns, 1e-9,
                        "「持续 4 回合」⇒ 第 3 个自己的回合之后仍然在"),
                () -> Assertions.assertEquals(0.0, afterFourTurns, 1e-9,
                        "「持续 4 回合」⇒ 第 4 个自己的回合开始时到期"),
                () -> Assertions.assertEquals(0.45, shieldGain, 1e-9,
                        "「治疗效果或护盾」—— 护盾那一半也是同一个效果"),
                () -> Assertions.assertEquals(0.0, selfGain, 1e-9,
                        "「队友提供的」—— 少了 `actor is_other_ally` 时这一条会红"));
    }

    /**
     * The rule carries the once-per-turn limit, and it is <b>one</b> rule on <b>both</b> events.
     *
     * <p>⚠ Two applications inside one turn are indistinguishable by the attribute's value (the second one refreshes 0.45
     * to 0.45), so the limit's reading is the engine-level {@code TriggerLimitTest}. What this case can pin is the shape the
     * sentence needs: one rule for the two sources, or the limit would be two allowances ("一次治疗 + 一次护盾" per turn).
     */
    @Test
    public void theRuleCarriesTheOncePerTurnLimit() {
        Character phainon = newBattle().characters().getFirst();
        List<TriggerTable.CompiledRule> onHeal = rulesFor(phainon, TriggerEvent.HEALED);
        Assertions.assertEquals(1, onHeal.size(), "HEALED 上只该有这一条");
        Assertions.assertEquals(1, onHeal.getFirst().perTurn(), "「该效果单个回合内不可重复触发」= per_turn: 1");
        Assertions.assertEquals(1, rulesFor(phainon, TriggerEvent.SHIELD_GRANTED).size(),
                "护盾那一半是**同一条**规则（`on_any`）—— 拆成两条就等于每回合可以触发两次");
    }

    /**
     * ⚠ <b>GAP WITNESS.</b> 「受到队友提供的能量恢复的技能效果时，获得 1 点【火种】」 - today's reading is "nothing", because
     * the energy event names the receiver and not the granter. When the event grows a granter, this case goes red and the
     * registered half can be written.
     */
    @Test
    public void theEnergyHalfIsStillANoOp() {
        Battle battle = newBattle();
        Character phainon = battle.characters().getFirst();
        int before = phainon.getResources().value(COREFLAME);
        battle.applyEnergyGain(phainon, EnergyGain.normal(10));
        battle.processRequests();
        int after = phainon.getResources().value(COREFLAME);
        System.out.println("[1408] 收到 10 点回能后的char:1408/ember: " + before + " -> " + after + " (登记：这一句还没写)");
        Assertions.assertEquals(before, after,
                "登记中的缺口：ENERGY_GAINED 不带「谁给的」，所以「队友提供的回能」今天判不了");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static double boost(Character unit) {
        return unit.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }

    /**
     * The compiled rules one event carries for Phainon.
     */
    private static List<TriggerTable.CompiledRule> rulesFor(Character unit, TriggerEvent event) {
        return unit.getTriggerTable().rulesFor(event);
    }

    /**
     * Note: The ally here is 1204 (the healer of the trace cases), not PhainonTest's 1002 teammate - the former class called
     * this constant `ALLY`, which now names the teammate of the kit group, so it is renamed rather than unified.
     */
    private static Battle newBattle() {
        Battle battle = new Battle(
                List.of(CharacterFactory.create(PHAINON, 80),
                        CharacterFactory.create(HEALER, 80)),
                List.of(BattleFixture.monster()), BattleFixture.alwaysRoll(0.9999));
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    // ==================================================================
    // Former class: SkillTargetCoreflameTest
    // ==================================================================
    /*
     * 1408: "when Phainon (白厄) becomes the skill target of any other target, he gains 1 point of [火种] (Coreflame). If the caster is a teammate of Phainon, it also increases Phainon's
     * crit damage by 30%, lasting 3 turns".
     *
     * <p>SAME SCENE, ONE VARIABLE: 1414 -- whose skill is a shield aimed at a teammate -- casts it either AT 1408 or AT THE ENEMY. The
     * only difference is who was aimed at, which is exactly what the sentence is about.
     */

    /**
     * SAME SCENE, ONE VARIABLE: 1414's shield skill cast AT him pays a point of [火种] and 30% crit damage; cast AT THE ENEMY
     * it pays nothing, because the sentence never starts.
     */
    @Test
    public void theTalentPaysOnlyWhenHeIsTheSkillTarget() {
        double[] aimedAtHer = scene(true);
        double[] aimedAway = scene(false);
        Assertions.assertAll("「when Phainon becomes the skill target of any other target」",
                () -> Assertions.assertEquals(1.0, aimedAtHer[0], EPS, "「gains 1 point of [char:1408/ember] (Kindling)」"),
                () -> Assertions.assertEquals(0.30, aimedAtHer[1], EPS, "「crit damage increased by 30%」"),
                () -> Assertions.assertEquals(0.0, aimedAway[0], EPS, "「when becoming ... a skill target」 -- she was not the target"),
                () -> Assertions.assertEquals(0.0, aimedAway[1], EPS, "…so there is no crit damage either"));
    }

    // ==================================================================

    /**
     * returns { Coreflame gained, crit damage gained }.
     */
    private static double[] scene(boolean aimAtHer) {
        Character owner = CharacterFactory.create(PHAINON, 80, false, null, null, 0);
        Character support = CharacterFactory.create(SUPPORT, 80, false, null, null, 0);
        Battle battle = new Battle(List.of(owner, support),
                List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();

        double coreBefore = owner.getResources().value(COREFLAME);
        double critBefore = owner.getAttribute(AttributeType.CRIT_ATTACK).get();

        Skill skill = support.getSkills().get(SkillType.SKILL);
        Assertions.assertNotNull(skill, "precondition: the teammate has a skill");
        // Note: Two branches, not a ternary expression: one is a `Character` and the other a `CanHit`, and there is no common type to declare.
        if (aimAtHer) {
            SkillExecutor.execute(battle, skill, support, List.of(owner));
        } else {
            SkillExecutor.execute(battle, skill, support, List.of(battle.enemies().get(0)));
        }
        battle.processRequests();

        return new double[]{
                owner.getResources().value(COREFLAME) - coreBefore,
                owner.getAttribute(AttributeType.CRIT_ATTACK).get() - critBefore};
    }
}
