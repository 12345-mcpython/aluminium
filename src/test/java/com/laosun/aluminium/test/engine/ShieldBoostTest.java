package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.buff.ShieldBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.equipment.RelicSuit;

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

    /**
     * The register's own example and the boundary around it: relic 103's 4-piece, "increases the shield amount provided
     * by the wearer by 20%", read at grant time from the <b>provider</b>.
     *
     * <p>Every sub-case below builds the same {@link #fixture()} and asks the same raw entry point
     * {@code Battle.grantShield}; only the attribute setup differs, so the assertions share one
     * {@link Assertions#assertAll} block. The claims are: the amount is multiplied by {@code 1 + provider's boost};
     * the boost belongs to the <b>giver</b>, so a strengthened <i>receiver</i> changes nothing and a grant with no
     * provider at all applies none; two statements about one stat <b>add</b>, because both are contributions to one
     * quantity (relic 103's 20% and 128's 10% cannot be worn together, but a light cone's 12% can sit next to a set
     * bonus, so the arithmetic has to be addition rather than "the strongest wins"); and a boost cannot conjure a
     * shield - 0 stays 0 and the "{@code <= 0} clears it" reading is unchanged.
     *
     * <p><b>Pure modifiers.</b> SHIELD_BOOST is a ratio attribute, and the engine's own {@code MODIFY_ATTR} gives a
     * ratio attribute a flat modifier rather than an additive percentage ({@code statModifier}:
     * {@code attribute.isPercent ? "pure" : "add_percent"}), because a percentage of a zero base is zero. Writing the
     * additive sub-case with {@code addPercent} would have "proved" that two boosts cancel out - and it did, until it
     * was measured.
     */
    @Test
    public void theGrantReadsTheProvidersBoost() {
        Battle providerBattle = fixture();
        Character provider = providerBattle.characters().getFirst();
        provider.setAttribute(AttributeType.SHIELD_BOOST, new DoubleValue(0.2));
        double boostedInstall = providerBattle.grantShield(provider, provider, 100);
        double drainedByDamage = provider.getShield();

        Battle receiverBattle = fixture();
        Character plainGiver = receiverBattle.characters().getFirst();
        Character boostedReceiver = receiverBattle.characters().getLast();
        boostedReceiver.setAttribute(AttributeType.SHIELD_BOOST, new DoubleValue(0.5));
        double unboostedInstall = receiverBattle.grantShield(plainGiver, boostedReceiver, 100);
        double receiverShield = boostedReceiver.getShield();

        Battle anonymousBattle = fixture();
        Character anonymousGiver = anonymousBattle.characters().getFirst();
        anonymousGiver.setAttribute(AttributeType.SHIELD_BOOST, new DoubleValue(0.5));
        double anonymousInstall = anonymousBattle.grantShield(anonymousGiver, 100);

        Battle additiveBattle = fixture();
        Character additiveGiver = additiveBattle.characters().getFirst();
        additiveGiver.getAttribute(AttributeType.SHIELD_BOOST)
                .addModifier(DoubleValue.Modifier.pure(0.1, DoubleValue.Modifier.ModifierSource.BUFF));
        additiveGiver.getAttribute(AttributeType.SHIELD_BOOST)
                .addModifier(DoubleValue.Modifier.pure(0.12, DoubleValue.Modifier.ModifierSource.BUFF));
        double addedInstall = additiveBattle.grantShield(additiveGiver, additiveGiver, 100);

        Battle clearingBattle = fixture();
        Character clearingGiver = clearingBattle.characters().getFirst();
        clearingGiver.setAttribute(AttributeType.SHIELD_BOOST, new DoubleValue(0.2));
        double zeroInstall = clearingBattle.grantShield(clearingGiver, clearingGiver, 0);
        clearingBattle.grantShield(clearingGiver, clearingGiver, 100);
        double negativeInstall = clearingBattle.grantShield(clearingGiver, clearingGiver, -50);
        double clearedShield = clearingGiver.getShield();

        Assertions.assertAll("the provider's SHIELD_BOOST is read at grant time",
                () -> Assertions.assertEquals(120, boostedInstall, EPS, "20% turns a 100 shield into 120"),
                () -> Assertions.assertEquals(120, drainedByDamage, EPS,
                        "and that is what the damage path will drain"),
                () -> Assertions.assertEquals(100, unboostedInstall, EPS,
                        "the giver states no boost, so the shield is what was asked for"),
                () -> Assertions.assertEquals(100, receiverShield, EPS,
                        "a boost on the receiver changes nothing - the number belongs to the giver"),
                () -> Assertions.assertEquals(100, anonymousInstall, EPS,
                        "a boost that cannot be attributed to a giver must not strengthen the shield"),
                () -> Assertions.assertEquals(122, addedInstall, EPS,
                        "two boosts on the same provider add: 10% + 12% = 22%"),
                () -> Assertions.assertEquals(0, zeroInstall, EPS, "a boost cannot conjure a shield from 0"),
                () -> Assertions.assertEquals(0, negativeInstall, EPS, "≤ 0 still clears, not −60"),
                () -> Assertions.assertEquals(0, clearedShield, EPS, "the cleared grant leaves no shield behind"));
    }

    /**
     * <b>Both rule paths, one number.</b> The {@code SHIELD} op reaches the field in two shapes - a raw grant when the
     * rule states no {@code turns}, and a timed {@link ShieldBuff} when it does - and a boost that only applied to one
     * of them would make March 7th (三月七)'s "lasting 3 turns" shield 120 for three turns and 100 forever after. Both
     * sub-cases fire the same {@code ALLY_ATTACK} rule on the same {@link #fixture()}; the untimed shape is checked
     * through the op's own call site rather than the API directly.
     */
    @Test
    public void bothRuleShapesInstallTheBoostedNumber() {
        Battle timedBattle = fixture();
        Character timedGiver = timedBattle.characters().getFirst();
        Character timedReceiver = timedBattle.characters().getLast();
        timedGiver.setAttribute(AttributeType.SHIELD_BOOST, new DoubleValue(0.2));
        timedGiver.setTriggerTable(new TriggerTable(WEARER,
                List.of(TriggerSpecs.rule("ALLY_ATTACK", null, shieldWithTurns(100, 3)))));
        timedBattle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.ALLY_ATTACK, timedGiver, timedReceiver, 1, 0));

        ShieldBuff timedBuff = timedReceiver.getBuffManager().allBuffsOf(ShieldBuff.class).getFirst();
        double buffAmount = timedBuff.getAmount();
        double timedShield = timedReceiver.getShield();

        Battle rawBattle = fixture();
        Character rawGiver = rawBattle.characters().getFirst();
        Character rawReceiver = rawBattle.characters().getLast();
        rawGiver.setAttribute(AttributeType.SHIELD_BOOST, new DoubleValue(0.2));
        rawGiver.setTriggerTable(new TriggerTable(WEARER,
                List.of(TriggerSpecs.rule("ALLY_ATTACK", null, shield(100)))));
        rawBattle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(
                com.laosun.aluminium.enums.TriggerEvent.ALLY_ATTACK, rawGiver, rawReceiver, 1, 0));

        double rawShield = rawReceiver.getShield();
        int rawBuffCount = rawReceiver.getBuffManager().allBuffsOf(ShieldBuff.class).size();

        Assertions.assertAll("both SHIELD shapes install the giver's boosted number",
                () -> Assertions.assertEquals(120, buffAmount, EPS, "the timed buff's amount is the boosted one"),
                () -> Assertions.assertEquals(120, timedShield, EPS, "…and that is what it installed"),
                () -> Assertions.assertEquals(120, rawShield, EPS, "the untimed rule grant is boosted too"),
                () -> Assertions.assertEquals(0, rawBuffCount,
                        "no turns stated, so no buff — the number is the whole effect"));
    }

    /**
     * The shipped content, read back through the loader: relic 103's 4-piece is 20% on its <b>wearer</b>.
     *
     * <p>This is the assertion the register could not make before: the ability was registered for days as
     * "not expressible", and a file that loads is not the same claim as a file whose number is right. It also pins
     * the <b>side</b>: the rule must land on the wearer (self), because "provided by the wearer" shields are the
     * wearer's own.
     *
     * <p>The number is read <b>after</b> {@code startBattle()}, and that is not a detail of the fixture: the bonus is an
     * ability rule, not a {@code properties} stat, so it is applied by the {@code BATTLE_START} rule it was written as.
     * Reading the sheet of a character who has not entered a battle would read 0 and "prove" the file wrong.
     */
    @Test
    public void theKnightsFourthPieceBoostsTheWearersOwnShields() {
        Character wearer = wearing(RELIC_SET_103);
        Battle battle = battleWith(List.of(wearer));
        double boost = wearer.getAttribute(AttributeType.SHIELD_BOOST).get();
        double granted = battle.grantShield(wearer, wearer, 100);

        Assertions.assertAll("relic 103's 4-piece lands on the wearer",
                () -> Assertions.assertEquals(0.2, boost, EPS, "「increases the shield amount provided by the wearer by 20%」"),
                () -> Assertions.assertEquals(120, granted, EPS,
                        "…and a shield that wearer grants really is a fifth larger"));
    }

    /**
     * 128's two tiers end up at 22% for a full set (10% + 12%), and each piece count states its own number.
     */
    @Test
    public void theReclusesTwoTiersAddUp() {
        Character full = wearing(RELIC_SET_128);
        Character twoPiece = wearingTwoPieces(RELIC_SET_128);
        double fullBoost = full.getAttribute(AttributeType.SHIELD_BOOST).get();
        double twoPieceBoost = twoPiece.getAttribute(AttributeType.SHIELD_BOOST).get();

        Assertions.assertAll("relic 128's two tiers state their own numbers",
                () -> Assertions.assertEquals(0.22, fullBoost, EPS,
                        "the 4-piece sentence is +12% ON TOP of the 2-piece's 10% (see the file's note for why it is "
                                + "written as the accumulated total)"),
                () -> Assertions.assertEquals(0.1, twoPieceBoost, EPS,
                        "the 2-piece alone is 10%, not a share of the 22%"));
    }

    /**
     * A set the wearer's shield does not come from changes nothing.
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
        double knightBoost = knight.getAttribute(AttributeType.SHIELD_BOOST).get();
        double teammateBoost = teammate.getAttribute(AttributeType.SHIELD_BOOST).get();
        double teammateGrant = battle.grantShield(teammate, knight, 100);

        Assertions.assertAll("the set bonus belongs to the unit that owns it",
                () -> Assertions.assertEquals(0.2, knightBoost, EPS, "the 103 wearer carries the 20%"),
                () -> Assertions.assertEquals(0, teammateBoost, EPS, "the teammate carries none of it"),
                () -> Assertions.assertEquals(100, teammateGrant, EPS,
                        "the teammate states no boost, so the shield they give is unboosted even though the carrier wears 103"));
    }

    /**
     * <b>The other shield path.</b> A shield can also be installed by the <b>skill data</b> rather than by a rule:
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

        Assertions.assertAll("the skill-data shield path names its caster as the provider",
                () -> Assertions.assertTrue(plain > 0,
                        "precondition: the Defence skill really installs a shield (" + plain + ")"),
                () -> Assertions.assertEquals(1.22 * plain, boosted, EPS,
                        "the skill-data path names the caster as the provider, so 「the shield amount provided is increased」 reaches it"));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * What March 7th's Skill data installs on its caster, with or without relic 128's two ability-only tiers.
     */
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
    /**
     * March 7th's data row, read while the caster is somebody else (see {@link #theSkillPanelShieldIsBoostedToo}).
     */
    private static final int CASTERS_SKILL_CID = 1001;
    private static final int SKILL_SLOT = 2;

    /**
     * A character wearing the whole cavern set, already in a started battle (so its rules have run).
     */
    private static Character wearing(int setId) {
        Character character = CharacterFactory.create(WEARER, LEVEL, true, null,
                RelicFactory.suit(setId, STAR, RELIC_LEVEL));
        battleWith(List.of(character));
        return character;
    }

    /**
     * The same with exactly the first two cavern pieces, which is what a 2-piece bonus needs.
     */
    private static Character wearingTwoPieces(int setId) {
        com.laosun.aluminium.equipment.RelicSuit suit = new com.laosun.aluminium.equipment.RelicSuit();
        for (com.laosun.aluminium.enums.RelicType slot : List.of(
                com.laosun.aluminium.enums.RelicType.HEAD, com.laosun.aluminium.enums.RelicType.HAND)) {
            suit.addToSuit(RelicFactory.piece(setId, slot, STAR, RELIC_LEVEL));
        }
        Character character = CharacterFactory.create(WEARER, LEVEL, true, null, suit);
        battleWith(List.of(character));
        return character;
    }

    /**
     * One started battle around the given team, so every {@code BATTLE_START} rule has fired.
     */
    private static Battle battleWith(List<Character> team) {
        Battle battle = BattleFixture.battle(team, List.of(BattleFixture.monster()));
        battle.startBattle();
        return battle;
    }

    /**
     * A giver and somebody to shield, both with their own trigger table slot (the fixture's table is set per case).
     */
    private static Battle fixture() {
        Character giver = CharacterFactory.create(WEARER, LEVEL);
        Character shielded = CharacterFactory.create(WEARER, LEVEL);
        Enemy enemy = EnemyFactory.create(MONSTER, 90, 1);
        Battle battle = new Battle(List.of(giver, shielded), List.of(enemy), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    /**
     * One {@code SHIELD} effect with no duration (the raw grant).
     */
    private static EffectSpec shield(double amount) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "SHIELD");
        TriggerSpecs.set(effect, "amount", amount);
        TriggerSpecs.set(effect, "target", "target");
        return effect;
    }

    /**
     * The same with "lasting N turns", which is the path that goes through {@code ShieldBuff}.
     */
    private static EffectSpec shieldWithTurns(double amount, int turns) {
        EffectSpec effect = shield(amount);
        TriggerSpecs.set(effect, "turns", turns);
        return effect;
    }
}
