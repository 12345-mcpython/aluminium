package com.laosun.aluminium.test.content.characters.cerydra;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.buff.StateBuff;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.trigger.TriggerPayload;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 1412 Cerydra (刻律德菈): the Military Merit (军功) mark - the Skill that plants it and the ATTACK share it carries, the
 * Charge the mark feeds, the technique that grants it, the Ultimate that hands it out, and Eidolons 2 and 6, the two ranks
 * that pay the mark off.
 *
 * <p>This file merges the former {@code CerydraTest}, {@code CerydraTechniqueTest}, {@code CerydraUltimateSelectionTest},
 * {@code CerydraEidolonTwoTest} and {@code CerydraEidolonSixTest}: every case, every assertion and every helper moved
 * unchanged, and each former class note now sits on the case it explains. The 爵位 (Peerage) tier the mark upgrades into
 * is {@link CerydraPeerageTest}.
 *
 * <p>L3 consolidation: cases that share one fixture, one clause and one observed object are now one scenario test, and
 * their assertions sit inside {@link Assertions#assertAll} with a message naming the clause. Nothing was dropped: every
 * expected value, tolerance and message is the one the separate case carried, and a rejection path was never merged
 * with a positive one.
 */
public class CerydraTest {
    private static final int CERYDRA = 1412;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;

    // ==================================================================
    // The Skill: the mark on the ally, her Charge, and the ATTACK share
    // ==================================================================

    /**
     * Note: The mark's own claims: the state on the ally, her +1 Charge (充能), and the ATTACK share = 24% of HER attack (a derived, absolute number).
     */
    @Test
    public void theSkillMarksTheAllyAndSharesHerAttack() {
        Character cerydra = CharacterFactory.create(CERYDRA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(cerydra, ally), List.of(enemy), fixed());
        battle.startBattle();
        double allyAttackBefore = ally.getAttribute(AttributeType.ATTACK).get();
        int chargeBefore = cerydra.getResources().get("充能").getValue();
        double expected = cerydra.getAttribute(AttributeType.ATTACK).get() * 0.24;

        battle.castImmediate(cerydra.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), cerydra, List.of(ally));

        double attackGain = ally.getAttribute(AttributeType.ATTACK).get() - allyAttackBefore;
        int chargeAfter = cerydra.getResources().get("充能").getValue();
        Assertions.assertAll("「使指定我方单体角色获得【军功】」: the mark, the ATTACK share and the Charge",
                () -> Assertions.assertTrue(ally.getBuffManager().hasState("军功"),
                        "「使指定我方单体角色获得【军功】」 (grants a designated single ally Military Merit (【军功】))"),
                () -> Assertions.assertEquals(expected, attackGain, expected * 0.02,
                        "「提高数值等同于刻律德菈攻击力的24.00%」 (raises by an amount equal to 24.00% of Cerydra's ATK): expected " + expected),
                () -> Assertions.assertEquals(chargeBefore + 1, chargeAfter,
                        "「并使刻律德菈获得1点充能」 (and Cerydra (刻律德菈) gains 1 point of Charge)"));
    }

    /**
     * Note: The mark's reaction: a marked ally's attack grants Charge AND draws her 60% additional damage; an unmarked one does neither.
     */
    @Test
    public void theMarkDrivesBothTheChargeAndTheAdditionalDamage() {
        double unmarked = markedAttackLoss(false);
        double marked = markedAttackLoss(true);

        Assertions.assertAll("「持有【军功】的角色施放攻击后」: the mark's reaction",
                () -> Assertions.assertEquals(0.0, unmarked, 1e-9,
                        "「持有【军功】的角色施放攻击后」 (after a character holding Military Merit casts an attack) -- without the mark, nothing"),
                () -> Assertions.assertTrue(marked > 0, "with the mark, the additional damage lands: " + marked));
    }

    // ==================================================================
    // The technique: 【军功】 on entry
    // ==================================================================

    /**
     * 1412 刻律德菈's technique:「使用秘技后获得【军功】」- the technique state at battle start hands her the merit.
     *
     * <p>The mapping-side halves (transfer on switching, the free Skill cast) are registered as gaps, not tested here.
     */
    @Test
    public void theTechniqueGrantsMeritOnEntry() {
        boolean withTechnique = merit(true);
        boolean withoutTechnique = merit(false);
        Assertions.assertAll("「使用秘技后获得【军功】」: the technique hands her the merit, and only it does",
                () -> Assertions.assertTrue(withTechnique, "「使用秘技后获得【军功】」"),
                () -> Assertions.assertFalse(withoutTechnique, "the control: without the technique there is no merit"));
    }

    // ==================================================================
    // The Ultimate: who gets the mark when nobody holds it
    // ==================================================================

    /**
     * 1412 刻律德菈's Ultimate:「若场上不存在持有【军功】的角色，优先使当前队伍中的第一位角色获得【军功】」.
     *
     * <p>Both directions of the one sentence: with nobody holding it the Ultimate hands it to another ally; with a holder
     * already there, the Ultimate leaves things alone. ⚠ It targets {@code next_ally}, not {@code party_first}: the latter
     * picks Cerydra herself, and measured, that pushed {@code OdeToLawChargeTest}'s charge from 6 to 8.
     */
    @Test
    public void theUltimateGivesTheMeritOnlyWhenNobodyHasIt() {
        Character her = CharacterFactory.create(1412, 80);
        Character ally = CharacterFactory.create(1101, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(her, ally)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        her.setCurrentEnergy(her.getMaxEnergy());
        battle.castImmediate(her.getSkills().get(SkillType.ULTRA), her, List.of(battle.enemyUnits().getFirst()));
        battle.processRequests();
        System.out.println("[1412] ult: ally merit=" + ally.getBuffManager().hasState("军功")
                + " ; her merit=" + her.getBuffManager().hasState("军功"));
        boolean allyGotIt = ally.getBuffManager().hasState("军功");

        Character holder = CharacterFactory.create(1412, 80);
        Character marked = CharacterFactory.create(1101, 80);
        Character other = CharacterFactory.create(1102, 80);
        marked.getBuffManager().addBuff(new StateBuff("军功", 1));
        Battle withHolder = new Battle(new ArrayList<>(List.of(holder, marked, other)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        withHolder.startBattle();
        holder.setCurrentEnergy(holder.getMaxEnergy());
        withHolder.castImmediate(holder.getSkills().get(SkillType.ULTRA), holder,
                List.of(withHolder.enemyUnits().getFirst()));
        withHolder.processRequests();

        Assertions.assertAll("「若场上不存在持有【军功】的角色…」",
                () -> Assertions.assertTrue(allyGotIt, "「使…第一位角色获得【军功】」"),
                () -> Assertions.assertTrue(marked.getBuffManager().hasState("军功"), "the existing holder keeps it"),
                () -> Assertions.assertFalse(other.getBuffManager().hasState("军功"), "「若场上不存在…」: nobody else gets one"));
    }

    // ==================================================================
    // Eidolon 2: 「持有【军功】的角色造成的伤害提高 40%」 and
    // 「场上存在持有【军功】的队友时，刻律德菈造成的伤害提高 160%」
    // ==================================================================

    /**
     * 1412 刻律德菈's Eidolon 2, the second half:「场上存在持有【军功】的队友时，刻律德菈造成的伤害提高 160%」, with its
     * control: below the rank the sentence does not exist.
     *
     * <p>It needs the new {@code any_ally has_state} condition: the sentence asks a question about the party, and there is no
     * second unit to act on. The judge gives the state to another ally before the battle starts, because the condition is read
     * once at {@code BATTLE_START}.
     */
    @Test
    public void anAllyHoldingMeritRaisesHerDamage() {
        double with = boost(true);
        double without = boost(false);
        double belowRank = boostAt(1);
        System.out.println("[1412] E2 damage boost: ally has merit " + with + " ; nobody does " + without);
        Assertions.assertAll("「场上存在持有【军功】的队友时，刻律德菈造成的伤害提高 160%」",
                () -> Assertions.assertEquals(1.6, with - without, 1e-9, "「提高 160%」"),
                () -> Assertions.assertEquals(0.0, belowRank, 1e-9, "the control: nothing at Eidolon 1"));
    }

    /**
     * Eidolon 2's FIRST half:「持有【军功】的角色造成的伤害提高 40%」- the boost follows the HOLDER, so a second cast on
     * another ally must move it, not add a second one.
     */
    @Test
    public void theMeritHolderItselfDealsMoreAndTheBoostMoves() {
        Character her = CharacterFactory.create(1412, 80, true, null, null, 2);
        Character first = CharacterFactory.create(1101, 80);
        Character second = CharacterFactory.create(1102, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(her, first, second)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(her.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), her, List.of(first));
        battle.processRequests();
        double firstAfterFirstCast = first.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        System.out.println("[1412] first holder " + firstAfterFirstCast + " ; second " + second.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
        battle.castImmediate(her.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), her, List.of(second));
        battle.processRequests();
        System.out.println("[1412] moved: first " + first.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get()
                + " ; second " + second.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get());
        double firstAfterMove = first.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        double secondAfterMove = second.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
        Assertions.assertAll("「持有【军功】的角色造成的伤害提高 40%」: the boost belongs to the holder",
                () -> Assertions.assertEquals(0.4, firstAfterFirstCast, 1e-9, "「持有【军功】的角色造成的伤害提高 40%」"),
                () -> Assertions.assertEquals(0.0, firstAfterMove, 1e-9,
                        "the boost MOVES with the merit, it does not stay behind"),
                () -> Assertions.assertEquals(0.4, secondAfterMove, 1e-9, "and the new holder carries it"));
    }

    // ==================================================================
    // Eidolon 6: the two penetration halves
    // ==================================================================

    /**
     * 1412 刻律德菈's Eidolon 6, the first half:「持有【军功】的角色全属性抗性穿透提高 20%」 - the bonus belongs to the
     * holder, not to the ally first marked, so it moves with the merit.
     */
    @Test
    public void theMeritHolderPiercesMoreAndTheBonusMoves() {
        Character her = CharacterFactory.create(1412, 80, true, null, null, 6);
        Character first = CharacterFactory.create(1101, 80);
        Character second = CharacterFactory.create(1102, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(her, first, second)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(her.getSkills().get(SkillType.SKILL), her, List.of(first));
        battle.processRequests();
        double firstAfterFirstCast = pen(first);
        System.out.println("[1412] E6 pen: first " + firstAfterFirstCast + " ; second " + pen(second));
        battle.castImmediate(her.getSkills().get(SkillType.SKILL), her, List.of(second));
        battle.processRequests();
        double firstAfterMove = pen(first);
        double secondAfterMove = pen(second);
        Assertions.assertAll("「持有【军功】的角色全属性抗性穿透提高 20%」: the penetration belongs to the holder",
                () -> Assertions.assertEquals(0.2, firstAfterFirstCast, 1e-9, "「持有【军功】的角色全属性抗性穿透提高 20%」"),
                () -> Assertions.assertEquals(0.0, firstAfterMove, 1e-9, "the penetration moves with the merit"),
                () -> Assertions.assertEquals(0.2, secondAfterMove, 1e-9, "and the new holder carries it"));
    }

    /**
     * 1412 刻律德菈's Eidolon 6, the second half:「场上存在持有【军功】的队友时，刻律德菈全属性抗性穿透提高 20%」- read as
     * the jump her own sheet takes once an ally holds the mark.
     */
    @Test
    public void shePiercesMoreWhileAnAllyHoldsMerit() {
        Character her = CharacterFactory.create(1412, 80, true, null, null, 6);
        Character ally = CharacterFactory.create(1101, 80);
        Battle battle = new Battle(new ArrayList<>(List.of(her, ally)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.castImmediate(her.getSkills().get(SkillType.SKILL), her, List.of(ally));
        battle.processRequests();
        double before = pen(her);
        battle.fire(TriggerPayload.Simple.of(TriggerEvent.TURN_START, her, her, 0, 0));
        double after = pen(her);
        System.out.println("[1412] E6 self pen " + before + " -> " + after);
        Assertions.assertEquals(0.2, after - before, 1e-9, "「场上存在持有【军功】的队友时…提高 20%」");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Fires the ally's attack, optionally after marking them, and returns the enemy's HP loss from HER additional damage only.
     */
    private static double markedAttackLoss(boolean mark) {
        Character cerydra = CharacterFactory.create(CERYDRA, LEVEL);
        Character ally = CharacterFactory.create(ALLY, LEVEL);
        Enemy enemy = BattleFixture.monster();
        Battle battle = new Battle(List.of(cerydra, ally), List.of(enemy), fixed());
        battle.startBattle();
        if (mark) {
            battle.castImmediate(cerydra.getSkills().get(com.laosun.aluminium.enums.SkillType.SKILL), cerydra, List.of(ally));
        }
        double before = enemy.getCurrentHp();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, ally, enemy, 0, 0));
        return before - enemy.getCurrentHp();
    }

    private static Random fixed() {
        return BattleFixture.alwaysRoll(0.0);
    }

    /**
     * Whether the battle-start state carries 【军功】, with or without the technique declared.
     */
    private static boolean merit(boolean technique) {
        Character her = CharacterFactory.create(1412, 80);
        if (technique) {
            her.getBuffManager().addBuff(new StateBuff("sys:technique", 1));
        }
        Battle battle = new Battle(new ArrayList<>(List.of(her)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        System.out.println("[1412] technique=" + technique + " merit=" + her.getBuffManager().hasState("军功"));
        return her.getBuffManager().hasState("军功");
    }

    /**
     * Eidolon 2's boost at the rank the sentence belongs to; {@code allyHasMerit} is the same scene with the mark taken off.
     */
    private static double boost(boolean allyHasMerit) {
        return boostAt(2, allyHasMerit);
    }

    private static double boostAt(int eidolon) {
        return boostAt(eidolon, true);
    }

    private static double boostAt(int eidolon, boolean allyHasMerit) {
        Character her = CharacterFactory.create(1412, 80, true, null, null, eidolon);
        Character ally = CharacterFactory.create(1101, 80);
        if (allyHasMerit) {
            ally.getBuffManager().addBuff(new StateBuff("军功", 1));
        }
        Battle battle = new Battle(new ArrayList<>(List.of(her, ally)),
                new ArrayList<>(List.of(BattleFixture.dummy())), BattleFixture.alwaysRoll(0.0));
        battle.startBattle();
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.TURN_START, her, her, 0, 0));
        return her.getAttribute(AttributeType.ALL_DAMAGE_TYPE_BOOST).get();
    }

    /**
     * A unit's All-Type RES PEN, i.e. the zone Eidolon 6 raises.
     */
    private static double pen(Character unit) {
        return unit.getAttribute(AttributeType.RESISTANCE_REDUCTION).get();
    }
}
