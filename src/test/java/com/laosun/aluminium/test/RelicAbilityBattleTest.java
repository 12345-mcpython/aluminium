package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.Constant;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.RelicType;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
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
 * <p>These four sets are the ones the current op vocabulary can express exactly (see
 * {@code RelicTriggerTableTest} for the other 31, which are registered as gaps instead of being
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
