package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 姬子 (1003) — the first character whose kit is built on a <b>declared resource</b>.
 *
 * <p><b>Why her.</b> Her document is the corpus's most common shape that had no spelling: 「获得充能，上限3点」,
 * 「若充能达到上限则…」, 「消耗全部充能」. The ops to write such a resource existed since P8-8, but nothing could
 * <em>read</em> one back, and nothing could declare one at all — 41 of the 97 character documents gate something on
 * a count, which is the largest single hole the corpus scan found (ROADMAP §13.7). Her three rules are the reader
 * that pays for both capabilities: {@code self_resource:<NAME>} in the condition DSL, and the {@code resources}
 * block that says what the cap is.
 *
 * <p>⚠ What is <b>not</b> authored, and the capability each clause would need, is listed in
 * {@code characters/1003.json}'s note — 星魂 4's 「施放战技…击破时」 and the ultimate's per-kill energy both need to
 * know <em>which ability</em> caused the event, which no event carries.
 */
public class HimekoChargeTest {
    private static final double EPS = 1e-6;

    /** 姬子 herself. */
    private static final int HIMEKO = 1003;
    /**
     * An ordinary character with no rules of their own: the ally who attacks, so nothing but her own table can
     * react ({@link TestCharacters}).
     */
    private static final int ALLY = TestCharacters.withoutTriggerFile();
    private static final int LEVEL = 80;
    /** 冰刃 (Ice Edge) @90: Fire-weak with 60 toughness, so two Fire basic attacks break it. */
    private static final int ICE_EDGE = 1002011;
    /** The resource her Talent declares. */
    private static final String CHARGE = "充能";

    // ==================================================================
    // The three clauses of 天赋「乘胜追击」
    // ==================================================================

    /** 「战斗开始时获得1点充能。」 */
    @Test
    public void theBattleStartsHerAtOneCharge() {
        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        Assertions.assertEquals(0, himeko.getResources().value(CHARGE),
                "precondition: the declaration alone starts her at 0 -- the sentence is a rule, not an initial value");

        Battle battle = new Battle(List.of(himeko), List.of(dummy()), new Random(0));
        battle.startBattle();

        Assertions.assertEquals(1, himeko.getResources().value(CHARGE), "「战斗开始时获得1点充能」");
    }

    /**
     * 「当有敌方目标的弱点被击破时，姬子获得充能，上限3点。」
     *
     * <p>Driven through a <b>real</b> weakness break, not by firing the event: two of her Fire basic attacks empty
     * Ice Edge's 60 toughness, and the rule has to fire from inside the engine's own break path.
     */
    @Test
    public void abreakChargesHerAndTheCapHolds() {
        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        Enemy iceEdge = dummy();
        Battle battle = new Battle(List.of(himeko), List.of(iceEdge), new Random(0));
        battle.startBattle();

        battle.castImmediate(new DefaultSkill(HIMEKO, 1, 1), himeko, List.of(iceEdge));
        Assertions.assertFalse(iceEdge.isBroken(), "precondition: one basic attack does not empty the bar");
        Assertions.assertEquals(1, himeko.getResources().value(CHARGE), "and no break means no charge");

        battle.castImmediate(new DefaultSkill(HIMEKO, 1, 1), himeko, List.of(iceEdge));
        Assertions.assertTrue(iceEdge.isBroken(), "the second one breaks it");
        Assertions.assertEquals(2, himeko.getResources().value(CHARGE),
                "BATTLE_START's 1 plus the break's 1 -- 「当有敌方目标的弱点被击破时，姬子获得充能」");

        // Two more breaks: the third point lands, the fourth is swallowed by the cap (overflow is 0 by default,
        // so 「上限3点」 is enforced by the declaration rather than by arithmetic in the rule).
        battle.fireTriggers(TriggerEvent.BREAK, himeko, iceEdge, 0, 0);
        Assertions.assertEquals(3, himeko.getResources().value(CHARGE), "the cap is reached");
        battle.fireTriggers(TriggerEvent.BREAK, himeko, iceEdge, 0, 0);
        Assertions.assertEquals(3, himeko.getResources().value(CHARGE), "and cannot be passed");
    }

    /**
     * 「当我方目标施放攻击后，若姬子的充能达到上限则立即发动1次追加攻击，对敌方全体目标造成…伤害，并消耗全部充能。」
     *
     * <p>A real ally attack drives it, and the claim is about <b>both</b> enemies: 「敌方全体」 is the thing the
     * selector had to learn, and the second enemy is the witness — the ally aims at the first one only, so
     * anything the second one loses came from her follow-up.
     */
    @Test
    public void atTheCapAnAllyAttackFiresHerFollowUpOnEveryEnemyAndSpendsItAll() {
        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy first = dummy();
        Enemy second = dummy();
        Battle battle = new Battle(List.of(himeko, ally), List.of(first, second), new Random(0));
        battle.startBattle();
        chargeTo(himeko, first, battle);
        Assertions.assertEquals(3, himeko.getResources().value(CHARGE), "precondition: she is at the cap");

        double firstBefore = first.getCurrentHp();
        double secondBefore = second.getCurrentHp();
        double ourSideBefore = ourSideHp(battle);

        battle.castImmediate(new DefaultSkill(ALLY, 1, 1), ally, List.of(first));

        Assertions.assertTrue(first.getCurrentHp() < firstBefore, "the first enemy was hit");
        Assertions.assertTrue(second.getCurrentHp() < secondBefore,
                "「对敌方全体目标」 -- and so was the one the ally never aimed at");
        Assertions.assertEquals(ourSideBefore, ourSideHp(battle), EPS, "on the enemies, not on our side");
        Assertions.assertEquals(0, himeko.getResources().value(CHARGE), "「并消耗全部充能」");
    }

    /** Below the cap nothing happens — the same attack, one point short. */
    @Test
    public void onePointShortOfTheCapNothingHappens() {
        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy first = dummy();
        Enemy second = dummy();
        Battle battle = new Battle(List.of(himeko, ally), List.of(first, second), new Random(0));
        battle.startBattle();
        chargeTo(himeko, first, battle);
        himeko.getResources().spend(CHARGE, 1);
        Assertions.assertEquals(2, himeko.getResources().value(CHARGE), "precondition: 2 of 3");

        double secondBefore = second.getCurrentHp();
        battle.castImmediate(new DefaultSkill(ALLY, 1, 1), ally, List.of(first));

        Assertions.assertEquals(secondBefore, second.getCurrentHp(), EPS, "「若…达到上限」 is not met");
        Assertions.assertEquals(2, himeko.getResources().value(CHARGE), "and nothing was spent");
    }

    /**
     * The follow-up's damage is the <b>Talent's own Lv10 row</b>, not a literal written into the rule.
     *
     * <p>Asserted against the engine's own settlement of the same base rather than against a recorded number: with
     * crit switched off (so no draw is taken and the value is deterministic) the rule's swing and a hand-made
     * {@code applyAdditionalDamage} of {@code row × ATTACK} must agree <b>exactly</b> — which is the whole claim of
     * {@code skill} + {@code damage_param} + {@code damage_level}. A rule that spelled 1.4 as a literal would pass
     * this too; a rule that read the Lv1 row (0.7) or column 1 (the cap, 3.0) would not.
     */
    @Test
    public void theFollowUpDealsTheTalentRowsLv10Damage() {
        double row = talentRow(HIMEKO, LEVEL_ROW, 0);
        Assertions.assertEquals(1.4, row, EPS,
                "the number the document quotes: 「等同于姬子140%攻击力」 is 100304's Lv10, column 0");

        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        // Crit off: the pipeline only draws for a crit when the rate is above 0, so every number below is exact.
        himeko.setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        Enemy first = dummy();
        Enemy second = dummy();
        Battle battle = new Battle(List.of(himeko), List.of(first, second), new Random(0));
        battle.startBattle();
        chargeTo(himeko, first, battle);

        double secondBefore = second.getCurrentHp();
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, himeko, null, 1, 0);
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

    /** The follow-up is not an attack that can trigger itself: at 0 charge the next ally attack does nothing. */
    @Test
    public void theFollowUpCannotRetriggerItself() {
        Character himeko = CharacterFactory.create(HIMEKO, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy first = dummy();
        Enemy second = dummy();
        Battle battle = new Battle(List.of(himeko, ally), List.of(first, second), new Random(0));
        battle.startBattle();
        chargeTo(himeko, first, battle);

        Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, null, 1, 0),
                "the rule fires once");
        double secondBefore = second.getCurrentHp();
        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.ALLY_ATTACK, ally, null, 1, 0),
                "the charge is gone, so nothing fires again");
        Assertions.assertEquals(secondBefore, second.getCurrentHp(), EPS);
    }

    // ==================================================================
    // What is deliberately NOT in the file
    // ==================================================================

    /**
     * The rest of her kit is <b>registered, not approximated</b>.
     *
     * <p>Four clauses exist and each is pinned above; the counts here are what says nothing else was written. The one
     * missing clause would be a wrong number if it were spelled with the vocabulary that exists: the ultimate's
     * 「每消灭1个敌方目标额外恢复5点能量」 needs to know the kill came from <b>that</b> ultimate — and while the
     * attribution now exists ({@code from_skill}, added for 星魂 4 on 2026-09-28), what is still unresolved is a
     * <b>data</b> question: the engine's own rule already credits 5 energy to the killer, and the sentence says
     * 「**额外**恢复5点」 — whether those are the same 5 has to be settled against the game's numbers, because guessing
     * it is a silent ±5 energy (registered as {@code M-45}).
     */
    @Test
    public void theRestOfHerKitIsNotAuthored() {
        TriggerTable table = TriggerTables.of(HIMEKO);
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.BATTLE_START), "「战斗开始时获得1点充能」");
        Assertions.assertEquals(2, table.ruleCount(TriggerEvent.BREAK),
                "「当有敌方目标的弱点被击破时」 (anybody's break) and 星魂 4's 「施放战技…造成弱点击破时」");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.ALLY_ATTACK), "「当我方目标施放攻击后」");
        Assertions.assertEquals(0, table.ruleCount(TriggerEvent.ULT_CAST), "her ultimate is the engine's ordinary path");
        Assertions.assertEquals(1, table.ruleCount(TriggerEvent.KILL),
                "「每消灭1个敌方目标额外恢复姬子5点能量」 -- shipped on 2026-09-28 once 「额外」 was read against the "
                        + "engine's general kill credit (HimekoKillEnergyTest)");
        Assertions.assertEquals(0, table.ruleCount(TriggerEvent.SKILL_CAST),
                "星魂 4's charge rides on BREAK with `from_skill SKILL`, not on a cast event of its own");
        Assertions.assertEquals(1, table.resources().size(), "and she declares exactly one resource");
        Assertions.assertEquals(CHARGE, table.referencedResources().iterator().next(),
                "every resource a rule names is the one she declares -- checked when she is built");
    }

    // ==================================================================
    // helpers
    // ==================================================================

    /** The row the document quotes for a maxed ability (its param tables are 1-based). */
    private static final int LEVEL_ROW = 10;

    /** One entry of a skill's parameter row, as the interpreter reads it. */
    private static double talentRow(int cid, int level, int column) {
        Character character = CharacterFactory.create(cid, LEVEL);
        return character.getSkills().get(SkillType.TALENT).getData().getSkills().get(level - 1).get(column);
    }

    /** Grants charge through her own BREAK rule until it reaches the cap. */
    private static void chargeTo(Character himeko, Enemy enemy, Battle battle) {
        while (himeko.getResources().value(CHARGE) < 3) {
            Assertions.assertEquals(1, battle.fireTriggers(TriggerEvent.BREAK, himeko, enemy, 0, 0),
                    "her break rule must fire for an enemy break");
        }
    }

    private static double ourSideHp(Battle battle) {
        return battle.allies.stream().mapToDouble(CanHit::getCurrentHp).sum();
    }

    private static Enemy dummy() {
        return EnemyFactory.create(ICE_EDGE, 90, 1);
    }
}
