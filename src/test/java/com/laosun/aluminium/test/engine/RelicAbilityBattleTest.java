package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.*;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.buff.ReductionBuff;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.equipment.RelicSuit;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.equipment.Relic;
import com.laosun.aluminium.Signal;
import com.laosun.aluminium.DoubleValue;

/**
 * Relic set abilities, end to end: a set whose bonus is an <em>ability</em> rather than stats now
 * actually does something in a battle.
 *
 * <p>These sets are the ones the current op vocabulary can express exactly (see
 * {@code RelicTriggerTableTest} for the rest, which are registered as gaps instead of being
 * approximated). Each is asserted through a real battle, because that is the point: the rules live in
 * {@code resources/game/relic_sets/<setId>.json} and are attached by {@code CharacterFactory}, so a test
 * that only read the JSON would prove nothing about whether the engine executes it.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the ten scenarios below, one {@code assertAll}
 * per scenario so a failing clause still names itself. A clause keeps its own battle whenever it needs a
 * different fixture (a control team, a partial suit, a fresh, uncapped stack count), so no scenario mixes
 * one fixture's numbers into another's, and no assertion of a different observed object is folded into
 * another's group.
 *
 * <h2>The F-2 case</h2>
 * {@link #passerbyOpeningSkillPointsScenario()} is the end-to-end claim registered as
 * {@code F-2}: a team wearing the Passerby of Wandering Cloud 4-piece starts
 * with <b>4</b> skill points instead of 3. It also pins the <b>ordering</b> the claim depends on -
 * the opening value is assigned when the {@link Battle} is constructed, and {@code BATTLE_START} is
 * fired later by {@code startBattle()}, which is what makes the +1 observable.
 */
public class RelicAbilityBattleTest {

    /**
     * "Passerby of Wandering Cloud" - 4-piece: at the start of the battle, regenerate 1 skill point.
     */
    private static final int PASSERBY = 101;
    /**
     * "Hunter of Glacial Forest" - 4-piece: after the Ultimate, +25% CRIT DMG for 2 turns.
     */
    private static final int GLACIAL_FOREST = 104;
    /**
     * "Band of Sizzling Thunder" - 4-piece: on Skill, +20% ATK for 1 turn.
     */
    private static final int SIZZLING_THUNDER = 109;
    /**
     * "Eagle of Twilight Line" - 4-piece: after the Ultimate, advance forward 25%.
     */
    private static final int TWILIGHT_EAGLE = 110;
    /**
     * "Champion of Streetwise Boxing" - 4-piece: +5% ATK per attack taken/given, up to 5 stacks.
     */
    private static final int STREETWISE_BOXING = 105;
    /**
     * "City of Converging Stars" (planar) - 2-piece: on Follow-Up ATK +24% ATK; on a kill +12% CRIT DMG.
     */
    private static final int CONVERGING_STARS = 326;
    /**
     * "The Ashblazing Grand Duke" - 2-piece: +20% DMG dealt by Follow-Up ATK.
     */
    private static final int ASHBLAZING = 115;

    /**
     * "the Navigator who sees the stars as I do" (星如我见的领航员) - 4-piece: a Skill/Ultimate DMG stack counter that grows and shrinks.
     */
    private static final int NAVIGATOR = 131;
    /**
     * "the Iron Guard that wards the wind and snow" (戍卫风雪的铁卫) - 2-piece: reduces the damage the wearer takes (a damage-taken zone).
     */
    private static final int GUARD_OF_SNOW = 106;

    /**
     * Himeko: basic attack / skill / ultimate are all real, damaging skill slots.
     */
    private static final int HIMEKO = 1003;
    private static final int ICE_EDGE = 1002011;
    private static final int ENEMY_LEVEL = 90;
    private static final int CHARACTER_LEVEL = 80;

    /**
     * Enough HP that a cast never kills the enemy mid-test.
     */
    private static final double ENEMY_HP = 1_000_000;

    private static final int STAR = 5;
    private static final int LEVEL = 15;

    /**
     * How many pieces a partial suit wears - one short of every set's 4-piece tier.
     */
    private static final int PARTIAL_PIECES = 3;

    /**
     * The four cavern slots, in the order a partial suit fills them.
     */
    private static final List<RelicType> CAVERN_SLOTS =
            List.of(RelicType.HEAD, RelicType.HAND, RelicType.BODY, RelicType.BOOT);

    /**
     * The authored numbers, so an assertion can never silently drift from the data file.
     */
    private static final double GLACIAL_CRIT_DMG = 0.25;
    private static final double SIZZLING_ATTACK = 0.2;
    private static final double EAGLE_ADVANCE = 0.25;
    /**
     * Ability51051: +5% ATK per application, at most 5 applications.
     */
    private static final double STREETWISE_ATTACK_PER_STACK = 0.05;
    private static final int STREETWISE_MAX_STACKS = 5;

    private static final double TOLERANCE = 1e-9;

    // ==================================================================
    // 1. F-2: the opening skill points (set 101)
    // ==================================================================

    /**
     * Set 101's whole opening claim in one place: a team without the set starts at 3; with one 4-piece
     * wearer it starts at 4; with two wearers, 5; and three pieces is one short, so it stays at 3.
     *
     * <p>Each case is measured against the same team without the set, so nothing but the set bonus can
     * explain the difference.
     *
     * <p>The test also states the <b>ordering</b> finding: the opening value exists <b>before</b>
     * {@code BATTLE_START} fires, and the set rule is what raises it. {@code Battle}'s constructor
     * installs the skill-point policy (start {@link Constant#SKILL_POINT_START}); {@code startBattle()}
     * fires {@code BATTLE_START} later. So the direction of the bonus is "3, then +1", and the
     * observable opening value is 4. Had the trigger fired before the opening assignment, the +1 would
     * have been overwritten and the registered F-2 claim would be unimplementable - this scenario is what
     * catches such a reordering.
     */
    @Test
    public void passerbyOpeningSkillPointsScenario() {
        Battle control = newBattle(List.of(plain(HIMEKO), plain(HIMEKO)), true);
        int controlPoints = control.getSkillPoints();

        Battle one = newBattle(List.of(wearing(HIMEKO, PASSERBY), plain(HIMEKO)), true);
        int oneWearerPoints = one.getSkillPoints();

        Battle two = newBattle(List.of(wearing(HIMEKO, PASSERBY), wearing(HIMEKO, PASSERBY)), true);
        int twoWearerPoints = two.getSkillPoints();

        Battle threePieces = newBattle(List.of(partial(HIMEKO, PASSERBY)), true);
        int partialPoints = threePieces.getSkillPoints();

        Battle notStarted = newBattle(List.of(wearing(HIMEKO, PASSERBY)), false);
        int pointsBeforeStart = notStarted.getSkillPoints();
        notStarted.startBattle();
        int pointsAfterStart = notStarted.getSkillPoints();

        Assertions.assertAll("F-2: the Passerby of Wandering Cloud 4-piece raises the opening skill points",
                () -> Assertions.assertEquals(3, Constant.SKILL_POINT_START,
                        "the engine's conventional opening value"),
                () -> Assertions.assertEquals(3, controlPoints,
                        "without the set the team still opens at the conventional 3"),
                () -> Assertions.assertEquals(4, oneWearerPoints,
                        "F-2: a Passerby 4-piece wearer makes the team open at 4 (3 + 1 from Ability51011)"),
                () -> Assertions.assertEquals(5, twoWearerPoints,
                        "two wearers each contribute 1, capped at the conventional maximum of "
                                + Constant.SKILL_POINT_MAX),
                () -> Assertions.assertEquals(3, partialPoints,
                        "three pieces of set 101 must not grant the 4-piece ability"),
                () -> Assertions.assertEquals(3, pointsBeforeStart,
                        "the conventional start value is in place before startBattle()"),
                () -> Assertions.assertEquals(4, pointsAfterStart,
                        "BATTLE_START is fired by startBattle() and adds the set's point on top of the opening 3"));
    }

    // ==================================================================
    // 2. The three in-battle abilities (sets 104, 109, 110)
    // ==================================================================

    /**
     * Set 104: after the wearer's Ultimate, +25% CRIT DMG for 2 turns - and three pieces grant nothing.
     *
     * <p>{@code CRIT DMG} is a <b>ratio</b> attribute, so the rule's {@code percent} is an absolute
     * addition to it (0.5 to 0.5), not a factor. That is asserted through the modifier the rule
     * actually installed as well as through the final value, so "the buff is there with the right
     * number" cannot pass by accident.
     */
    @Test
    public void glacialForestUltimateCritDamageScenario() {
        Battle battle = newBattle(List.of(wearing(HIMEKO, GLACIAL_FOREST)), true);
        Character hero = battle.characters().getFirst();
        double before = hero.getAttribute(AttributeType.CRIT_ATTACK).get();
        boolean cast = castUltimate(battle, hero);
        List<DoubleValue.Modifier> buffs = buffsOn(hero, AttributeType.CRIT_ATTACK);
        double after = hero.getAttribute(AttributeType.CRIT_ATTACK).get();

        Battle partialBattle = newBattle(List.of(partial(HIMEKO, GLACIAL_FOREST)), true);
        Character control = partialBattle.characters().getFirst();
        boolean controlCast = castUltimate(partialBattle, control);
        boolean controlUntouched = buffsOn(control, AttributeType.CRIT_ATTACK).isEmpty();

        Assertions.assertAll("set 104: +25% CRIT DMG after the wearer's Ultimate",
                () -> Assertions.assertTrue(cast, "the wearer's Ultimate really was cast"),
                () -> Assertions.assertEquals(1, buffs.size(),
                        "Ability51041 must install exactly one CRIT DMG buff; the 2-piece tier of this set is "
                                + "Ice DMG, so nothing else touches this attribute"),
                () -> Assertions.assertEquals(GLACIAL_CRIT_DMG, buffs.getFirst().getValue(), TOLERANCE,
                        "param #1 is 0.25, and for a ratio attribute that is 25 percentage points"),
                () -> Assertions.assertEquals(before + GLACIAL_CRIT_DMG, after, TOLERANCE,
                        "Ability51041: +25% CRIT DMG after the wearer's Ultimate"),
                () -> Assertions.assertTrue(controlCast, "the three-piece control's Ultimate really was cast"),
                () -> Assertions.assertTrue(controlUntouched, "three pieces must not grant the 4-piece ability"));
    }

    /**
     * Set 109: on the wearer's <b>Skill</b>, +20% ATK - and <b>not</b> on the Ultimate.
     *
     * <p>Two separate battles rather than one, because re-applying the same stat buff only
     * <em>refreshes</em> it ({@code StatModifierBuff.isSameKind}), so "did it fire again?" would be
     * invisible in a single run. The control battle casts only the ultimate: its ATK must be untouched
     * there.
     *
     * <p>ATK is a <b>base</b> attribute, so the rule's {@code percent} is an additive percentage of
     * the base ({@code base  x  1.2 + flat}), which is why the expectation is stated on the modifier
     * rather than on a simple ratio of the final value.
     */
    @Test
    public void sizzlingThunderSkillAttackScenario() {
        Battle onSkill = newBattle(List.of(wearing(HIMEKO, SIZZLING_THUNDER)), true);
        Character skillUser = onSkill.characters().getFirst();
        double skillBefore = skillUser.getAttribute(AttributeType.ATTACK).get();
        onSkill.castImmediate(skillUser.getSkills().get(SkillType.SKILL), skillUser,
                List.of(onSkill.enemyUnits().getFirst()));

        List<DoubleValue.Modifier> buffs = buffsOn(skillUser, AttributeType.ATTACK);
        double skillAfter = skillUser.getAttribute(AttributeType.ATTACK).get();

        Battle onUltimate = newBattle(List.of(wearing(HIMEKO, SIZZLING_THUNDER)), true);
        Character ultimateUser = onUltimate.characters().getFirst();
        double ultimateBefore = ultimateUser.getAttribute(AttributeType.ATTACK).get();
        boolean ultimateCast = castUltimate(onUltimate, ultimateUser);
        boolean ultimateUntouched = buffsOn(ultimateUser, AttributeType.ATTACK).isEmpty();
        double ultimateAfter = ultimateUser.getAttribute(AttributeType.ATTACK).get();

        Assertions.assertAll("set 109: +20% ATK on the Skill, and not on the Ultimate",
                () -> Assertions.assertEquals(1, buffs.size(),
                        "Ability51091 must install exactly one ATK buff when the wearer uses their Skill"),
                () -> Assertions.assertEquals(SIZZLING_ATTACK, buffs.getFirst().getValue(), TOLERANCE,
                        "param #1 is 0.2"),
                () -> Assertions.assertEquals(DoubleValue.Modifier.ModifierType.ADD_PERCENT,
                        buffs.getFirst().getModifierType(),
                        "ATK is a base attribute, so the buff is an additive percentage, not a flat value"),
                () -> Assertions.assertTrue(skillAfter > skillBefore,
                        "the buff must actually raise the attribute"),
                () -> Assertions.assertTrue(ultimateCast, "the control's Ultimate really was cast"),
                () -> Assertions.assertTrue(ultimateUntouched,
                        "an Ultimate is not a Skill: ULT_CAST and SKILL_CAST are mutually exclusive, so the "
                                + "20% ATK must not arrive here"),
                () -> Assertions.assertEquals(ultimateBefore, ultimateAfter, TOLERANCE,
                        "so the Ultimate leaves ATK exactly where it was"));
    }

    /**
     * Set 110: after the wearer's Ultimate, their action is advanced forward by 25% - and three pieces
     * leave the action bar alone.
     */
    @Test
    public void twilightEagleUltimateAdvanceScenario() {
        Battle battle = newBattle(List.of(wearing(HIMEKO, TWILIGHT_EAGLE)), true);
        Character hero = battle.characters().getFirst();
        double remainingBefore = timeRemaining(battle, hero);
        boolean cast = castUltimate(battle, hero);
        double remainingAfter = timeRemaining(battle, hero);

        Battle partialBattle = newBattle(List.of(partial(HIMEKO, TWILIGHT_EAGLE)), true);
        Character control = partialBattle.characters().getFirst();
        double controlBefore = timeRemaining(partialBattle, control);
        boolean controlCast = castUltimate(partialBattle, control);
        double controlAfter = timeRemaining(partialBattle, control);

        Assertions.assertAll("set 110: after the Ultimate the wearer advances forward 25%",
                () -> Assertions.assertTrue(remainingBefore > 0,
                        "precondition: the wearer has time left before acting"),
                () -> Assertions.assertTrue(cast, "the wearer's Ultimate really was cast"),
                () -> Assertions.assertEquals(remainingBefore * (1 - EAGLE_ADVANCE), remainingAfter, 1e-6,
                        "Ability51101: the wearer skips 25% of their remaining time to act"),
                () -> Assertions.assertTrue(controlCast, "the three-piece control's Ultimate really was cast"),
                () -> Assertions.assertEquals(controlBefore, controlAfter, 1e-6,
                        "three pieces must not grant the 4-piece ability"));
    }

    // ==================================================================
    // 3. The stackable / unbounded ability (set 105)
    // ==================================================================

    /**
     * Ability51051, the "attacks or is hit" ability: each application adds one stack of +5% ATK, the
     * sixth application changes nothing, a shielded hit still counts, the stacks are permanent, and
     * three pieces grant none of it.
     *
     * <p>This is the end-to-end claim for the stacking primitive and for the {@code TAKING_HIT} event at
     * once. Three things are asserted separately because they fail differently:
     * <ul>
     *   <li><b>The two halves of the disjunction share one cap.</b> Four attacks plus three hits is
     *       seven applications of a 5-stack ability, so the total must be exactly 5 - which is only true
     *       if {@code ALLY_ATTACK} and {@code TAKING_HIT} accumulate into the <b>same</b> stack group.
     *       (With one counter per rule the total would be 8, and the test would still pass a naive
     *       "did anything accumulate?" check.)</li>
     *   <li><b>The "is hit" half is {@code TAKING_HIT}, not {@code HP_LOST}.</b> The hits taken here are
     *       <b>fully absorbed by a shield</b>, so no HP is lost: had the rule been hung on
     *       {@code HP_LOST} instead, the stack count would stop at 4. That is the exact scenario the
     *       distinction exists for, and the second (single shielded hit) fixture states it without a cap
     *       in the way.</li>
     *   <li><b>The stacks actually reach the attribute.</b> Each one is checked to be an
     *       {@code ADD_PERCENT} of 0.05 - the kind a <b>base</b> attribute needs, because ATK is
     *       {@code base  x  (1 + sum of percentages)}: the total is deliberately <em>not</em> asserted as a
     *       ratio of the character's ATK, since the character carries several other sources of the same
     *       percentage (traces, relics, and this set's own 2-piece stat bonus).</li>
     * </ul>
     */
    @Test
    public void streetwiseBoxingStackingScenario() {
        Battle battle = newBattle(List.of(wearing(HIMEKO, STREETWISE_BOXING)), true);
        Character hero = battle.characters().getFirst();
        int baseBuffs = buffsOn(hero, AttributeType.ATTACK).size();
        double attackWithoutStacks = hero.getAttribute(AttributeType.ATTACK).get();

        // Six of the wearer's own attacks: the sixth application is already past the cap.
        for (int i = 0; i < 6; i++) {
            battle.castImmediate(hero.getSkills().get(SkillType.COMMON), hero,
                    List.of(battle.enemyUnits().getFirst()));
        }
        int buffsAfterSixAttacks = buffsOn(hero, AttributeType.ATTACK).size();
        List<DoubleValue.Modifier> stacksAfterSixAttacks = buffsOn(hero, AttributeType.ATTACK);
        double attackAfterSixAttacks = hero.getAttribute(AttributeType.ATTACK).get();

        // Three hits taken, all of them swallowed by a shield: the cap is already reached, so these add
        // nothing - and they prove the "is hit" half is wired, because HP_LOST never fires here.
        battle.grantShield(hero, 10_000_000);
        double hpBefore = hero.getCurrentHp();
        for (int i = 0; i < 3; i++) {
            battle.applyDamage(hero, new Damage(battle.enemyUnits().getFirst(), hero,
                    DamageElement.PHYSICAL, 1_000));
        }
        double hpAfterShieldedHits = hero.getCurrentHp();
        int buffsAfterShieldedHits = buffsOn(hero, AttributeType.ATTACK).size();
        List<DoubleValue.Modifier> stacksAfterShieldedHits = buffsOn(hero, AttributeType.ATTACK);

        // The "is hit" half on its own: a shielded hit is still a hit, so it adds a stack.
        Battle hitOnly = newBattle(List.of(wearing(HIMEKO, STREETWISE_BOXING)), true);
        Character hitHero = hitOnly.characters().getFirst();
        int hitOnlyBaseBuffs = buffsOn(hitHero, AttributeType.ATTACK).size();
        hitOnly.grantShield(hitHero, 10_000_000);
        double hitOnlyHpBefore = hitHero.getCurrentHp();
        hitOnly.applyDamage(hitHero, new Damage(hitOnly.enemyUnits().getFirst(), hitHero,
                DamageElement.PHYSICAL, 1_000));
        double hitOnlyHpAfter = hitHero.getCurrentHp();
        int buffsAfterOneShieldedHit = buffsOn(hitHero, AttributeType.ATTACK).size();

        // "For the rest of the battle": the stacks survive every turn boundary.
        Battle lasting = newBattle(List.of(wearing(HIMEKO, STREETWISE_BOXING)), true);
        Character lastingHero = lasting.characters().getFirst();
        int lastingBaseBuffs = buffsOn(lastingHero, AttributeType.ATTACK).size();
        lasting.castImmediate(lastingHero.getSkills().get(SkillType.COMMON), lastingHero,
                List.of(lasting.enemyUnits().getFirst()));
        int buffsAfterOneAttack = buffsOn(lastingHero, AttributeType.ATTACK).size();
        for (int turn = 0; turn < 10; turn++) {
            lasting.stepForward();
            if (lasting.currentMove() == null) {
                break;
            }
            lasting.beforeMove();
            lasting.afterMove();
        }
        int buffsAfterTenTurns = buffsOn(lastingHero, AttributeType.ATTACK).size();
        List<DoubleValue.Modifier> stacksAfterTenTurns = buffsOn(lastingHero, AttributeType.ATTACK);

        // The threshold: three pieces must not grant the 4-piece ability.
        Battle partialBattle = newBattle(List.of(partial(HIMEKO, STREETWISE_BOXING)), true);
        Character partialHero = partialBattle.characters().getFirst();
        double partialBaseAttack = partialHero.getAttribute(AttributeType.ATTACK).get();
        partialBattle.castImmediate(partialHero.getSkills().get(SkillType.COMMON), partialHero,
                List.of(partialBattle.enemyUnits().getFirst()));
        boolean partialUntouched = buffsOn(partialHero, AttributeType.ATTACK).isEmpty();
        double partialAttackAfter = partialHero.getAttribute(AttributeType.ATTACK).get();

        Assertions.assertAll("set 105: +5% ATK per attack given or taken, up to 5 stacks",
                () -> Assertions.assertEquals(baseBuffs + STREETWISE_MAX_STACKS, buffsAfterSixAttacks,
                        "six attacks stop at the 5-stack cap from Ability51051's own text"),
                () -> assertEachStackIsStreetwise(stacksAfterSixAttacks),
                () -> Assertions.assertTrue(attackAfterSixAttacks > attackWithoutStacks,
                        "the stacks must reach the attribute"),
                () -> Assertions.assertEquals(hpBefore, hpAfterShieldedHits, TOLERANCE,
                        "precondition: the shield absorbed every hit, so no HP was lost"),
                () -> Assertions.assertEquals(baseBuffs + STREETWISE_MAX_STACKS, buffsAfterShieldedHits,
                        "TAKING_HIT must add stacks to the SAME counter as ALLY_ATTACK, and the shared cap is 5"),
                () -> assertEachStackIsStreetwise(stacksAfterShieldedHits),
                () -> Assertions.assertEquals(hitOnlyHpBefore, hitOnlyHpAfter, TOLERANCE,
                        "precondition: the shield ate the whole hit"),
                () -> Assertions.assertEquals(hitOnlyBaseBuffs + 1, buffsAfterOneShieldedHit,
                        "a hit the shield absorbed is still 'being hit', so the set must gain a stack"),
                () -> Assertions.assertEquals(lastingBaseBuffs + 1, buffsAfterOneAttack,
                        "one basic attack adds one stack"),
                () -> Assertions.assertEquals(lastingBaseBuffs + 1, buffsAfterTenTurns,
                        "a 'permanent' modifier has no turn limit, so no number of turns may remove it"),
                () -> assertEachStackIsStreetwise(stacksAfterTenTurns),
                () -> Assertions.assertTrue(partialUntouched,
                        "three pieces is one short of the 4-piece bonus"),
                () -> Assertions.assertEquals(partialBaseAttack, partialAttackAfter, TOLERANCE,
                        "so the three-piece wearer's ATK is left exactly where it started"));
    }

    /**
     * Every installed stack carries the modifier the ability states, of the kind a base attribute needs.
     */
    private static void assertEachStackIsStreetwise(List<DoubleValue.Modifier> stacks) {
        for (DoubleValue.Modifier modifier : stacks) {
            Assertions.assertEquals(STREETWISE_ATTACK_PER_STACK, modifier.getValue(), TOLERANCE,
                    "Ability51051: each stack is +5% ATK (param #1[i] = 0.05)");
            Assertions.assertEquals(DoubleValue.Modifier.ModifierType.ADD_PERCENT,
                    modifier.getModifierType(),
                    "ATK is a base attribute, so each stack is an additive percentage");
        }
    }

    // ==================================================================
    // 4. FOLLOW_UP: City of Converging Stars (326)
    // ==================================================================

    /**
     * Ability53261: on Follow-Up ATK, +24% ATK for 2 turns; on a kill, +12% CRIT DMG for all allies.
     */
    private static final double CONVERGING_ATTACK = 0.24;
    private static final double CONVERGING_CRIT_DMG = 0.12;

    /**
     * The wearer's own follow-up attack raises their ATK by the authored amount; a plain basic attack
     * leaves it alone; and "this effect cannot stack", so a second follow-up only refreshes.
     *
     * <p>This is the end-to-end proof that {@code FOLLOW_UP} has a real emitter: the event is fired from
     * {@code Battle.applyAdditionalDamage}, the rule lives in {@code resources/game/relic_sets/326.json}, and
     * neither half is visible unless both work. Asserting on the resulting <b>attribute value</b> rather
     * than on "a modifier exists" is deliberate - a rule that fired but applied nothing would pass the
     * weaker check.
     *
     * <p>The basic-attack control is why {@code FOLLOW_UP} exists instead of reusing {@code ALLY_ATTACK}:
     * a rule hung on the latter would fire there too, which the set's text does not allow. The second
     * follow-up pins the non-stacking reading - a stackable one would give +48% ATK and two modifiers.
     */
    @Test
    public void convergingStarsFollowUpAttackScenario() {
        Character himeko = wearing(HIMEKO, CONVERGING_STARS);
        Battle battle = newBattle(List.of(himeko), true);
        double before = himeko.getAttribute(AttributeType.ATTACK).get();

        battle.applyAdditionalDamage(himeko, battle.enemyUnits().getFirst(), DamageElement.FIRE, 100);

        List<DoubleValue.Modifier> buffs = buffsOn(himeko, AttributeType.ATTACK);
        double after = himeko.getAttribute(AttributeType.ATTACK).get();

        // A follow-up attack is not just "an attack": a plain basic attack must leave the bonus alone.
        Character basicHero = wearing(HIMEKO, CONVERGING_STARS);
        Battle basicBattle = newBattle(List.of(basicHero), true);
        double basicBefore = basicHero.getAttribute(AttributeType.ATTACK).get();
        basicBattle.castImmediate(basicHero.getSkills().get(SkillType.COMMON), basicHero,
                List.of(basicBattle.enemyUnits().getFirst()));
        double basicAfter = basicHero.getAttribute(AttributeType.ATTACK).get();

        // "This effect cannot stack": a second follow-up refreshes rather than accumulating.
        Character stackingHero = wearing(HIMEKO, CONVERGING_STARS);
        Battle stackingBattle = newBattle(List.of(stackingHero), true);
        double base = stackingHero.getAttribute(AttributeType.ATTACK).get();
        stackingBattle.applyAdditionalDamage(stackingHero, stackingBattle.enemyUnits().getFirst(),
                DamageElement.FIRE, 100);
        double afterFirst = stackingHero.getAttribute(AttributeType.ATTACK).get();
        stackingBattle.applyAdditionalDamage(stackingHero, stackingBattle.enemyUnits().getFirst(),
                DamageElement.FIRE, 100);
        List<DoubleValue.Modifier> stackBuffs = buffsOn(stackingHero, AttributeType.ATTACK);
        double afterSecond = stackingHero.getAttribute(AttributeType.ATTACK).get();

        Assertions.assertAll("set 326: +24% ATK on the wearer's Follow-Up ATK, once",
                () -> Assertions.assertEquals(1, buffs.size(),
                        "Ability53261 must install exactly one ATK buff when the wearer uses a Follow-Up ATK"),
                () -> Assertions.assertEquals(CONVERGING_ATTACK, buffs.getFirst().getValue(), TOLERANCE,
                        "param #1 is 0.24"),
                () -> Assertions.assertEquals(DoubleValue.Modifier.ModifierType.ADD_PERCENT,
                        buffs.getFirst().getModifierType(),
                        "ATK is a base attribute, so the buff is an additive percentage, not a flat value"),
                () -> Assertions.assertTrue(after > before, "the buff must actually raise the attribute"),
                () -> Assertions.assertEquals(basicBefore, basicAfter, TOLERANCE,
                        "only an additional-damage instance is a follow-up attack; a basic attack is not"),
                () -> Assertions.assertEquals(1, stackBuffs.size(),
                        "two follow-ups must leave one modifier, not two"),
                () -> Assertions.assertEquals(CONVERGING_ATTACK, stackBuffs.getFirst().getValue(), TOLERANCE,
                        "the second follow-up refreshes the same bonus instead of adding another"),
                () -> Assertions.assertEquals(afterFirst, afterSecond, TOLERANCE,
                        "so the value after the second follow-up is the same as after the first, not higher"),
                () -> Assertions.assertTrue(afterFirst > base,
                        "and it really did move off the base value"));
    }

    /**
     * "on a kill, +12% CRIT DMG for all allies": the kill fires from the follow-up's settlement, and the
     * bonus reaches a team-mate, not just the wearer.
     */
    @Test
    public void convergingStarsTeamCritDamageOnKillScenario() {
        Character himeko = wearing(HIMEKO, CONVERGING_STARS);
        Character mate = plain(HIMEKO);
        Battle battle = newBattle(List.of(himeko, mate), true);
        Enemy enemy = battle.enemyUnits().getFirst();
        double wearerBefore = himeko.getAttribute(AttributeType.CRIT_ATTACK).get();
        double mateBefore = mate.getAttribute(AttributeType.CRIT_ATTACK).get();

        // One instance big enough to kill: the KILL event fires from applyDamage's settlement.
        battle.applyAdditionalDamage(himeko, enemy, DamageElement.FIRE, ENEMY_HP * 2);
        boolean enemyDied = enemy.isDeath();
        double wearerAfter = himeko.getAttribute(AttributeType.CRIT_ATTACK).get();
        double mateAfter = mate.getAttribute(AttributeType.CRIT_ATTACK).get();

        Assertions.assertAll("set 326: on a kill, +12% CRIT DMG for all allies",
                () -> Assertions.assertTrue(enemyDied, "the enemy really did die, so KILL fired"),
                () -> Assertions.assertEquals(wearerBefore + CONVERGING_CRIT_DMG, wearerAfter, 1e-9,
                        "CRIT DMG is a ratio attribute, so the value is the bonus itself (12 percentage points)"),
                () -> Assertions.assertEquals(mateBefore + CONVERGING_CRIT_DMG, mateAfter, 1e-9,
                        "'for all allies' must reach a team-mate, not just the wearer"));
    }

    // ==================================================================
    // 5. The follow-up-only damage boost: The Ashblazing Grand Duke (115)
    // ==================================================================

    /**
     * Ability51150: "Increases the DMG dealt by Follow-Up ATK by 20%."
     */
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
     * percentage points on that sum, not a factor of 1.2 on the final number - the same trap the ATK
     * buffs above fell into.
     */
    @Test
    public void ashblazingFollowUpDamageScenario() {
        Battle withBoost = newBattle(List.of(wearing(HIMEKO, ASHBLAZING)), true);
        Battle withoutBoost = newBattle(List.of(wearing(HIMEKO, ASHBLAZING)), true);
        withoutBoost.characters().getFirst().getBuffManager().clearAll();

        Character himeko = withBoost.characters().getFirst();
        double boostOnWearer = himeko.getAttribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST).get();
        double boostInControl = withoutBoost.characters().getFirst()
                .getAttribute(AttributeType.FOLLOW_UP_DAMAGE_BOOST).get();

        // A fixed crit state keeps both battles numerically comparable.
        for (Battle battle : List.of(withBoost, withoutBoost)) {
            battle.characters().getFirst().setAttribute(AttributeType.CRIT_CHANCE, new DoubleValue(0));
        }

        double followUpWith = hitFor(withBoost, true);
        double followUpWithout = hitFor(withoutBoost, true);
        double normalWith = hitFor(withBoost, false);
        double normalWithout = hitFor(withoutBoost, false);

        Assertions.assertAll("set 115: +20% DMG on Follow-Up ATK, and nothing else",
                () -> Assertions.assertEquals(ASHBLAZING_BOOST, boostOnWearer, TOLERANCE,
                        "the 2-piece rule grants the follow-up-only boost at battle start"),
                () -> Assertions.assertEquals(0, boostInControl, TOLERANCE,
                        "and clearing the buffs removes it, so the control battle really is the control"),
                () -> Assertions.assertTrue(followUpWith > followUpWithout,
                        "a follow-up attack must hit harder with the boost: " + followUpWith
                                + " vs " + followUpWithout),
                () -> Assertions.assertEquals(normalWith, normalWithout, TOLERANCE,
                        "a basic attack must be completely unaffected: " + normalWith + " vs " + normalWithout));
    }

    /**
     * Settles one hit and returns how much HP the enemy lost.
     */
    private static double hitFor(Battle battle, boolean followUp) {
        Enemy enemy = battle.enemyUnits().getFirst();
        Character hero = battle.characters().getFirst();
        double before = enemy.getCurrentHp();
        if (followUp) {
            battle.applyAdditionalDamage(hero, enemy, DamageElement.FIRE, 1000);
        } else {
            battle.castImmediate(hero.getSkills().get(SkillType.COMMON), hero, List.of(enemy));
        }
        return before - enemy.getCurrentHp();
    }

    // ==================================================================
    // 6. 131 "the Navigator who sees the stars as I do" (星如我见的领航员): the stack counter that also comes back down
    // ==================================================================

    /**
     * One stack of the set's boost (param #1[i] = 0.18).
     */
    private static final double NAVIGATOR_BOOST = 0.18;
    /**
     * The text's cap (param #2[i] = 3).
     */
    private static final int NAVIGATOR_MAX_STACKS = 3;

    /**
     * The whole 4-piece ability, in one battle: enter to 1 stack; turn start to 1 off; Skill to 1 on, up to 3;
     * turn start to 1 off again - and, on a second fixture, the Ultimate's removal clause and the
     * three-piece threshold.
     *
     * <p><b>Why this is the interesting one.</b> Every other set ability the engine has is monotonic - a buff
     * that appears, or a counter that only grows. This one says "…stacking up to 3 time(s) … removes 1
     * stack(s)", so it needed an op that takes a stack <b>back</b> ({@code REMOVE_STACK}); before that it
     * could only have been modelled by dropping the second half of the text, which is why it sat in
     * {@code _unmodelled.json}. Both directions are asserted here, and through a real battle, so the claim
     * "the set works" is about the engine executing the file rather than about the file existing.
     *
     * <p>"or after casting the Ultimate, remove 1 layer" is the other half of the removal clause, so it
     * needs a fresh battle (the first one has already had its turn-start removals); the three-piece guard
     * is the same threshold check the other sets carry.
     */
    @Test
    public void navigatorStackCounterScenario() {
        Battle battle = newBattle(List.of(wearing(HIMEKO, NAVIGATOR)), true);
        Character hero = battle.characters().getFirst();

        int skillStacksOnEntry = buffsOn(hero, AttributeType.SKILL_DAMAGE_BOOST).size();
        int ultimateStacksOnEntry = buffsOn(hero, AttributeType.ULTIMATE_DAMAGE_BOOST).size();
        double valueOnEntry = hero.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get();

        takeTurnOf(battle, hero);
        int skillStacksAfterFirstTurn = buffsOn(hero, AttributeType.SKILL_DAMAGE_BOOST).size();

        for (int cast = 0; cast < NAVIGATOR_MAX_STACKS + 1; cast++) {
            castSkill(battle, hero);
        }
        int skillStacksAfterCasts = buffsOn(hero, AttributeType.SKILL_DAMAGE_BOOST).size();
        double valueAfterCasts = hero.getAttribute(AttributeType.SKILL_DAMAGE_BOOST).get();

        takeTurnOf(battle, hero);
        int skillStacksAfterSecondTurn = buffsOn(hero, AttributeType.SKILL_DAMAGE_BOOST).size();

        Battle ultimateBattle = newBattle(List.of(wearing(HIMEKO, NAVIGATOR)), true);
        Character ultimateHero = ultimateBattle.characters().getFirst();
        castSkill(ultimateBattle, ultimateHero);
        int ultimateStacksBeforeUltimate = buffsOn(ultimateHero, AttributeType.ULTIMATE_DAMAGE_BOOST).size();
        boolean ultimateCast = castUltimate(ultimateBattle, ultimateHero);
        int ultimateStacksAfterUltimate = buffsOn(ultimateHero, AttributeType.ULTIMATE_DAMAGE_BOOST).size();
        int skillStacksAfterUltimate = buffsOn(ultimateHero, AttributeType.SKILL_DAMAGE_BOOST).size();

        Battle partialBattle = newBattle(List.of(partial(HIMEKO, NAVIGATOR)), true);
        Character partialHero = partialBattle.characters().getFirst();
        int partialSkillStacks = buffsOn(partialHero, AttributeType.SKILL_DAMAGE_BOOST).size();
        int partialUltimateStacks = buffsOn(partialHero, AttributeType.ULTIMATE_DAMAGE_BOOST).size();

        Assertions.assertAll("set 131: the Skill/Ultimate DMG stack counter that grows and shrinks",
                () -> Assertions.assertEquals(1, skillStacksOnEntry,
                        "entering combat grants one stack of the Skill DMG boost"),
                () -> Assertions.assertEquals(1, ultimateStacksOnEntry,
                        "and one of the Ultimate DMG boost -- the text names both scopes"),
                () -> Assertions.assertEquals(NAVIGATOR_BOOST, valueOnEntry, TOLERANCE, "one stack is 18%"),
                () -> Assertions.assertEquals(0, skillStacksAfterFirstTurn,
                        "the start of the wearer's turn removes one -- which is exactly the stack entering combat gave"),
                () -> Assertions.assertEquals(NAVIGATOR_MAX_STACKS, skillStacksAfterCasts,
                        "each Skill grants one, up to the cap of 3 -- the fourth changes nothing"),
                () -> Assertions.assertEquals(NAVIGATOR_MAX_STACKS * NAVIGATOR_BOOST, valueAfterCasts, TOLERANCE,
                        "and the scoped boost adds up stack by stack (one attribute per stack, removed by id)"),
                () -> Assertions.assertEquals(NAVIGATOR_MAX_STACKS - 1, skillStacksAfterSecondTurn,
                        "one comes off per turn start, so the counter never just sits at the cap"),
                () -> Assertions.assertEquals(2, ultimateStacksBeforeUltimate,
                        "precondition: one stack from entering combat, one from the Skill"),
                () -> Assertions.assertTrue(ultimateCast, "the wearer's Ultimate really was cast"),
                () -> Assertions.assertEquals(1, ultimateStacksAfterUltimate,
                        "after the wearer's Ultimate, one stack of each scope comes off"),
                () -> Assertions.assertEquals(1, skillStacksAfterUltimate,
                        "the Skill scope loses one too -- the text does not distinguish them"),
                () -> Assertions.assertEquals(0, partialSkillStacks,
                        "three pieces must not grant the 4-piece ability: no Skill scope stack"),
                () -> Assertions.assertEquals(0, partialUltimateStacks,
                        "and no Ultimate scope stack either"));
    }

    // ==================================================================
    // 7. 106 "the Iron Guard that wards the wind and snow" (戍卫风雪的铁卫): a damage-taken zone the data could not reach
    // ==================================================================

    /**
     * The 2-piece's number (param #1[i] = 0.08).
     */
    private static final double SNOW_REDUCTION = 0.08;

    /**
     * Set 106's whole ability: the 2-piece's 8% damage reduction (measured on the same wearer with the buff
     * and without it), the 2-piece tier's reach (three pieces are enough, none is not), and the 4-piece's
     * "at the start of the wearer's turn, below half health, heal 8% of Max HP and give 5 Energy" - plus
     * the same turn start doing nothing above the threshold.
     *
     * <p>"reduces the damage taken by 8%" is deliberately measured twice <b>on the same wearer in the
     * same battle</b>: once with the buff the rule installed and once after removing it. Comparing "a
     * character wearing the set" against "a character wearing nothing" would have been meaningless here,
     * because the relics themselves carry main and sub stats (DEF, HP) - the damage would differ for
     * reasons that have nothing to do with the 8%. Removing one buff leaves everything else identical, so
     * the ratio is the set's rule and nothing else. `TOLERANCE` is the same 1e-9 the other cases use.
     *
     * <p>The heal is the interesting half in engine terms: 8% of Max HP is a different number for every
     * character and level, so it is written as {@code scale: target_max_hp} rather than as a number - which is
     * why this ability was registered as unmodellable until that spelling existed. The two 4-piece cases
     * are the complement of one threshold, so they share the fixture and differ only in the damage taken
     * before the turn starts.
     */
    @Test
    public void guardOfSnowDamageReductionAndHealScenario() {
        Battle battle = newBattle(List.of(wearing(HIMEKO, GUARD_OF_SNOW)), true);
        Character hero = battle.characters().getFirst();
        boolean reductionAttached = hero.getBuffManager().hasBuff(ReductionBuff.class);

        double withReduction = smallHitOn(battle, hero);
        boolean removed = hero.getBuffManager().removeOneBuff(ReductionBuff.class);
        double without = smallHitOn(battle, hero);

        Battle threePieces = newBattle(List.of(partial(HIMEKO, GUARD_OF_SNOW)), true);
        boolean partialHasReduction = threePieces.characters().getFirst()
                .getBuffManager().hasBuff(ReductionBuff.class);
        Battle none = newBattle(List.of(plain(HIMEKO)), true);
        boolean plainHasReduction = none.characters().getFirst().getBuffManager().hasBuff(ReductionBuff.class);

        Battle lowBattle = newBattle(List.of(wearing(HIMEKO, GUARD_OF_SNOW)), true);
        Character lowHero = lowBattle.characters().getFirst();
        double maxHp = lowHero.getMaxHp();
        lowHero.takeDamage(maxHp * 0.6);                  // down to 40%: below the threshold
        double hpBefore = lowHero.getCurrentHp();
        double energyBefore = lowHero.getCurrentEnergy();
        takeTurnOf(lowBattle, lowHero);
        double hpAfter = lowHero.getCurrentHp();
        double energyAfter = lowHero.getCurrentEnergy();

        Battle highBattle = newBattle(List.of(wearing(HIMEKO, GUARD_OF_SNOW)), true);
        Character highHero = highBattle.characters().getFirst();
        highHero.takeDamage(highHero.getMaxHp() * 0.2);   // 80%: above the threshold
        double highHpBefore = highHero.getCurrentHp();
        double highEnergyBefore = highHero.getCurrentEnergy();
        takeTurnOf(highBattle, highHero);
        double highHpAfter = highHero.getCurrentHp();
        double highEnergyAfter = highHero.getCurrentEnergy();

        Assertions.assertAll("set 106: 8% damage reduction (2-piece) and the 8%-Max-HP heal (4-piece)",
                () -> Assertions.assertTrue(reductionAttached,
                        "the 2-piece is attached at battle start (BATTLE_START -> MODIFY_DAMAGE_TAKEN, negative)"),
                () -> Assertions.assertTrue(removed, "precondition: the buff is there to take off"),
                () -> Assertions.assertEquals(without * (1 - SNOW_REDUCTION), withReduction, TOLERANCE,
                        "8% less, in the multiplicative reduction zone"),
                () -> Assertions.assertTrue(partialHasReduction,
                        "the 2-piece tier is reached by three pieces, one short of the 4-piece tier"),
                () -> Assertions.assertFalse(plainHasReduction,
                        "and a character wearing no set carries no reduction buff"),
                () -> Assertions.assertEquals(hpBefore + maxHp * 0.08, hpAfter, TOLERANCE,
                        "8% of the wearer's own Max HP, not a number from the file"),
                () -> Assertions.assertEquals(energyBefore + 5, energyAfter, TOLERANCE,
                        "and the text's 5 Energy"),
                () -> Assertions.assertEquals(highHpBefore, highHpAfter, TOLERANCE,
                        "「if the HP percentage <= 50%」"),
                () -> Assertions.assertEquals(highEnergyBefore, highEnergyAfter, TOLERANCE,
                        "so no Energy above the threshold either"));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * A character wearing the whole set (four pieces for a cavern set).
     */
    private static Character wearing(int cid, int setId) {
        return CharacterFactory.create(cid, CHARACTER_LEVEL, true, null,
                RelicFactory.suit(setId, STAR, LEVEL));
    }

    /**
     * A character with no relics at all.
     */
    private static Character plain(int cid) {
        return CharacterFactory.create(cid, CHARACTER_LEVEL);
    }

    /**
     * A character wearing the first {@link #PARTIAL_PIECES} cavern pieces of a set.
     */
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
        Battle battle = BattleFixture.battle(team, List.of(enemy));
        if (start) {
            battle.startBattle();
        }
        return battle;
    }

    /**
     * Settles one small hit on {@code victim} and returns the HP it lost.
     *
     * <p>Small on purpose: a level-80 character's bar is a few thousand points, and {@code takeDamage} clamps
     * at zero, so a big hit would report the bar's remainder instead of the hit.
     */
    private static double smallHitOn(Battle battle, Character victim) {
        double before = victim.getCurrentHp();
        battle.applyDamage(victim, new Damage(battle.enemyUnits().getFirst(), victim, DamageElement.ICE, 100));
        return before - victim.getCurrentHp();
    }

    /**
     * Casts the character's ultimate for real (through {@code Battle.castUltra}, energy gate included).
     */
    private static boolean castUltimate(Battle battle, Character hero) {
        hero.setCurrentEnergy(hero.getMaxEnergy());
        return battle.castUltra(hero, List.of(battle.enemyUnits().getFirst()));
    }

    /**
     * Casts the character's Skill (through the action-bar-free entry point, so no turn is needed).
     */
    private static void castSkill(Battle battle, Character hero) {
        battle.castImmediate(hero.getSkills().get(SkillType.SKILL), hero,
                List.of(battle.enemyUnits().getFirst()));
    }

    /**
     * Runs turns until {@code who} acts, then settles that turn start and end.
     *
     * <p>A turn has to be finished for the next one to begin, and it is the start that the set's removal
     * clause hangs on - so "the wearer's turn" has to be driven, not simulated by calling the trigger.
     */
    private static void takeTurnOf(Battle battle, Character who) {
        for (int guard = 0; guard < 40; guard++) {
            battle.stepForward();
            if (battle.isOver()) {
                throw new AssertionError("the battle ended before the requested unit acted");
            }
            boolean mine = battle.queue().getCurrentActor().getCanHit() == who;
            if (mine) {
                battle.beforeMove();
            }
            battle.afterMove();
            if (mine) {
                return;
            }
        }
        throw new AssertionError("no turn for the requested unit within 40 steps");
    }

    /**
     * The modifiers a buff has installed on one attribute, in application order.
     */
    private static List<DoubleValue.Modifier> buffsOn(Character character, AttributeType type) {
        return character.getAttribute(type).filterBySource(DoubleValue.Modifier.ModifierSource.BUFF);
    }

    /**
     * How much action value the unit still has to accumulate before its next turn.
     */
    private static double timeRemaining(Battle battle, CanHit unit) {
        for (Signal signal : battle.queue().snapshot()) {
            if (signal.getCanHit() == unit) {
                return battle.queue().getTimeRemaining(signal);
            }
        }
        throw new IllegalStateException(unit.getName() + " is not on the action bar");
    }

    // Genius of Brilliant Stars (繁星璀璨的天才), 4-piece: "ignores 10% of defence when dealing damage;
    // if the target has a quantum weakness, additionally ignores 10%".
}
