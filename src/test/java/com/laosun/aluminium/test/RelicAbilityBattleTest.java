package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.RelicSuit;
import com.laosun.aluminium.models.Signal;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Relic set abilities, end to end: a set whose bonus is an <em>ability</em> rather than stats now
 * actually does something in a battle.
 *
 * <p>These sets are the ones the current op vocabulary can express exactly (see
 * {@code RelicTriggerTableTest} for the rest, which are registered as gaps instead of being
 * approximated). Each is asserted through a real battle, because that is the point: the rules live in
 * {@code resources/relic_sets/<setId>.json} and are attached by {@code CharacterFactory}, so a test
 * that only read the JSON would prove nothing about whether the engine executes it.
 *
 * <h2>The F-2 case</h2>
 * {@link #openingSkillPointsIncludeThePasserbyFourPiece()} is the end-to-end claim registered as
 * {@code F-2} in {@code DOC_VS_CODE.md}: a team wearing the Passerby of Wandering Cloud 4-piece starts
 * with <b>4</b> skill points instead of 3. It also pins the <b>ordering</b> the claim depends on —
 * the opening value is assigned when the {@link Battle} is constructed, and {@code BATTLE_START} is
 * fired later by {@code startBattle()}, which is what makes the +1 observable.
 */
public class RelicAbilityBattleTest {

    /** "Passerby of Wandering Cloud" — 4-piece: at the start of the battle, regenerate 1 skill point. */
    private static final int PASSERBY = 101;
    /** "Hunter of Glacial Forest" — 4-piece: after the Ultimate, +25% CRIT DMG for 2 turns. */
    private static final int GLACIAL_FOREST = 104;
    /** "Band of Sizzling Thunder" — 4-piece: on Skill, +20% ATK for 1 turn. */
    private static final int SIZZLING_THUNDER = 109;
    /** "Eagle of Twilight Line" — 4-piece: after the Ultimate, advance forward 25%. */
    private static final int TWILIGHT_EAGLE = 110;
    /** "Champion of Streetwise Boxing" — 4-piece: +5% ATK per attack taken/given, up to 5 stacks. */
    private static final int STREETWISE_BOXING = 105;
    /** "City of Converging Stars" (planar) — 2-piece: on Follow-Up ATK +24% ATK; on a kill +12% CRIT DMG. */
    private static final int CONVERGING_STARS = 326;
    /** "The Ashblazing Grand Duke" — 2-piece: +20% DMG dealt by Follow-Up ATK. */
    private static final int ASHBLAZING = 115;

    /** Himeko: basic attack / skill / ultimate are all real, damaging skill slots. */
    private static final int HIMEKO = 1003;
    private static final int ICE_EDGE = 1002011;
    private static final int ENEMY_LEVEL = 90;
    private static final int CHARACTER_LEVEL = 80;

    /** Enough HP that a cast never kills the enemy mid-test. */
    private static final double ENEMY_HP = 1_000_000;

    private static final int STAR = 5;
    private static final int LEVEL = 15;

    /** How many pieces a partial suit wears — one short of every set's 4-piece tier. */
    private static final int PARTIAL_PIECES = 3;

    /** The four cavern slots, in the order a partial suit fills them. */
    private static final List<RelicType> CAVERN_SLOTS =
            List.of(RelicType.HEAD, RelicType.HAND, RelicType.BODY, RelicType.BOOT);

    /** The authored numbers, so an assertion can never silently drift from the data file. */
    private static final double GLACIAL_CRIT_DMG = 0.25;
    private static final double SIZZLING_ATTACK = 0.2;
    private static final double EAGLE_ADVANCE = 0.25;
    /** Ability51051: +5% ATK per application, at most 5 applications. */
    private static final double STREETWISE_ATTACK_PER_STACK = 0.05;
    private static final int STREETWISE_MAX_STACKS = 5;

    private static final double TOLERANCE = 1e-9;

    // ==================================================================
    // 1. F-2: the opening skill points
    // ==================================================================

    /**
     * A team without the set starts at 3; with one 4-piece wearer it starts at 4; with two wearers, 5.
     *
     * <p>Each case is measured against the same team without the set, so nothing but the set bonus can
     * explain the difference.
     */
    @Test
    public void openingSkillPointsIncludeThePasserbyFourPiece() {
        Assertions.assertEquals(3, Constant.SKILL_POINT_START, "the engine's conventional opening value");

        Battle control = newBattle(List.of(plain(HIMEKO), plain(HIMEKO)), true);
        Assertions.assertEquals(3, control.getSkillPoints(),
                "without the set the team still opens at the conventional 3");

        Battle one = newBattle(List.of(wearing(HIMEKO, PASSERBY), plain(HIMEKO)), true);
        Assertions.assertEquals(4, one.getSkillPoints(),
                "F-2: a Passerby 4-piece wearer makes the team open at 4 (3 + 1 from Ability51011)");

        Battle two = newBattle(List.of(wearing(HIMEKO, PASSERBY), wearing(HIMEKO, PASSERBY)), true);
        Assertions.assertEquals(5, two.getSkillPoints(),
                "two wearers each contribute 1, capped at the conventional maximum of "
                        + Constant.SKILL_POINT_MAX);
    }

    /**
     * The ordering finding, stated as an assertion: the opening value exists <b>before</b>
     * {@code BATTLE_START} fires, and the set rule is what raises it.
     *
     * <p>{@code Battle}'s constructor installs the skill-point policy (start
     * {@link Constant#SKILL_POINT_START}); {@code startBattle()} fires {@code BATTLE_START} later. So
     * the direction of the bonus is "3, then +1", and the observable opening value is 4. Had the
     * trigger fired before the opening assignment, the +1 would have been overwritten and the
     * registered F-2 claim would be unimplementable — this test is what catches such a reordering.
     */
    @Test
    public void theOpeningValueIsAssignedBeforeBattleStartFires() {
        Battle notStarted = newBattle(List.of(wearing(HIMEKO, PASSERBY)), false);
        Assertions.assertEquals(3, notStarted.getSkillPoints(),
                "the conventional start value is in place before startBattle()");

        notStarted.startBattle();
        Assertions.assertEquals(4, notStarted.getSkillPoints(),
                "BATTLE_START is fired by startBattle() and adds the set's point on top of the opening 3");
    }

    /** Three pieces is one short of the 4-piece bonus, so the team opens at 3. */
    @Test
    public void belowTheThresholdTheOpeningValueIsUnchanged() {
        Battle threePieces = newBattle(List.of(partial(HIMEKO, PASSERBY)), true);
        Assertions.assertEquals(3, threePieces.getSkillPoints(),
                "three pieces of set 101 must not grant the 4-piece ability");
    }

    // ==================================================================
    // 2. The three in-battle abilities
    // ==================================================================

    /**
     * Set 104: after the wearer's Ultimate, +25% CRIT DMG for 2 turns.
     *
     * <p>{@code CRIT DMG} is a <b>ratio</b> attribute, so the rule's {@code percent} is an absolute
     * addition to it (0.5 → 0.75), not a factor. That is asserted through the modifier the rule
     * actually installed as well as through the final value, so "the buff is there with the right
     * number" cannot pass by accident.
     */
    @Test
    public void ultimateCriticalDamageBonusApplies() {
        Battle battle = newBattle(List.of(wearing(HIMEKO, GLACIAL_FOREST)), true);
        Character hero = battle.characters.getFirst();
        double before = hero.getAttribute(AttributeType.CRIT_ATTACK).get();

        Assertions.assertTrue(castUltimate(battle, hero));

        List<DoubleValue.Modifier> buffs = buffsOn(hero, AttributeType.CRIT_ATTACK);
        Assertions.assertEquals(1, buffs.size(),
                "Ability51041 must install exactly one CRIT DMG buff; the 2-piece tier of this set is "
                        + "Ice DMG, so nothing else touches this attribute");
        Assertions.assertEquals(GLACIAL_CRIT_DMG, buffs.getFirst().getValue(), TOLERANCE,
                "param #1 is 0.25, and for a ratio attribute that is 25 percentage points");
        Assertions.assertEquals(before + GLACIAL_CRIT_DMG,
                hero.getAttribute(AttributeType.CRIT_ATTACK).get(), TOLERANCE,
                "Ability51041: +25% CRIT DMG after the wearer's Ultimate");

        Battle partialBattle = newBattle(List.of(partial(HIMEKO, GLACIAL_FOREST)), true);
        Character control = partialBattle.characters.getFirst();
        Assertions.assertTrue(castUltimate(partialBattle, control));
        Assertions.assertTrue(buffsOn(control, AttributeType.CRIT_ATTACK).isEmpty(),
                "three pieces must not grant the 4-piece ability");
    }

    /**
     * Set 109: on the wearer's <b>Skill</b>, +20% ATK — and <b>not</b> on the Ultimate.
     *
     * <p>Two separate battles rather than one, because re-applying the same stat buff only
     * <em>refreshes</em> it ({@code StatModifierBuff.isSameKind}), so "did it fire again?" would be
     * invisible in a single run. The control battle casts only the ultimate: its ATK must be untouched
     * there.
     *
     * <p>ATK is a <b>base</b> attribute, so the rule's {@code percent} is an additive percentage of
     * the base ({@code base × 1.2 + flat}), which is why the expectation is stated on the modifier
     * rather than on a simple ratio of the final value.
     */
    @Test
    public void skillAttackBonusAppliesOnSkillButNotOnTheUltimate() {
        Battle onSkill = newBattle(List.of(wearing(HIMEKO, SIZZLING_THUNDER)), true);
        Character skillUser = onSkill.characters.getFirst();
        double skillBefore = skillUser.getAttribute(AttributeType.ATTACK).get();
        onSkill.castImmediate(skillUser.getSkills().get(SkillType.SKILL), skillUser,
                List.of(onSkill.enemies.getFirst()));

        List<DoubleValue.Modifier> buffs = buffsOn(skillUser, AttributeType.ATTACK);
        Assertions.assertEquals(1, buffs.size(),
                "Ability51091 must install exactly one ATK buff when the wearer uses their Skill");
        Assertions.assertEquals(SIZZLING_ATTACK, buffs.getFirst().getValue(), TOLERANCE,
                "param #1 is 0.2");
        Assertions.assertEquals(DoubleValue.Modifier.ModifierType.ADD_PERCENT,
                buffs.getFirst().getModifierType(),
                "ATK is a base attribute, so the buff is an additive percentage, not a flat value");
        Assertions.assertTrue(skillUser.getAttribute(AttributeType.ATTACK).get() > skillBefore,
                "the buff must actually raise the attribute");

        Battle onUltimate = newBattle(List.of(wearing(HIMEKO, SIZZLING_THUNDER)), true);
        Character ultimateUser = onUltimate.characters.getFirst();
        double ultimateBefore = ultimateUser.getAttribute(AttributeType.ATTACK).get();
        Assertions.assertTrue(castUltimate(onUltimate, ultimateUser));
        Assertions.assertTrue(buffsOn(ultimateUser, AttributeType.ATTACK).isEmpty(),
                "an Ultimate is not a Skill: ULT_CAST and SKILL_CAST are mutually exclusive, so the "
                        + "20% ATK must not arrive here");
        Assertions.assertEquals(ultimateBefore, ultimateUser.getAttribute(AttributeType.ATTACK).get(), TOLERANCE);
    }

    /** Set 110: after the wearer's Ultimate, their action is advanced forward by 25%. */
    @Test
    public void ultimateAdvancesTheWearersAction() {
        Battle battle = newBattle(List.of(wearing(HIMEKO, TWILIGHT_EAGLE)), true);
        Character hero = battle.characters.getFirst();
        double remainingBefore = timeRemaining(battle, hero);
        Assertions.assertTrue(remainingBefore > 0, "precondition: the wearer has time left before acting");

        Assertions.assertTrue(castUltimate(battle, hero));

        Assertions.assertEquals(remainingBefore * (1 - EAGLE_ADVANCE), timeRemaining(battle, hero), 1e-6,
                "Ability51101: the wearer skips 25% of their remaining time to act");

        Battle partialBattle = newBattle(List.of(partial(HIMEKO, TWILIGHT_EAGLE)), true);
        Character control = partialBattle.characters.getFirst();
        double controlBefore = timeRemaining(partialBattle, control);
        Assertions.assertTrue(castUltimate(partialBattle, control));
        Assertions.assertEquals(controlBefore, timeRemaining(partialBattle, control), 1e-6,
                "three pieces must not grant the 4-piece ability");
    }

    // ==================================================================
    // 3. The stackable / unbounded ability (set 105)
    // ==================================================================

    /**
     * Ability51051, the "attacks or is hit" ability: each application adds one stack of +5% ATK, and the
     * sixth application changes nothing.
     *
     * <p>This is the end-to-end claim for the stacking primitive and for the {@code TAKING_HIT} event at
     * once. Two things are asserted separately because they fail differently:
     * <ul>
     *   <li><b>The two halves of the disjunction share one cap.</b> Four attacks plus three hits is
     *       seven applications of a 5-stack ability, so the total must be exactly 5 — which is only true
     *       if {@code ALLY_ATTACK} and {@code TAKING_HIT} accumulate into the <b>same</b> stack group.
     *       (With one counter per rule the total would be 8, and the test would still pass a naive
     *       "did anything accumulate?" check.)</li>
     *   <li><b>The "is hit" half is {@code TAKING_HIT}, not {@code HP_LOST}.</b> The hits taken here are
     *       <b>fully absorbed by a shield</b>, so no HP is lost: had the rule been hung on
     *       {@code HP_LOST} instead, the stack count would stop at 4. That is the exact scenario the
     *       distinction exists for.</li>
     *   <li><b>The stacks actually reach the attribute.</b> Each one is checked to be an
     *       {@code ADD_PERCENT} of 0.05 — the kind a <b>base</b> attribute needs, because ATK is
     *       {@code base × (1 + sum of percentages)}: the total is deliberately <em>not</em> asserted as a
     *       ratio of the character's ATK, since the character carries several other sources of the same
     *       percentage (traces, relics, and this set's own 2-piece stat bonus).</li>
     * </ul>
     */
    @Test
    public void streetwiseBoxingStacksOnAttackAndOnBeingHitUpToItsCap() {
        Battle battle = newBattle(List.of(wearing(HIMEKO, STREETWISE_BOXING)), true);
        Character hero = battle.characters.getFirst();
        int baseBuffs = buffsOn(hero, AttributeType.ATTACK).size();
        double attackWithoutStacks = hero.getAttribute(AttributeType.ATTACK).get();

        // Six of the wearer's own attacks: the sixth application is already past the cap.
        for (int i = 0; i < 6; i++) {
            battle.castImmediate(hero.getSkills().get(SkillType.COMMON), hero,
                    List.of(battle.enemies.getFirst()));
        }
        Assertions.assertEquals(baseBuffs + STREETWISE_MAX_STACKS,
                buffsOn(hero, AttributeType.ATTACK).size(),
                "six attacks stop at the 5-stack cap from Ability51051's own text");
        assertEachStackIsStreetwise(hero);
        Assertions.assertTrue(hero.getAttribute(AttributeType.ATTACK).get() > attackWithoutStacks,
                "the stacks must reach the attribute");

        // Three hits taken, all of them swallowed by a shield: the cap is already reached, so these add
        // nothing — and they prove the "is hit" half is wired, because HP_LOST never fires here.
        battle.grantShield(hero, 10_000_000);
        double hpBefore = hero.getCurrentHp();
        for (int i = 0; i < 3; i++) {
            battle.applyDamage(hero, new Damage(battle.enemies.getFirst(), hero,
                    DamageElement.PHYSICAL, 1_000));
        }
        Assertions.assertEquals(hpBefore, hero.getCurrentHp(), TOLERANCE,
                "precondition: the shield absorbed every hit, so no HP was lost");
        Assertions.assertEquals(baseBuffs + STREETWISE_MAX_STACKS,
                buffsOn(hero, AttributeType.ATTACK).size(),
                "TAKING_HIT must add stacks to the SAME counter as ALLY_ATTACK, and the shared cap is 5");
        assertEachStackIsStreetwise(hero);
    }

    /**
     * The "is hit" half on its own: a shielded hit is still a hit, so it adds a stack.
     *
     * <p>Split from the test above because that one is already at the cap when the hits land. Here the
     * shield guarantees {@code HP_LOST} does not fire, so a rule hung on it would leave the count at zero
     * — which is exactly the regression this pins.
     */
    @Test
    public void streetwiseBoxingStacksOnAShieldedHitAsWell() {
        Battle battle = newBattle(List.of(wearing(HIMEKO, STREETWISE_BOXING)), true);
        Character hero = battle.characters.getFirst();
        int baseBuffs = buffsOn(hero, AttributeType.ATTACK).size();

        battle.grantShield(hero, 10_000_000);
        double hpBefore = hero.getCurrentHp();
        battle.applyDamage(hero, new Damage(battle.enemies.getFirst(), hero,
                DamageElement.PHYSICAL, 1_000));

        Assertions.assertEquals(hpBefore, hero.getCurrentHp(), TOLERANCE,
                "precondition: the shield ate the whole hit");
        Assertions.assertEquals(baseBuffs + 1, buffsOn(hero, AttributeType.ATTACK).size(),
                "a hit the shield absorbed is still 'being hit', so the set must gain a stack");
    }

    /** Every installed stack carries the modifier the ability states, of the kind a base attribute needs. */
    private static void assertEachStackIsStreetwise(Character hero) {
        for (DoubleValue.Modifier modifier : buffsOn(hero, AttributeType.ATTACK)) {
            Assertions.assertEquals(STREETWISE_ATTACK_PER_STACK, modifier.getValue(), TOLERANCE);
            Assertions.assertEquals(DoubleValue.Modifier.ModifierType.ADD_PERCENT,
                    modifier.getModifierType(),
                    "ATK is a base attribute, so each stack is an additive percentage");
        }
    }

    /**
     * "For the rest of the battle": the stacks survive every turn boundary.
     *
     * <p>The observable is the stack count after several full turns — a turn-limited modifier would
     * have expired (and the count dropped) long before the loop ends.
     */
    @Test
    public void streetwiseBoxingStacksLastForTheRestOfTheBattle() {
        Battle battle = newBattle(List.of(wearing(HIMEKO, STREETWISE_BOXING)), true);
        Character hero = battle.characters.getFirst();
        int baseBuffs = buffsOn(hero, AttributeType.ATTACK).size();

        battle.castImmediate(hero.getSkills().get(SkillType.COMMON), hero,
                List.of(battle.enemies.getFirst()));
        Assertions.assertEquals(baseBuffs + 1, buffsOn(hero, AttributeType.ATTACK).size());

        for (int turn = 0; turn < 10; turn++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            battle.beforeMove();
            battle.afterMove();
        }

        Assertions.assertEquals(baseBuffs + 1, buffsOn(hero, AttributeType.ATTACK).size(),
                "a 'permanent' modifier has no turn limit, so no number of turns may remove it");
        assertEachStackIsStreetwise(hero);
    }

    /** Three pieces must not grant the 4-piece ability. */
    @Test
    public void streetwiseBoxingNeedsAllFourPieces() {
        Battle battle = newBattle(List.of(partial(HIMEKO, STREETWISE_BOXING)), true);
        Character hero = battle.characters.getFirst();
        double baseAttack = hero.getAttribute(AttributeType.ATTACK).get();

        battle.castImmediate(hero.getSkills().get(SkillType.COMMON), hero,
                List.of(battle.enemies.getFirst()));

        Assertions.assertTrue(buffsOn(hero, AttributeType.ATTACK).isEmpty(),
                "three pieces is one short of the 4-piece bonus");
        Assertions.assertEquals(baseAttack, hero.getAttribute(AttributeType.ATTACK).get(), TOLERANCE);
    }

    // ==================================================================
    // 6. FOLLOW_UP: City of Converging Stars (326)
    // ==================================================================

    /** Ability53261: on Follow-Up ATK, +24% ATK for 2 turns; on a kill, +12% CRIT DMG for all allies. */
    private static final double CONVERGING_ATTACK = 0.24;
    private static final double CONVERGING_CRIT_DMG = 0.12;

    /**
     * The wearer's own follow-up attack raises their ATK by the authored amount.
     *
     * <p>This is the end-to-end proof that {@code FOLLOW_UP} has a real emitter: the event is fired from
     * {@code Battle.applyAdditionalDamage}, the rule lives in {@code resources/relic_sets/326.json}, and
     * neither half is visible unless both work. Asserting on the resulting <b>attribute value</b> rather
     * than on "a modifier exists" is deliberate — a rule that fired but applied nothing would pass the
     * weaker check.
     */
    @Test
    public void cityOfConvergingStarsBuffsAttackOnTheWearersFollowUp() {
        Character himeko = wearing(HIMEKO, CONVERGING_STARS);
        Battle battle = newBattle(List.of(himeko), true);
        double before = himeko.getAttribute(AttributeType.ATTACK).get();

        battle.applyAdditionalDamage(himeko, battle.enemies.getFirst(), DamageElement.FIRE, 100);

        List<DoubleValue.Modifier> buffs = buffsOn(himeko, AttributeType.ATTACK);
        Assertions.assertEquals(1, buffs.size(),
                "Ability53261 must install exactly one ATK buff when the wearer uses a Follow-Up ATK");
        Assertions.assertEquals(CONVERGING_ATTACK, buffs.getFirst().getValue(), TOLERANCE, "param #1 is 0.24");
        Assertions.assertEquals(DoubleValue.Modifier.ModifierType.ADD_PERCENT,
                buffs.getFirst().getModifierType(),
                "ATK is a base attribute, so the buff is an additive percentage, not a flat value");
        Assertions.assertTrue(himeko.getAttribute(AttributeType.ATTACK).get() > before,
                "the buff must actually raise the attribute");
    }

    /**
     * A follow-up attack is not just "an attack": a plain basic attack must leave the bonus alone.
     *
     * <p>This is why {@code FOLLOW_UP} exists instead of reusing {@code ALLY_ATTACK} — a rule hung on
     * the latter would fire here too, which the set's text does not allow.
     */
    @Test
    public void aBasicAttackDoesNotCountAsAFollowUp() {
        Character himeko = wearing(HIMEKO, CONVERGING_STARS);
        Battle battle = newBattle(List.of(himeko), true);
        double before = himeko.getAttribute(AttributeType.ATTACK).get();

        battle.castImmediate(himeko.getSkills().get(SkillType.COMMON), himeko,
                List.of(battle.enemies.getFirst()));

        Assertions.assertEquals(before, himeko.getAttribute(AttributeType.ATTACK).get(), TOLERANCE,
                "only an additional-damage instance is a follow-up attack; a basic attack is not");
    }

    /**
     * "This effect cannot stack" is honoured: a second follow-up refreshes rather than accumulating.
     *
     * <p>Worth asserting because the stacking primitive added for set 105 could easily have been applied
     * here by reflex. A stackable reading would give +48% ATK and two modifiers.
     */
    @Test
    public void theFollowUpAttackBonusDoesNotStack() {
        Character himeko = wearing(HIMEKO, CONVERGING_STARS);
        Battle battle = newBattle(List.of(himeko), true);
        double base = himeko.getAttribute(AttributeType.ATTACK).get();

        battle.applyAdditionalDamage(himeko, battle.enemies.getFirst(), DamageElement.FIRE, 100);
        double afterFirst = himeko.getAttribute(AttributeType.ATTACK).get();
        battle.applyAdditionalDamage(himeko, battle.enemies.getFirst(), DamageElement.FIRE, 100);

        List<DoubleValue.Modifier> buffs = buffsOn(himeko, AttributeType.ATTACK);
        Assertions.assertEquals(1, buffs.size(),
                "two follow-ups must leave one modifier, not two");
        Assertions.assertEquals(CONVERGING_ATTACK, buffs.getFirst().getValue(), TOLERANCE,
                "the second follow-up refreshes the same bonus instead of adding another");
        Assertions.assertEquals(afterFirst, himeko.getAttribute(AttributeType.ATTACK).get(), TOLERANCE,
                "so the value after the second follow-up is the same as after the first, not higher");
        Assertions.assertTrue(afterFirst > base, "and it really did move off the base value");
    }

    @Test
    public void cityOfConvergingStarsGivesTheWholeTeamCritDamageOnAKill() {
        Character himeko = wearing(HIMEKO, CONVERGING_STARS);
        Character mate = plain(HIMEKO);
        Battle battle = newBattle(List.of(himeko, mate), true);
        Enemy enemy = battle.enemies.getFirst();
        double wearerBefore = himeko.getAttribute(AttributeType.CRIT_ATTACK).get();
        double mateBefore = mate.getAttribute(AttributeType.CRIT_ATTACK).get();

        // One instance big enough to kill: the KILL event fires from applyDamage's settlement.
        battle.applyAdditionalDamage(himeko, enemy, DamageElement.FIRE, ENEMY_HP * 2);
        Assertions.assertTrue(enemy.isDeath(), "the enemy really did die, so KILL fired");

        Assertions.assertEquals(wearerBefore + CONVERGING_CRIT_DMG,
                himeko.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "CRIT DMG is a ratio attribute, so the value is the bonus itself (12 percentage points)");
        Assertions.assertEquals(mateBefore + CONVERGING_CRIT_DMG,
                mate.getAttribute(AttributeType.CRIT_ATTACK).get(), 1e-9,
                "'for all allies' must reach a team-mate, not just the wearer");
    }

    // ==================================================================
    // 7. The follow-up-only damage boost: The Ashblazing Grand Duke (115)
    // ==================================================================

    /** Ability51150: "Increases the DMG dealt by Follow-Up ATK by 20%." */
    private static final double ASHBLAZING_BOOST = 0.2;

    /**
     * Set 115's 2-piece raises <b>follow-up damage and nothing else</b>.
     *
     * <p>This is the whole reason {@code FOLLOW_UP_DAMAGE_BOOST} exists as its own attribute: the
     * all-type boost would also raise basic attacks, skills and ultimates, and the text says Follow-Up
     * ATK specifically.
     *
     * <p>The measurement is a <b>differential between two identically-built battles</b>, one with the set
     * bonus and one with the buffs cleared, rather than an absolute ratio. A ratio would be wrong here:
     * the boost zone is additive with the wearer's element and all-type boosts, so "20% more" is 20
     * percentage points on that sum, not a factor of 1.2 on the final number — the same trap the ATK
     * buffs above fell into.
     */
    @Test
    public void ashblazingTwoPieceRaisesFollowUpDamageAndLeavesOtherAttacksAlone() {
        Battle withBoost = newBattle(List.of(wearing(HIMEKO, ASHBLAZING)), true);
        Battle withoutBoost = newBattle(List.of(wearing(HIMEKO, ASHBLAZING)), true);
        withoutBoost.characters.getFirst().getBuffManager().clearAll();

        Character himeko = withBoost.characters.getFirst();
        Assertions.assertEquals(ASHBLAZING_BOOST,
                himeko.getAttribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST).get(), TOLERANCE,
                "the 2-piece rule grants the follow-up-only boost at battle start");
        Assertions.assertEquals(0,
                withoutBoost.characters.getFirst()
                        .getAttribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST).get(), TOLERANCE,
                "and clearing the buffs removes it, so the control battle really is the control");

        // A fixed crit state keeps both battles numerically comparable.
        for (Battle battle : List.of(withBoost, withoutBoost)) {
            battle.characters.getFirst().setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        }

        double followUpWith = hitFor(withBoost, true);
        double followUpWithout = hitFor(withoutBoost, true);
        double normalWith = hitFor(withBoost, false);
        double normalWithout = hitFor(withoutBoost, false);

        Assertions.assertTrue(followUpWith > followUpWithout,
                "a follow-up attack must hit harder with the boost: " + followUpWith
                        + " vs " + followUpWithout);
        Assertions.assertEquals(normalWith, normalWithout, TOLERANCE,
                "a basic attack must be completely unaffected: " + normalWith + " vs " + normalWithout);
    }

    /** Settles one hit and returns how much HP the enemy lost. */
    private static double hitFor(Battle battle, boolean followUp) {
        Enemy enemy = battle.enemies.getFirst();
        Character hero = battle.characters.getFirst();
        double before = enemy.getCurrentHp();
        if (followUp) {
            battle.applyAdditionalDamage(hero, enemy, DamageElement.FIRE, 1000);
        } else {
            battle.castImmediate(hero.getSkills().get(SkillType.COMMON), hero, List.of(enemy));
        }
        return before - enemy.getCurrentHp();
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** A character wearing the whole set (four pieces for a cavern set). */
    private static Character wearing(int cid, int setId) {
        return CharacterFactory.create(cid, CHARACTER_LEVEL, true, null,
                RelicFactory.suit(setId, STAR, LEVEL));
    }

    /** A character with no relics at all. */
    private static Character plain(int cid) {
        return CharacterFactory.create(cid, CHARACTER_LEVEL);
    }

    /** A character wearing the first {@link #PARTIAL_PIECES} cavern pieces of a set. */
    private static Character partial(int cid, int setId) {
        RelicSuit suit = new RelicSuit();
        for (RelicType slot : CAVERN_SLOTS.subList(0, PARTIAL_PIECES)) {
            suit.addToSuit(RelicFactory.piece(setId, slot, STAR, LEVEL));
        }
        return CharacterFactory.create(cid, CHARACTER_LEVEL, true, null, suit);
    }

    /**
     * A battle of real characters against one enemy that cannot die.
     *
     * @param start whether {@code startBattle()} has already run
     */
    private static Battle newBattle(List<Character> team, boolean start) {
        Enemy enemy = EnemyFactory.create(ICE_EDGE, ENEMY_LEVEL, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(ENEMY_HP));
        enemy.heal(ENEMY_HP);
        Battle battle = new Battle(team, List.of(enemy), new Random(0));
        if (start) {
            battle.startBattle();
        }
        return battle;
    }

    /** Casts the character's ultimate for real (through {@code Battle.castUltra}, energy gate included). */
    private static boolean castUltimate(Battle battle, Character hero) {
        hero.setCurrentEnergy(hero.getMaxEnergy());
        return battle.castUltra(hero, List.of(battle.enemies.getFirst()));
    }

    /** The modifiers a buff has installed on one attribute, in application order. */
    private static List<DoubleValue.Modifier> buffsOn(Character character, AttributeType type) {
        return character.getAttribute(type).filterBySource(DoubleValue.Modifier.ModifierSource.BUFF);
    }

    /** How much action value the unit still has to accumulate before its next turn. */
    private static double timeRemaining(Battle battle, CanHit unit) {
        for (Signal signal : battle.queue.snapshot()) {
            if (signal.getCanHit() == unit) {
                return battle.queue.getTimeRemaining(signal);
            }
        }
        throw new IllegalStateException(unit.getName() + " is not on the action bar");
    }
}
