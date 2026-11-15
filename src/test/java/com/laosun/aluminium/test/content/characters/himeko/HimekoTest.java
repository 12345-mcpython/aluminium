package com.laosun.aluminium.test.content.characters.himeko;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.DamageType;
import com.laosun.aluminium.enums.SkillCategory;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TestCharacters;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Himeko (姬子) (1003): her charge resource, her 秘技's Fire-only vulnerable zone, the 2026-10-19 audit of her
 * traces and Eidolons, and the kill-energy credit that rides on her ultimate - every case about her, in one file.
 *
 * <p><b>Why one file.</b> This class merges {@code HimekoChargeTest}, {@code HimekoTechniqueTest},
 * {@code HimekoAuditTest} and {@code HimekoKillEnergyTest}. Nothing about the cases changed in the merge: none was
 * dropped, disabled, renamed or softened, and every expected value is the one its original class asserted. The
 * original framing of each group is quoted at the head of its section below.
 *
 * <p><b>L3 scenario consolidation.</b> The single-clause cases are folded into scenario tests that call
 * {@code Assertions.assertAll}: each merged test keeps every assertion, expected value, tolerance and message of the
 * cases it absorbed, and asserts them together only where the fixture and the observed object are shared.
 */
public class HimekoTest {
    /**
     * Himeko (姬子) herself.
     */
    private static final int HIMEKO = 1003;
    private static final int LEVEL = 80;
    private static final double EPS = 1e-6;
    /**
     * The tighter tolerance the 秘技 section measures a multiplier with: those ratios have to land on the last digit
     * (measured, {@code 550.799986} becomes {@code 605.879985}, exactly 1.1), where 1e-6 would hide the drift the
     * case exists to catch.
     */
    private static final double EPS_STRICT = 1e-9;
    private static final int ULTIMATE_SLOT = 3;
    /**
     * What the vulnerable area reads when nothing has raised it.
     *
     * <p>⚠ Measured, and the reason an earlier version of this test failed against a working rule: the area is a MULTIPLIER, so an
     * untouched hit reads {@code 1.0} (bare 1.0, one +0.1 -> 1.1, two -> 1.3). "No zone" is 1.0, not 0.
     */
    private static final double NO_ZONE = 1.0;
    // Note: this test's ally must be SINGLE-TARGET, named here instead of taken from the shared
    // "unregistered character" control. Measured: with 1505 as the control, and again with 1502/1506, the
    // bystander enemy in `onePointShortOfTheCapNothingHappens` lost 26.6 HP -- because that test has the ally cast
    // `DefaultSkill(ALLY, 1, 1)` at the FIRST enemy while asserting on the SECOND, and slot 1 is a blast for some
    // characters and a single hit for others. 1002 (Dan Heng, The Hunt) attacks one enemy, so the witness stays out of
    // reach whatever the shared control happens to be.
    /**
     * An ordinary character with no rules of their own: the ally who attacks, so nothing but her own table can
     * react ({@link TestCharacters}).
     */
    private static final int ALLY = 1002;
    /**
     * Ice Edge (冰刃) @90: Fire-weak with 60 toughness, so two Fire basic attacks break it.
     */
    private static final int ICE_EDGE = 1002011;
    /**
     * The resource her Talent declares.
     */
    private static final String CHARGE = "char:1003/charge";
    /**
     * The row the document quotes for a maxed ability (its param tables are 1-based).
     */
    private static final int LEVEL_ROW = 10;

    // ==================================================================
    // 1. Her charge resource: the talent (天赋) 乘胜追击
    // ==================================================================

    /*
     * Himeko (姬子) (1003) - the first character whose kit is built on a <b>declared resource</b>.
     *
     * <p><b>Why her.</b> Her document is the corpus's most common shape that had no spelling: "gains charge, cap 3 points",
     * "if the charge reaches its cap then...", "consumes all charge". The ops to write such a resource existed, but nothing could
     * <em>read</em> one back, and nothing could declare one at all - 41 of the 9character documents gate something on
     * a count, which is the largest single hole the corpus scan found. Her three rules are the reader
     * that pays for both capabilities: {@code self_resource:<NAME>} in the condition DSL, and the {@code resources}
     * block that says what the cap is.
     *
     * <p>Note: What is <b>not</b> authored, and the capability each clause would need, is listed in
     * {@code characters/1003.json}'s note - Eidolon (星魂) 4's "when casting the Skill... on a break" and the ultimate's per-kill energy both need to
     * know <em>which ability</em> caused the event, which no event carries.
     */

    // ------------------------------------------------------------------
    // The three clauses of the talent (天赋) "乘胜追击" (Press the Advantage)
    // ------------------------------------------------------------------

    /**
     * The talent's charge in one run: "战斗开始时获得1点充能。" and "当有敌方目标的弱点被击破时，姬子获得充能，上限3点。"
     *
     * <p>The two clauses share one fixture - her alone against one Ice Edge, seeded - and one observed object, her
     * 充能, so they are one scenario: the resource starts at 0, the battle start grants the first point, and a real
     * weakness break grants the second. The break is <b>real</b>, not a fired event: two of her Fire basic attacks
     * empty Ice Edge's 60 toughness, so the rule has to fire from inside the engine's own break path.
     *
     * <p>Absorbs {@code theBattleStartsHerAtOneCharge} and {@code abreakChargesHerAndTheCapHolds}.
     */
    @Test
    public void herChargeStartsAtOneAndEveryBreakAddsOneUpToTheCap() {
        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        Enemy iceEdge = dummy();
        Battle battle = new Battle(List.of(himeko), List.of(iceEdge), BattleFixture.seeded());
        int atDeclaration = himeko.getResources().value(CHARGE);
        battle.startBattle();

        Assertions.assertAll("「战斗开始时获得1点充能」",
                () -> Assertions.assertEquals(0, atDeclaration,
                        "precondition: the declaration alone starts her at 0 -- the sentence is a rule, not an initial value"),
                () -> Assertions.assertEquals(1, himeko.getResources().value(CHARGE),
                        "「战斗开始时获得1点充能」 (gains 1 point of Charge at the start of battle)"));

        battle.castImmediate(new DefaultSkill(HIMEKO, 1, 1), himeko, List.of(iceEdge));
        int afterOneAttack = himeko.getResources().value(CHARGE);
        Assertions.assertAll("one attack, no break",
                () -> Assertions.assertFalse(iceEdge.isBroken(), "precondition: one basic attack does not empty the bar"),
                () -> Assertions.assertEquals(1, afterOneAttack, "and no break means no charge"));

        battle.castImmediate(new DefaultSkill(HIMEKO, 1, 1), himeko, List.of(iceEdge));
        int afterBreak = himeko.getResources().value(CHARGE);
        Assertions.assertAll("「当有敌方目标的弱点被击破时，姬子获得充能」",
                () -> Assertions.assertTrue(iceEdge.isBroken(), "the second one breaks it"),
                () -> Assertions.assertEquals(2, afterBreak,
                        "BATTLE_START's 1 plus the break's 1 -- 「当有敌方目标的弱点被击破时，姬子获得充能」 (when an enemy target's Weakness is Broken, Himeko gains Charge)"));

        // Two more breaks: the third point lands, the fourth is swallowed by the cap (overflow is 0 by default,
        // so "上限3点" is enforced by the declaration rather than by arithmetic in the rule).
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, himeko, iceEdge, 0, 0));
        int atCap = himeko.getResources().value(CHARGE);
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, himeko, iceEdge, 0, 0));
        int pastCap = himeko.getResources().value(CHARGE);
        Assertions.assertAll("「上限3点」 holds",
                () -> Assertions.assertEquals(3, atCap, "the cap is reached"),
                () -> Assertions.assertEquals(3, pastCap, "and cannot be passed"));
    }

    /**
     * At the cap, one ally attack fires her follow-up on <b>every</b> enemy and spends the whole charge - and that
     * follow-up is not an attack that can trigger itself.
     *
     * <p>Both clauses share one fixture - her, a single-target ally, and two enemies - and one observed object, her
     * 充能 together with the enemies' HP, so they are one scenario, asserted as one. A real ally attack drives the
     * first claim, and the claim is about <b>both</b> enemies: "敌方全体" is the thing the selector had to learn, and
     * the second enemy is the witness - the ally aims at the first one only, so anything the second one loses came
     * from her follow-up. The second claim is then measured on the same enemies: once the charge is spent, the next
     * ally attack does nothing at all.
     *
     * <p>Absorbs {@code atTheCapAnAllyAttackFiresHerFollowUpOnEveryEnemyAndSpendsItAll} and
     * {@code theFollowUpCannotRetriggerItself}.
     */
    @Test
    public void atTheCapHerFollowUpHitsEveryEnemySpendsTheChargeAndCannotRetriggerItself() {
        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy first = dummy();
        Enemy second = dummy();
        Battle battle = new Battle(List.of(himeko, ally), List.of(first, second), BattleFixture.seeded());
        battle.startBattle();
        chargeTo(himeko, first, battle);

        // 1. The follow-up cannot retrigger itself: the first ally attack spends the charge, the second finds none.
        int firedOnce = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, null, 1, 0));
        double secondBeforeRetrigger = second.getCurrentHp();
        int firedAgain = battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, null, 1, 0));
        Assertions.assertAll("the follow-up cannot retrigger itself",
                () -> Assertions.assertEquals(1, firedOnce, "the rule fires once"),
                () -> Assertions.assertEquals(0, firedAgain, "the charge is gone, so nothing fires again"),
                () -> Assertions.assertEquals(secondBeforeRetrigger, second.getCurrentHp(), EPS,
                        "the second enemy takes no second hit once the charge is gone"));

        // 2. 「对敌方全体目标」 and 「并消耗全部充能」, read on the same fixture brought back to the cap.
        chargeTo(himeko, first, battle);
        int atCap = himeko.getResources().value(CHARGE);
        double firstBefore = first.getCurrentHp();
        double secondBefore = second.getCurrentHp();
        double ourSideBefore = ourSideHp(battle);

        battle.castImmediate(new DefaultSkill(ALLY, 1, 1), ally, List.of(first));

        Assertions.assertAll("one ally attack at the cap",
                () -> Assertions.assertEquals(3, atCap, "precondition: she is at the cap"),
                () -> Assertions.assertTrue(first.getCurrentHp() < firstBefore, "the first enemy was hit"),
                () -> Assertions.assertTrue(second.getCurrentHp() < secondBefore,
                        "「对敌方全体目标」 (to all enemies) -- and so was the one the ally never aimed at"),
                () -> Assertions.assertEquals(ourSideBefore, ourSideHp(battle), EPS, "on the enemies, not on our side"),
                () -> Assertions.assertEquals(0, himeko.getResources().value(CHARGE),
                        "「并消耗全部充能」 (and consumes all of the Charge)"));
    }

    /**
     * Below the cap nothing happens - the same attack, one point short.
     */
    @Test
    public void onePointShortOfTheCapNothingHappens() {
        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy first = dummy();
        Enemy second = dummy();
        Battle battle = new Battle(List.of(himeko, ally), List.of(first, second), BattleFixture.seeded());
        battle.startBattle();
        chargeTo(himeko, first, battle);
        himeko.getResources().spend(CHARGE, 1);
        Assertions.assertEquals(2, himeko.getResources().value(CHARGE), "precondition: 2 of 3");

        double secondBefore = second.getCurrentHp();
        battle.castImmediate(new DefaultSkill(ALLY, 1, 1), ally, List.of(first));

        Assertions.assertEquals(secondBefore, second.getCurrentHp(), EPS, "「若…达到上限」 (if ... reaches the ceiling) is not met");
        Assertions.assertEquals(2, himeko.getResources().value(CHARGE), "and nothing was spent");
    }

    /**
     * The follow-up's damage is the <b>Talent's own Lv10 row</b>, not a literal written into the rule.
     *
     * <p>Asserted against the engine's own settlement of the same base rather than against a recorded number: with
     * crit switched off (so no draw is taken and the value is deterministic) the rule's swing and a hand-made
     * {@code applyAdditionalDamage} of {@code row  x  ATTACK} must agree <b>exactly</b> - which is the whole claim of
     * {@code skill} + {@code damage_param} + {@code damage_level}. A rule that spelled 1.4 as a literal would pass
     * this too; a rule that read the Lv1 row (0.) or column 1 (the cap, 3.0) would not.
     */
    @Test
    public void theFollowUpDealsTheTalentRowsLv10Damage() {
        double row = talentRow(HIMEKO, LEVEL_ROW, 0);
        Assertions.assertEquals(1.4, row, EPS,
                "the number the document quotes: 「等同于姬子140%攻击力」 (equal to 140% of Himeko's ATK) is 100304's Lv10, column 0");

        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        // Crit off: the pipeline only draws for a crit when the rate is above 0, so every number below is exact.
        himeko.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        Enemy first = dummy();
        Enemy second = dummy();
        Battle battle = new Battle(List.of(himeko), List.of(first, second), BattleFixture.seeded());
        battle.startBattle();
        chargeTo(himeko, first, battle);

        double secondBefore = second.getCurrentHp();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, himeko, null, 1, 0));
        double dealt = secondBefore - second.getCurrentHp();
        Assertions.assertTrue(dealt > 0, "precondition: the follow-up landed on the second enemy");

        double referenceBefore = second.getCurrentHp();
        battle.applyAdditionalDamage(himeko, second,
                himeko.getSkills().get(SkillType.TALENT).getData().getElement(),
                himeko.getAttribute(AttributeType.ATTACK).get() * row);
        double reference = referenceBefore - second.getCurrentHp();

        Assertions.assertEquals(reference, dealt, EPS,
                "the rule's damage is the skill row's share of her ATTACK, settled by the engine's own channel");
    }

    /**
     * The rest of her kit is <b>authored as of 2026-10-19</b>; this case is what keeps it authored.
     *
     * <p>It used to assert the opposite (「registered, not approximated」), with the counts saying "nothing else was
     * written". The three traces, Eidolon (星魂) 1's speed, Eidolon 2's gate on the low-HP share and Eidolon 3/5's level
     * statements all landed in that pass, so the same counts now say the opposite: every event her text needs carries a
     * rule, and the only clauses still registered are the ones with no vocabulary (see the status row).
     */
    @Test
    public void theRestOfHerKitIsAuthored() {
        TriggerTable table = TriggerTables.of(HIMEKO);
        // 5 rules: 「战斗开始时获得1点充能」, the talent's level statement, 秘技, and the two eidolon level statements (3 and 5).
        Assertions.assertEquals(5, table.ruleCount(TriggerEvent.BATTLE_START),
                "start-of-battle charge, the talent's level statement, her 秘技, and the E3/E5 level statements");
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.BREAK),
                "anybody's break, and Eidolon 4's Skill-triggered break");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ALLY_ATTACK),
                "「当我方目标施放攻击后」 (after one of our targets casts an attack)");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ATTACK_FINISHED),
                "行迹 星火 (Starfire): her attack can set dot:burn on fire (2026-10-19)");
        Assertions.assertEquals(0, table.ruleCount(TriggerEvent.ULT_CAST), "her ultimate is the engine's ordinary path");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.KILL), "「每消灭1个敌方目标额外恢复5点能量」");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.FOLLOW_UP),
                "Eidolon 1's speed rides on her own follow-up (2026-10-19)");
        Assertions.assertEquals(0, table.ruleCount(TriggerEvent.SKILL_CAST),
                "Eidolon 4's charge rides on BREAK with `from_skill SKILL`, not on a cast event of its own");
        Assertions.assertEquals(1, table.resources().size(), "and she declares exactly one resource");
        Assertions.assertEquals(CHARGE, table.referencedResources().iterator().next(),
                "every resource a rule names is the one she declares -- checked when she is built");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * One entry of a skill's parameter row, as the interpreter reads it.
     */
    private static double talentRow(int cid, int level, int column) {
        Character character = CharacterFactory.create(cid, LEVEL);
        return character.getSkills().get(SkillType.TALENT).getData().getSkills().get(level - 1).get(column);
    }

    /**
     * Grants charge through her own BREAK rule until it reaches the cap.
     */
    private static void chargeTo(Character himeko, Enemy enemy, Battle battle) {
        while (himeko.getResources().value(CHARGE) < 3) {
            Assertions.assertEquals(1, battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.BREAK, himeko, enemy, 0, 0)),
                    "her break rule must fire for an enemy break");
        }
    }

    private static double ourSideHp(Battle battle) {
        return battle.allies().stream().mapToDouble(CanHit::getCurrentHp).sum();
    }

    private static Enemy dummy() {
        return EnemyFactory.create(ICE_EDGE, 90, 1);
    }

    // ==================================================================
    // 2. Her 秘技 「不完全燃烧」: the gate, the magnitude, and Fire-only
    // ==================================================================

    /*
     * Himeko's 秘技 「不完全燃烧」: the gate, the magnitude, and that it is <b>Fire-only</b>.
     *
     * <p>「使用秘技后，制造1片持续15秒的特殊领域。与处于特殊领域内的敌人进入战斗后，有100%的基础概率使敌方目标受到的<b>火属性</b>伤害
     * 提高10%，持续2回合。」
     *
     * <p>⚠ <b>Why the fire/ice pair is the point.</b> Before this rule her clause had no spelling: {@code MODIFY_DAMAGE_TAKEN}'s only
     * scope was a damage TYPE ({@code DOT}, {@code BREAK}, {@code ADDITIONAL}, {@code ULTRA}), and the attribute vocabulary offered
     * {@code ALL_TYPE_RESISTANCE} (every element). The cheap version of this rule -- a plain all-type vulnerability -- would have said
     * 10% more of EVERY element: a wrong number with no symptom, and nothing else in the suite would have noticed, because the rule
     * would still fire at battle start and still produce a zone.
     */

    /**
     * ⭐ The zone 「不完全燃烧」: it exists only when the technique was declared, it raises the damage the enemies
     * take by 10%, it is FIRE-only, and it lasts two turns.
     *
     * <p>All four claims read the same fixture she alone against the fixture's monster, with the technique optionally
     * declared and the same object, the zone the 秘技 applies to that enemy, so they are one scenario, asserted as
     * one. The pair is asserted together, because a rule gated on {@code self has_state sys:technique} can only be
     * trusted against its own control: "the zone appeared" alone would pass for the wrong reason if something else
     * also applied it. The duration is asserted as "the countdown it was given", not as "it expires after N of my
     * calls": measured, a carrier's buffs are ticked by the turn loop on ITS OWN turn, and
     * {@code Battle.afterMove()} acts on the battle's current actor rather than on a unit handed to it -- an earlier
     * version called it repeatedly and watched a 2-turn zone sit unchanged through six calls, failing against a
     * working rule.
     *
     * <p>Absorbs {@code aDeclaredTechniqueRaisesTheFireDamageTheEnemiesTake},
     * {@code itLeavesOtherElementsUntouched} and {@code theZoneCarriesTheStatedDuration}.
     */
    @Test
    public void theTechniqueZoneIsFireOnlyWorthTenPercentAndLastsTwoTurns() {
        double fireOff = elementVulnerability(false, DamageElement.FIRE);
        double fireOn = elementVulnerability(true, DamageElement.FIRE);
        double fireOnAgain = elementVulnerability(true, DamageElement.FIRE);
        double ice = elementVulnerability(true, DamageElement.ICE);
        double physical = elementVulnerability(true, DamageElement.PHYSICAL);
        Battle battle = techniqueBattle(true);
        Enemy enemy = battle.enemyUnits().get(0);

        int duration = enemy.getBuffManager().activeBuffs().stream()
                .mapToInt(buff -> buff.duration())
                .max()
                .orElse(-1);
        Assertions.assertAll("the 秘技 「不完全燃烧」 zone",
                () -> Assertions.assertEquals(NO_ZONE, fireOff, EPS_STRICT,
                        "「使用秘技后」 -- without the technique the zone must not exist at all"),
                () -> Assertions.assertEquals(0.1, fireOn - NO_ZONE, EPS_STRICT,
                        "「使敌方目标受到的火属性伤害提高10%」 -- 10%, and of the damage-taken zone rather than a boost"),
                () -> Assertions.assertEquals(0.1, fireOnAgain - NO_ZONE, EPS_STRICT,
                        "Fire is raised"),
                () -> Assertions.assertEquals(NO_ZONE, ice, EPS_STRICT,
                        "and Ice is not -- 「火属性」 names one element, not all of them"),
                () -> Assertions.assertEquals(NO_ZONE, physical, EPS_STRICT,
                        "nor Physical"),
                () -> Assertions.assertEquals(2, duration,
                        "「持续2回合」 -- the zone the technique applies lasts two turns"));
    }

    /**
     * ⭐ The SETTLED number rises by exactly 10% for Fire, and not at all for Ice.
     *
     * <p>⚠ The other cases read the zone's own multiplier, which proves only that it was INJECTED. This one reads what the engine
     * actually settles, because `Battle.applyDamage` is where a zone turns into damage: measured, Fire `550.799986` becomes
     * `605.879985` -- a ratio of exactly 1.1 -- while Ice stays `359.999991` to the last digit. A zone attached to an instance whose
     * settlement ignored it would pass every other case here and fail this one.
     */
    @Test
    public void theSettledFireDamageRisesByTenPercent() {
        double fireOff = settled(false, DamageElement.FIRE);
        double fireOn = settled(true, DamageElement.FIRE);
        double iceOff = settled(false, DamageElement.ICE);
        double iceOn = settled(true, DamageElement.ICE);

        Assertions.assertEquals(1.1, fireOn / fireOff, 1e-9,
                "「使敌方目标受到的火属性伤害提高10%」 -- the settled Fire damage must scale by exactly 1.1: "
                        + fireOff + " -> " + fireOn);
        Assertions.assertEquals(iceOff, iceOn, 0.0,
                "and Ice must be BIT-IDENTICAL, not merely close: an element scope is a filter, not a multiplier to trim. "
                        + iceOff + " vs " + iceOn);
    }

    /**
     * The vulnerability rate one element's hit collects from the enemies' own buffs.
     *
     * @param technique whether Himeko's technique was declared
     * @param element   the element of the hit
     * @return the vulnerable area's value (1.0 = nothing raised it)
     */
    private static double elementVulnerability(boolean technique, DamageElement element) {
        Battle battle = techniqueBattle(technique);
        Enemy enemy = battle.enemyUnits().get(0);
        Damage damage = new Damage(null, enemy, element, DamageType.NORMAL, 1000);
        // ⚠ The unit's own entry point, which is the path settlement takes: `CanHit.onDamage` walks its own buffs, so this
        // cannot drift from what a real hit would collect. Casting instead would fold in resistance, defence and crit as well, and
        // this test's claim is about the zone alone.
        enemy.onDamage(battle, damage);
        return damage.vulnerableArea().raw().get();
    }

    /**
     * One started battle, optionally with her technique declared.
     *
     * @param technique whether she used it
     * @return the battle
     */
    private static Battle techniqueBattle(boolean technique) {
        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(himeko), List.of(enemy), BattleFixture.alwaysRoll(1.0));
        if (technique) {
            battle.markTechniqueUsed(himeko);
        }
        battle.startBattle();
        return battle;
    }

    /**
     * The damage the engine actually settles for one element's hit, against a nominal 1000 base.
     *
     * <p>⚠ Through {@link Battle#applyDamage}, the settlement entry point, so this goes through the whole zone pipeline -- defence,
     * resistance, boosts and the vulnerable area together -- rather than summing the zone by hand.
     *
     * @param technique whether the technique was declared
     * @param element   the element of the hit
     * @return the settled amount
     */
    private static double settled(boolean technique, DamageElement element) {
        Battle battle = techniqueBattle(technique);
        Enemy enemy = battle.enemyUnits().get(0);
        return battle.applyDamage(enemy,
                new Damage(battle.characters().get(0), enemy, element, DamageType.NORMAL, 1000));
    }

    // ==================================================================
    // 3. The 2026-10-19 audit: the three traces and Eidolon 1
    // ==================================================================

    /*
     * Himeko (1003)'s 2026-10-19 audit: the three traces and Eidolon 1, read as battles.
     *
     * <p>What these cases are for: the clauses were authored from the text, and each one can be *wired to the wrong
     * channel* while still looking right in the file - a trait that never lands, a speed buff hung on an event her own
     * follow-up never fires, a crit share that ignores the HP line. Each case below therefore reads an ATTRIBUTE or a
     * STATE after a REAL cast, not the file.
     */

    /**
     * 行迹 星火: her attack can set dot:burn, and the burn's own damage is her 30% ATK per turn.
     */
    @Test
    public void traceStarfireSetsTheBurn() {
        Character her = CharacterFactory.create(HIMEKO, LEVEL);
        Enemy victim = BattleFixture.monster();
        // Roll under the 50% base chance: the burn lands.
        Battle battle = new Battle(List.of(her), List.of(victim), always(0.1));
        battle.startBattle();
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(victim));
        battle.processRequests();
        boolean burning = victim.getBuffManager().hasState("dot:burn") || victim.getBuffManager().stacksOf("dot:burn") > 0;
        System.out.println("[1003] dot:burn landed=" + burning);
        Assertions.assertTrue(burning, "「有 50% 基础概率使敌方目标陷入灼烧状态」");

        // ⚠ DOT 的每回合结算走敌方回合的真实流程，这个夹具不发那个事件 ⇒ 只钉「灼烧上了」与它自己的快照值。
        System.out.println("[1003] 灼烧已挂上；每回合的 30% 伤害由该 DOT 自己结算（见状态表）");
    }

    /**
     * 星魂 1: her own follow-up (the talent) raises her speed.
     */
    @Test
    public void eidolonOneSpeedsHerUpAfterTheFollowUp() {
        Character her = CharacterFactory.create(HIMEKO, LEVEL, false, null, null, 1);
        Enemy victim = BattleFixture.monster();
        Battle battle = new Battle(List.of(her), List.of(victim), always(0.99));
        battle.startBattle();
        double before = her.getAttribute(AttributeType.SPEED).get();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(com.laosun.aluminium.enums.TriggerEvent.FOLLOW_UP, her, victim, 0, 0));
        battle.processRequests();
        double gained = her.getAttribute(AttributeType.SPEED).get() - before;
        System.out.println("[1003] 星魂1 追加攻击后速度增量 " + gained);
        Assertions.assertTrue(gained > 0, "「【乘胜追击】触发后，姬子的速度提高 20%」");
    }

    /**
     * 星魂 2: the low-HP share is gated - at eidolon 0 it does not apply.
     */
    @Test
    public void eidolonTwoGateIsReal() {
        Assertions.assertEquals(0.0, lowHpShare(false), 1e-9, "0 星魂：那句不该生效");
        Assertions.assertTrue(lowHpShare(true) > 0, "2 星魂：生效");
    }

    /**
     * 行迹 道标: +15% crit rate at or above 80% HP - read by rolling just under the boosted rate.
     *
     * <p>Why not the panel: the share is an INSTANCE modifier, so the panel never moves, and the `CritArea` only differs
     * when the instance actually crits (measured: reading it under a 0.99 roll answered 1.0 for both cases, which says
     * nothing). Her base crit rate is 5%, so a 0.10 roll misses at 5% and hits at 20%.
     */
    @Test
    public void traceBeaconNeedsTheHpLine() {
        boolean full = critWith(new java.util.Random(0) {
            @Override
            public double nextDouble() {
                return 0.10;
            }
        }, false);
        boolean wounded = critWith(new java.util.Random(0) {
            @Override
            public double nextDouble() {
                return 0.10;
            }
        }, true);
        System.out.println("[1003] 道标: 满血暴击=" + full + " ; 掉到 80% 以下暴击=" + wounded);
        Assertions.assertTrue(full, "「若当前生命值百分比 ≥80%，暴击率提高 15%」: 0.10 应当在 5%+15% 之下命中");
        Assertions.assertFalse(wounded, "血线之下不加成: 0.10 应当落在 5% 之外");
    }

    /**
     * 星魂 3 / 5: the raises are filed on the per-slot bonus, which is what the damage readings resolve against.
     *
     * <p>Why not `skill.getLevel()`: that is the skill's own base level and never moves (the engine's `CanHit.skillLevel`
     * adds `skillLevelBonus(slot)` on top), so a judge reading it would answer 1 -> 1 for every character forever.
     */
    @Test
    public void eidolonThreeAndFiveRaiseTheLevels() {
        Character plain = CharacterFactory.create(HIMEKO, LEVEL);
        Character invested = CharacterFactory.create(HIMEKO, LEVEL, false, null, null, 5);
        new Battle(List.of(plain), List.of(BattleFixture.monster()), always(0.99)).startBattle();
        new Battle(List.of(invested), List.of(BattleFixture.monster()), always(0.99)).startBattle();

        int skillDelta = invested.skillLevelBonus(SkillType.SKILL) - plain.skillLevelBonus(SkillType.SKILL);
        int commonDelta = invested.skillLevelBonus(SkillType.COMMON) - plain.skillLevelBonus(SkillType.COMMON);
        int ultraDelta = invested.skillLevelBonus(SkillType.ULTRA) - plain.skillLevelBonus(SkillType.ULTRA);
        int talentDelta = invested.skillLevelBonus(SkillType.TALENT) - plain.skillLevelBonus(SkillType.TALENT);
        System.out.println("[1003] 等级加成增量: 战技 " + skillDelta + " ; 普攻 " + commonDelta
                + " ; 终结技 " + ultraDelta + " ; 天赋 " + talentDelta);
        Assertions.assertEquals(2, skillDelta, "星魂 3：战技等级 +2");
        Assertions.assertEquals(1, commonDelta, "星魂 3：普攻等级 +1");
        Assertions.assertEquals(2, ultraDelta, "星魂 5：终结技等级 +2");
        Assertions.assertEquals(2, talentDelta, "星魂 5：天赋等级 +2");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static Random always(double roll) {
        return new Random(0) {
            @Override
            public double nextDouble() {
                return roll;
            }
        };
    }

    private static boolean critWith(java.util.Random rolls, boolean wound) {
        Character her = CharacterFactory.create(HIMEKO, LEVEL);
        if (wound) {
            her.consumeHp(her.getMaxHp() * 0.3);
        }
        Enemy victim = BattleFixture.monster();
        Battle battle = new Battle(List.of(her), List.of(victim), rolls);
        battle.startBattle();
        java.util.List<com.laosun.aluminium.damage.Damage> settled = new java.util.ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(victim));
        battle.processRequests();
        return settled.get(0).isCrit();
    }

    private static double critAfterOneHit(boolean wound) {
        // 道标是伤害时的**实例修饰**，面板上永远看不到它 ✗ → 读那一发的 CritArea。
        Character her = CharacterFactory.create(HIMEKO, LEVEL);
        if (wound) {
            her.consumeHp(her.getMaxHp() * 0.3);
        }
        Enemy victim = BattleFixture.monster();
        Battle battle = new Battle(List.of(her), List.of(victim), always(0.99));
        battle.startBattle();
        java.util.List<com.laosun.aluminium.damage.Damage> settled = new java.util.ArrayList<>();
        battle.addDamageObserver((damage, applied, hpLoss, shieldAbsorbed, died) -> settled.add(damage));
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(victim));
        battle.processRequests();
        double area = settled.get(0).breakdown().get("CritArea");
        return Math.max(0.0, area - 1.0);
    }

    private static double lowHpShare(boolean withEidolonTwo) {
        Character her = CharacterFactory.create(HIMEKO, LEVEL, false, null, null, withEidolonTwo ? 2 : 0);
        Enemy victim = BattleFixture.monster();
        victim.setAttribute(AttributeType.HEALTH, new DoubleValue(900000));
        victim.heal(900000);
        victim.takeDamage(victim.getCurrentHp() * 0.6);
        Battle battle = new Battle(List.of(her), List.of(victim), always(0.99));
        battle.startBattle();
        double before = victim.getCurrentHp();
        battle.castImmediate(her.getSkills().get(SkillType.COMMON), her, List.of(victim));
        double dealt = before - victim.getCurrentHp();

        Character reference = CharacterFactory.create(HIMEKO, LEVEL);
        Enemy other = BattleFixture.monster();
        Battle clean = new Battle(List.of(reference), List.of(other), always(0.99));
        clean.startBattle();
        double cleanBefore = other.getCurrentHp();
        clean.castImmediate(reference.getSkills().get(SkillType.COMMON), reference, List.of(other));
        return (dealt / (cleanBefore - other.getCurrentHp())) - 1.0;
    }

    // ==================================================================
    // 4. The kill-energy credit that rides on her ultimate
    // ==================================================================

    /*
     * "每消灭1个敌方目标<b>额外</b>恢复姬子5点能量" - the kill credit that rides on <b>her ultimate</b>.
     *
     * <p><b>Why the reading needed settling first.</b> The engine already credits <b>any</b> killer 5 energy
     * ({@code Constant.ENERGY_GAIN_KILL}), and this sentence also says 5 - one credit or two? The document answers it: the
     * skill's own {@code param_list} states the 5 ({@code 100303} Lv10 = {@code [1.38, 5]}), the general credit is a rule
     * about the kill happening, and the text says "额外恢复" (it would read "恢复5点能量" if it were that same 5).
     * Hence +5 on top - and this section measures that total.
     *
     * <p>Note: <b>How the first version of this case went wrong, kept as a warning</b>: it left the enemy at 20% HP, which her
     * Lv1 ultimate (1.38  x  ATK ~= 966) cannot take off a 30000-HP monster - so <b>neither</b> run killed anything, the
     * difference was 0, and the "control" was not a control at all. The fixture now leaves the enemy at 1 HP and asserts
     * the precondition it depends on.
     */

    /**
     * A kill by her ultimate pays the general 5 AND her extra 5.
     */
    @Test
    public void theUltimatesKillPaysFiveOnTopOfTheGeneralCredit() {
        double killed = ultimateEnergy(true);
        double survived = ultimateEnergy(false);

        // Note: Measured: killed = 10, survived = 5. The difference is HER five and nothing else -- the
        // general kill credit is folded into the attack's own energy («one attack grants energy only once»,
        // Battle.grantHitAndKillEnergy), so "额外" really is an extra credit rather than a relabelling of the
        // standard one. That is the reading this case exists to pin.
        Assertions.assertEquals(5, killed - survived, 1.0,
                "「每消灭1个敌方目标额外恢复姬子5点能量」 (every time an enemy target is killed, Himeko restores 5 extra Energy) adds exactly 5 on top of the cast's own energy ("
                        + killed + " vs " + survived + ")");
    }

    /**
     * Note: A kill she did not cause pays her nothing: the general credit follows the killer, and her sentence names her
     * ultimate.
     */
    @Test
    public void aKillFromAnotherSourceDoesNotPayHerExtra() {
        Character hero = CharacterFactory.create(HIMEKO, LEVEL);
        Enemy enemy = weakEnemy();
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.startBattle();
        enemy.takeDamage(enemy.getMaxHp() - 1);
        double before = hero.getCurrentEnergy();

        Character ally = CharacterFactory.create(1002, LEVEL);
        ally.setTriggerTable(new TriggerTable(1002, List.of()));
        battle.applyDamage(enemy, new Damage(ally, enemy, DamageElement.FIRE, DamageType.NORMAL, 1_000,
                SkillCategory.NORMAL));

        Assertions.assertTrue(enemy.isDeath(), "precondition: the ally's hit killed it");
        Assertions.assertEquals(0, hero.getCurrentEnergy() - before, EPS,
                "Himeko (姬子) gains nothing from a kill she did not cause -- the general credit goes to the killer");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /**
     * Her ultimate against the same enemy, one hit away from death or at full HP.
     */
    private static double ultimateEnergy(boolean theEnemyDies) {
        Character hero = CharacterFactory.create(HIMEKO, LEVEL);
        Enemy enemy = weakEnemy();
        Battle battle = new Battle(List.of(hero), List.of(enemy), fixed());
        battle.startBattle();
        if (theEnemyDies) {
            enemy.takeDamage(enemy.getMaxHp() - 1);
        }
        double before = hero.getCurrentEnergy();

        battle.castImmediate(new DefaultSkill(HIMEKO, ULTIMATE_SLOT, 1), hero, List.of(enemy));

        Assertions.assertEquals(theEnemyDies, enemy.isDeath(),
                "precondition: the fixture really is the case it claims to be");
        return hero.getCurrentEnergy() - before;
    }

    private static Enemy weakEnemy() {
        Enemy enemy = BattleFixture.monster();
        enemy.setStanceWeak(Set.of(DamageElement.FIRE));
        return enemy;
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }
}
