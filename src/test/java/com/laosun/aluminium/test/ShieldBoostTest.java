package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.buff.ShieldBuff;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.skill.DefaultSkill;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * "increases the shield amount provided by <b>the wearer</b> by X%" - relic 103 (20%) / 128 (10% + 12%) and one light cone, four readers.
 *
 * <p><b>Why the vocabulary needed it.</b> {@code Battle.grantShield} used to install exactly the number the rule
 * computed, and the class register had carried these three abilities for days with the same sentence: <i>"Needs an op
 * that scales every shield the wearer creates"</i>. That is not an op - it is a <b>stat</b>, and the game states it as
 * one: "the shield amount provided by <b>the wearer</b>" names the <b>giver</b>, so the number belongs to whoever creates the shield and
 * travels with it. Hence {@link AttributeType#SHIELD_BOOST}, read at grant time from the provider.
 *
 * <p><b>The three claims this file pins.</b>
 * <ol>
 *   <li>the amount is multiplied by {@code 1 + provider's boost} - 20% turns 100 into 120;</li>
 *   <li>it is the <b>giver's</b> number: a receiver carrying a boost of their own changes nothing, which is the whole
 *       difference between this and a damage-taken modifier;</li>
 *   <li>both shield paths agree - a raw grant and a timed {@code ShieldBuff} install the <b>same</b> value, so a
 *       shield does not silently shrink when its duration is what put it on the field.</li>
 * </ol>
 */
public class ShieldBoostTest {
    private static final double EPS = 1e-9;

    private static final int WEARER = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    /** The register's own example: relic 103's 4-piece, "increases the shield amount provided by the wearer by 20%". */
    @Test
    public void theProvidersBoostMultipliesTheShield() {
        Battle battle = fixture();
        Character giver = battle.characters.getFirst();
        giver.setAttribute(AttributeType.SHIELD_BOOST, new DoubleValue(0.2));

        double installed = battle.grantShield(giver, giver, 100);

        Assertions.assertEquals(120, installed, EPS);
        Assertions.assertEquals(120, giver.getShield(), EPS, "and that is what the damage path will drain");
    }

    /**
     * Note: The boost belongs to the <b>giver</b>, not to the one being shielded.
     *
     * <p>This is the case that separates "the shield amount provided" from every damage/healing modifier in the engine: a
     * strengthened <i>receiver</i> must change nothing at all, or the same 20% would quietly apply to shields handed
     * out by teammates and by light cones nobody is wearing.
     */
    @Test
    public void aBoostOnTheReceiverChangesNothing() {
        Battle battle = fixture();
        Character giver = battle.characters.getFirst();
        Character shielded = battle.characters.getLast();
        shielded.setAttribute(AttributeType.SHIELD_BOOST, new DoubleValue(0.5));

        double installed = battle.grantShield(giver, shielded, 100);

        Assertions.assertEquals(100, installed, EPS, "the giver states no boost, so the shield is what was asked for");
        Assertions.assertEquals(100, shielded.getShield(), EPS);
    }

    /** No provider means no boost: the raw entry point the fixtures and demos use stays exactly as it was. */
    @Test
    public void anUnknownProviderAppliesNoBoost() {
        Battle battle = fixture();
        Character shielded = battle.characters.getFirst();
        shielded.setAttribute(AttributeType.SHIELD_BOOST, new DoubleValue(0.5));

        Assertions.assertEquals(100, battle.grantShield(shielded, 100), EPS,
                "a boost that cannot be attributed to a giver must not strengthen the shield");
    }

    /**
     * Two statements about one stat <b>add</b>, because both are contributions to one quantity.
     *
     * <p>relic 103's 20% and 128's 10% cannot be worn together (one relic per slot), but a light cone's "the shield amount provided is increased
     * by 12%" can sit next to a set bonus, so the arithmetic has to be addition rather than "the strongest wins".
     *
     * <p>Note: The modifiers are <b>pure</b> ones, which is not a detail of the fixture: SHIELD_BOOST is a ratio
     * attribute, and the engine's own {@code MODIFY_ATTR} gives a ratio attribute a flat modifier rather than an
     * additive percentage ({@code statModifier}: {@code attribute.isPercent ? "pure" : "add_percent"}), because a
     * percentage of a zero base is zero. Writing this case with {@code addPercent} would have "proved" that two
     * boosts cancel out - and it did, until it was measured.
     */
    @Test
    public void twoBoostsOnTheSameProviderAdd() {
        Battle battle = fixture();
        Character giver = battle.characters.getFirst();
        giver.getAttribute(AttributeType.SHIELD_BOOST)
                .addModifier(DoubleValue.Modifier.pure(0.1, DoubleValue.Modifier.ModifierSource.BUFF));
        giver.getAttribute(AttributeType.SHIELD_BOOST)
                .addModifier(DoubleValue.Modifier.pure(0.12, DoubleValue.Modifier.ModifierSource.BUFF));

        Assertions.assertEquals(122, battle.grantShield(giver, giver, 100), EPS);
    }

    /** A boost cannot conjure a shield: 0 stays 0, and the "<= 0 clears it" reading is unchanged. */
    @Test
    public void aBoostDoesNotCreateOrUnclearAShield() {
        Battle battle = fixture();
        Character giver = battle.characters.getFirst();
        giver.setAttribute(AttributeType.SHIELD_BOOST, new DoubleValue(0.2));

        Assertions.assertEquals(0, battle.grantShield(giver, giver, 0), EPS);
        battle.grantShield(giver, giver, 100);
        Assertions.assertEquals(0, battle.grantShield(giver, giver, -50), EPS, "≤ 0 still clears, not −60");
        Assertions.assertEquals(0, giver.getShield(), EPS);
    }

    /**
     * Note: <b>Both paths, one number.</b> The {@code SHIELD} op reaches the field in two shapes - a raw grant when the
     * rule states no {@code turns}, and a timed {@link ShieldBuff} when it does - and a boost that only applied to
     * one of them would make March 7th (三月七)'s "lasting 3 turns" shield 120 for three turns and 100 forever after.
     */
    @Test
    public void theTimedShieldInstallsTheSameBoostedNumber() {
        Battle battle = fixture();
        Character giver = battle.characters.getFirst();
        Character shielded = battle.characters.getLast();
        giver.setAttribute(AttributeType.SHIELD_BOOST, new DoubleValue(0.2));
        giver.setTriggerTable(new TriggerTable(WEARER,
                List.of(TriggerSpecs.rule("ALLY_ATTACK", null, shieldWithTurns(100, 3)))));

        battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.ALLY_ATTACK, giver, shielded, 1, 0);

        ShieldBuff buff = shielded.getBuffManager().allBuffsOf(ShieldBuff.class).getFirst();
        Assertions.assertEquals(120, buff.getAmount(), EPS, "the buff's amount is the boosted one");
        Assertions.assertEquals(120, shielded.getShield(), EPS, "…and that is what it installed");
    }

    /** …and the untimed shape of the same rule agrees, through the op's own call site rather than the API directly. */
    @Test
    public void theRawGrantFromARuleIsBoostedToo() {
        Battle battle = fixture();
        Character giver = battle.characters.getFirst();
        Character shielded = battle.characters.getLast();
        giver.setAttribute(AttributeType.SHIELD_BOOST, new DoubleValue(0.2));
        giver.setTriggerTable(new TriggerTable(WEARER,
                List.of(TriggerSpecs.rule("ALLY_ATTACK", null, shield(100)))));

        battle.fireTriggers(com.laosun.aluminium.enums.TriggerEvent.ALLY_ATTACK, giver, shielded, 1, 0);

        Assertions.assertEquals(120, shielded.getShield(), EPS);
        Assertions.assertEquals(0, shielded.getBuffManager().allBuffsOf(ShieldBuff.class).size(),
                "no turns stated, so no buff — the number is the whole effect");
    }

    /**
     * The shipped content, read back through the loader: relic 103's 4-piece is 20% on its <b>wearer</b>.
     *
     * <p>Note: This is the assertion the register could not make before: the ability was registered for days as
     * "not expressible", and a file that loads is not the same claim as a file whose number is right. It also pins
     * the <b>side</b>: the rule must land on the wearer (self), because "provided by the wearer" shields are the wearer's own.
     *
     * <p>Note: The number is read <b>after</b> {@code startBattle()}, and that is not a detail of the fixture: the bonus is
     * an ability rule, not a {@code properties} stat, so it is applied by the {@code BATTLE_START} rule it was written
     * as. Reading the sheet of a character who has not entered a battle would read 0 and "prove" the file wrong.
     */
    @Test
    public void theKnightsFourthPieceBoostsTheWearersOwnShields() {
        Character wearer = wearing(RELIC_SET_103);
        Battle battle = battleWith(List.of(wearer));

        Assertions.assertEquals(0.2, wearer.getAttribute(AttributeType.SHIELD_BOOST).get(), EPS,
                "「increases the shield amount provided by the wearer by 20%」");
        Assertions.assertEquals(120, battle.grantShield(wearer, wearer, 100), EPS,
                "…and a shield that wearer grants really is a fifth larger");
    }

    /** 128's two tiers end up at 22% for a full set (10% + 12%), and each piece count states its own number. */
    @Test
    public void theReclusesTwoTiersAddUp() {
        Character full = wearing(RELIC_SET_128);
        Character twoPiece = wearingTwoPieces(RELIC_SET_128);

        Assertions.assertEquals(0.22, full.getAttribute(AttributeType.SHIELD_BOOST).get(), EPS,
                "the 4-piece sentence is +12% ON TOP of the 2-piece's 10% (see the file's note for why it is "
                        + "written as the accumulated total)");
        Assertions.assertEquals(0.1, twoPiece.getAttribute(AttributeType.SHIELD_BOOST).get(), EPS,
                "the 2-piece alone is 10%, not a share of the 22%");
    }

    /**
     * Note: A set the wearer's shield does not come from changes nothing.
     *
     * <p>relic 103's bonus is about <b>the wearer's own</b> shields only, so a teammate wearing 103 who shields
     * somebody else must be the one who gets the boost - and a wearer who shields nobody must not have the attribute
     * leak into shields granted by their teammates.
     */
    @Test
    public void theSetBonusStaysWithTheUnitThatOwnsIt() {
        Character knight = wearing(RELIC_SET_103);
        Character teammate = CharacterFactory.create(WEARER, LEVEL);
        Battle battle = battleWith(List.of(knight, teammate));

        Assertions.assertEquals(0.2, knight.getAttribute(AttributeType.SHIELD_BOOST).get(), EPS);
        Assertions.assertEquals(0, teammate.getAttribute(AttributeType.SHIELD_BOOST).get(), EPS);
        Assertions.assertEquals(100, battle.grantShield(teammate, knight, 100), EPS,
                "the teammate states no boost, so the shield they give is unboosted even though the carrier wears 103");
    }

    /**
     * Note: <b>The other shield path.</b> A shield can also be installed by the <b>skill data</b> rather than by a rule:
     * {@code SkillExecutor}'s non-damaging arm reads a {@code Defence} effect's parameters (March 7th's 100102 is one:
     * "38% defence + 190") and grants it directly. That arm must name its caster as the provider too, or a data-driven
     * Shield skill would be the one shield in the fight that "the shield amount provided is increased" does not reach.
     *
     * <p>The claim is stated as a <b>ratio</b> rather than an absolute number: what is being pinned is that the same
     * skill installs 1.22 x  as much for a wearer of relic 128, which stays true whatever the skill's own formula is (that
     * formula is March 7th's, and {@code SkillExecutorTest} owns it). Note: The caster is 1002 (a character with no rules of
     * its own) casting March 7th's skill <b>data</b>, so nothing but the data path can install the shield.
     *
     * <p>Note: <b>Set 128, not 103</b>, and the reason is a measurement: 103's 2-piece is a <i>stat</i> (defence +15%), and
     * this skill's amount is "38% defence + 190" - so a 103 wearer's shield is larger for <b>two</b> reasons at once and
     * the ratio came out 1.255 instead of 1.2 (measured). 128's tiers are ability-only, so it moves exactly one number.
     */
    @Test
    public void theSkillPanelShieldIsBoostedToo() {
        double plain = skillShield(false);
        double boosted = skillShield(true);

        Assertions.assertTrue(plain > 0, "precondition: the Defence skill really installs a shield (" + plain + ")");
        Assertions.assertEquals(1.22 * plain, boosted, EPS,
                "the skill-data path names the caster as the provider, so 「the shield amount provided is increased」 reaches it");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** What March 7th's Skill data installs on its caster, with or without relic 128's two ability-only tiers. */
    private static double skillShield(boolean withRelic) {
        Character caster = withRelic
                ? CharacterFactory.create(WEARER, LEVEL, true, null, RelicFactory.suit(RELIC_SET_128, STAR, RELIC_LEVEL))
                : CharacterFactory.create(WEARER, LEVEL);
        Battle battle = battleWith(List.of(caster));
        battle.castImmediate(new DefaultSkill(CASTERS_SKILL_CID, SKILL_SLOT, 1), caster, List.of(caster));
        return caster.getShield();
    }

    private static final int RELIC_SET_103 = 103;
    private static final int RELIC_SET_128 = 128;
    private static final int STAR = 5;
    private static final int RELIC_LEVEL = 15;
    /** March 7th's data row, read while the caster is somebody else (see {@link #theSkillPanelShieldIsBoostedToo}). */
    private static final int CASTERS_SKILL_CID = 1001;
    private static final int SKILL_SLOT = 2;

    /** A character wearing the whole cavern set, already in a started battle (so its rules have run). */
    private static Character wearing(int setId) {
        Character character = CharacterFactory.create(WEARER, LEVEL, true, null,
                RelicFactory.suit(setId, STAR, RELIC_LEVEL));
        battleWith(List.of(character));
        return character;
    }

    /** The same with exactly the first two cavern pieces, which is what a 2-piece bonus needs. */
    private static Character wearingTwoPieces(int setId) {
        com.laosun.aluminium.models.RelicSuit suit = new com.laosun.aluminium.models.RelicSuit();
        for (com.laosun.aluminium.enums.RelicType slot : List.of(
                com.laosun.aluminium.enums.RelicType.HEAD, com.laosun.aluminium.enums.RelicType.HAND)) {
            suit.addToSuit(RelicFactory.piece(setId, slot, STAR, RELIC_LEVEL));
        }
        Character character = CharacterFactory.create(WEARER, LEVEL, true, null, suit);
        battleWith(List.of(character));
        return character;
    }

    /** One started battle around the given team, so every {@code BATTLE_START} rule has fired. */
    private static Battle battleWith(List<Character> team) {
        Battle battle = new Battle(team, List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        return battle;
    }

    /** A giver and somebody to shield, both with their own trigger table slot (the fixture's table is set per case). */
    private static Battle fixture() {
        Character giver = CharacterFactory.create(WEARER, LEVEL);
        Character shielded = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(giver, shielded), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    /** One {@code SHIELD} effect with no duration (the raw grant). */
    private static EffectSpec shield(double amount) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "SHIELD");
        TriggerSpecs.set(effect, "amount", amount);
        TriggerSpecs.set(effect, "target", "target");
        return effect;
    }

    /** The same with "lasting N turns", which is the path that goes through {@code ShieldBuff}. */
    private static EffectSpec shieldWithTurns(double amount, int turns) {
        EffectSpec effect = shield(amount);
        TriggerSpecs.set(effect, "turns", turns);
        return effect;
    }
}
