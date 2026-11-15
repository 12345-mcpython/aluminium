package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.DoubleValue;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.skill.DefaultSkill;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import com.laosun.aluminium.utils.RelicFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import com.laosun.aluminium.equipment.Relic;

/**
 * {@code "until"} - a buff that ends when its <b>owner does something</b> instead of after N turns.
 *
 * <p><b>Why a lifetime and not a turn count.</b> "持续到施放首次攻击后结束" / "for the next attack" /
 * "the next Skill" are not numbers of turns. Written as {@code turns: 1} the buff expires on the wrong turn
 * boundary and survives a turn in which nothing was attacked; written as {@code permanent: true} it never
 * goes away. Both are wrong numbers with nothing to see, which is exactly what this project refuses - so the
 * rule names the <b>event</b> that ends it, and the vocabulary is closed
 * ({@code next_attack} / {@code next_skill} / {@code next_ultimate} / {@code next_elation_skill}).
 *
 * <p><b>What the cases guard.</b>
 * <ol>
 *   <li>the event really ends it, and it is the <b>owner's</b> event - the engine broadcasts "an attack
 *       happened" to every member of our side, so without the owner test one character's attack would
 *       consume another's buff;</li>
 *   <li>it is <b>not</b> ticked away: a lifetime is not a turn count, so any number of turn boundaries must
 *       leave it alone. (This is why an event-bound buff is created with the "never ticked" flag, which is
 *       what {@code permanent} means mechanically - see {@code TriggerInterpreter.unticked}.)</li>
 *   <li>the spellings mean different events, including the Elation skill's own category;</li>
 *   <li>the shipped user of it - relic set 305 - grants its 60% CRIT Rate and loses it to the first attack.</li>
 * </ol>
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the four scenarios below, one {@code assertAll} per
 * scenario so a failing clause still names itself. The four load-time rejection paths stay their own tests:
 * an {@code assertThrows} is not a positive clause and must not hide behind one.
 */
public class BuffLifetimeTest {
    private static final double EPS = 1e-6;

    /**
     * Himeko (姬子) - the plain character, and the one whose attacks this class drives.
     */
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    /**
     * Celestial Differentiator (星体差分机) - "lasts until the first attack is cast, then ends".
     */
    private static final int STELLAR_DIFFERENTIATOR = 305;

    // ==================================================================
    // 1. The event ends it
    // ==================================================================

    /**
     * "for the next attack": the lifetime belongs to its owner and only a real attack ends it.
     *
     * <p>One scenario for the one fixture ({@code next_attack} on the owner) and the one observable (the
     * CRIT Rate bonus): the owner's attack consumes it, a teammate's attack does not, any number of turn
     * boundaries leave it alone, and a swing that connects with nothing is not an attack.
     */
    @Test
    public void nextAttackLifetimeBelongsToItsOwner() {
        // The owner's own attack consumes it, and it is up beforehand.
        Battle ownerBattle = battleWithBuff("next_attack");
        Character owner = ownerBattle.characters().getFirst();
        double beforeAnyAction = critRateBonus(owner);
        attack(ownerBattle, owner, 1);        // slot 1 = the basic attack
        double afterOwnerAttack = critRateBonus(owner);

        // A teammate's attack does not: the buff belongs to its owner, not to whoever attacks first.
        Battle teamBattle = battleWithBuff("next_attack", 2);
        Character teamOwner = teamBattle.characters().getFirst();
        Character teammate = teamBattle.characters().get(1);
        attack(teamBattle, teammate, 1);
        double afterTeammateAttack = critRateBonus(teamOwner);

        // A lifetime is not a turn count: turn boundaries leave it alone.
        Battle turnBattle = battleWithBuff("next_attack");
        Character turnOwner = turnBattle.characters().getFirst();
        for (int turn = 0; turn < 5; turn++) {
            turnOwner.getBuffManager().beforeMove();
            turnOwner.getBuffManager().afterMove();
        }
        double afterTurnBoundaries = critRateBonus(turnOwner);

        // An attack that hits nothing is not an attack: the buff survives the whiff.
        Battle whiffBattle = battleWithBuff("next_attack");
        Character whiffOwner = whiffBattle.characters().getFirst();
        whiffBattle.enemyUnits().getFirst().takeDamage(9_999_999);
        whiffBattle.processRequests();
        attack(whiffBattle, whiffOwner, 1, whiffBattle.enemyUnits().getFirst());
        double afterWhiff = critRateBonus(whiffOwner);

        Assertions.assertAll(
                () -> Assertions.assertEquals(0.5, beforeAnyAction, EPS, "precondition: the buff is up"),
                () -> Assertions.assertEquals(0, afterOwnerAttack, EPS,
                        "「until after the wearer's first attack」 -- the attack ended it"),
                () -> Assertions.assertEquals(0.5, afterTeammateAttack, EPS,
                        "the buff belongs to its owner, not to whoever happens to attack"),
                () -> Assertions.assertEquals(0.5, afterTurnBoundaries, EPS,
                        "it ends when its owner attacks, and nothing has attacked"),
                () -> Assertions.assertEquals(0.5, afterWhiff, EPS,
                        "not one hit landed, so nothing attacked"));
    }

    /**
     * The spellings name different events: {@code next_skill}, {@code next_ultimate},
     * {@code next_elation_skill} and {@code next_skill:<slot>} are four clauses of one vocabulary, so they
     * share the fixture and the observable and are checked as one scenario.
     *
     * <p>The Elation cast is driven with skill 1513's own row ({@code DefaultSkill(1513, 20, 1)}) because the
     * category the lifetime reads is a fact about the SKILL's data ({@code ElationDamage}), while the caster
     * stays this class's own unit: nothing in the lifetime reads who cast it.
     */
    @Test
    public void lifetimeSpellingsNameDistinctEvents() {
        // next_skill: a basic attack does not consume it, a Skill does.
        Battle skillBattle = battleWithBuff("next_skill");
        Character skillOwner = skillBattle.characters().getFirst();
        attack(skillBattle, skillOwner, 1);
        double afterBasicForSkill = critRateBonus(skillOwner);
        attack(skillBattle, skillOwner, 2);
        double afterSkill = critRateBonus(skillOwner);

        // next_ultimate: a Skill does not consume it, the Ultimate does.
        Battle ultBattle = battleWithBuff("next_ultimate");
        Character ultOwner = ultBattle.characters().getFirst();
        attack(ultBattle, ultOwner, 2);
        double afterSkillForUltimate = critRateBonus(ultOwner);
        attack(ultBattle, ultOwner, 3);
        double afterUltimate = critRateBonus(ultOwner);

        // next_elation_skill: a Skill does not consume it, an Elation skill does.
        Battle elationBattle = battleWithBuff("next_elation_skill");
        Character elationOwner = elationBattle.characters().getFirst();
        attack(elationBattle, elationOwner, 2);
        double afterSkillForElation = critRateBonus(elationOwner);
        elationBattle.castImmediate(new DefaultSkill(1513, 20, 1), elationOwner, List.of(monster()));
        double afterElationCast = critRateBonus(elationOwner);

        // next_skill:20: an ordinary basic and a Skill are not the row; the named row ends it.
        Battle slotBattle = battleWithBuff("next_skill:20");
        Character slotOwner = slotBattle.characters().getFirst();
        attack(slotBattle, slotOwner, 1);
        double afterBasicForSlot = critRateBonus(slotOwner);
        attack(slotBattle, slotOwner, 2);
        double afterSkillForSlot = critRateBonus(slotOwner);
        slotBattle.castImmediate(new DefaultSkill(1513, 20, 1), slotOwner, List.of(monster()));
        double afterNamedSlot = critRateBonus(slotOwner);

        Assertions.assertAll(
                () -> Assertions.assertEquals(0.5, afterBasicForSkill, EPS,
                        "a basic attack is not a Skill"),
                () -> Assertions.assertEquals(0, afterSkill, EPS, "a Skill is"),
                () -> Assertions.assertEquals(0.5, afterSkillForUltimate, EPS,
                        "a Skill is not an Ultimate"),
                () -> Assertions.assertEquals(0, afterUltimate, EPS, "the Ultimate is"),
                () -> Assertions.assertEquals(0.5, afterSkillForElation, EPS,
                        "a Skill is not an Elation skill"),
                () -> Assertions.assertEquals(0, afterElationCast, EPS, "the Elation cast is"),
                () -> Assertions.assertEquals(0.5, afterBasicForSlot, EPS,
                        "an ordinary basic is not the row"),
                () -> Assertions.assertEquals(0.5, afterSkillForSlot, EPS,
                        "a Skill is not the row"),
                () -> Assertions.assertEquals(0, afterNamedSlot, EPS, "the named slot ends it"));
    }

    // ==================================================================
    // 1b. Several events at once: "普攻或战技"
    // ==================================================================

    /**
     * "持续至装备者下次施放普攻<b>或</b>战技后" - a duration that names two events ends at the <b>first</b> of
     * them, and it takes a whiffed basic attack (neither event) to leave it alone.
     *
     * <p>One scenario, because all three clauses use the same fixture (a list of lifetimes on the owner) and
     * the same observable: a Skill cast that lands nothing still ends it, a basic attack ends it, and a basic
     * attack that lands nothing does not.
     */
    @Test
    public void aDisjunctionEndsAtTheFirstNamedEvent() {
        // The half a lone `next_attack` cannot express: a cast that landed nothing still ends it.
        Battle bySkill = battleWithBuff(List.of("next_attack", "next_skill"));
        Character skilled = bySkill.characters().getFirst();
        bySkill.enemyUnits().getFirst().takeDamage(9_999_999);
        bySkill.processRequests();
        attack(bySkill, skilled, 2, bySkill.enemyUnits().getFirst());   // a Skill, aimed at the dead enemy
        double afterWhiffedSkill = critRateBonus(skilled);

        // The other member, on its own: a basic attack ends it just as well.
        Battle byAttack = battleWithBuff(List.of("next_attack", "next_skill"));
        Character attacker = byAttack.characters().getFirst();
        attack(byAttack, attacker, 1);
        double afterBasic = critRateBonus(attacker);

        // A whiffed basic attack is neither named event, so the disjunction must survive it.
        Battle whiff = battleWithBuff(List.of("next_attack", "next_skill"));
        Character whiffOwner = whiff.characters().getFirst();
        whiff.enemyUnits().getFirst().takeDamage(9_999_999);
        whiff.processRequests();
        attack(whiff, whiffOwner, 1, whiff.enemyUnits().getFirst());
        double afterWhiffedBasic = critRateBonus(whiffOwner);

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, afterWhiffedSkill, EPS,
                        "the cast is the event, whether or not it connected"),
                () -> Assertions.assertEquals(0, afterBasic, EPS,
                        "「普攻或战技」 (a Basic ATK or a Skill): either one is enough"),
                () -> Assertions.assertEquals(0.5, afterWhiffedBasic, EPS,
                        "no hit landed and no Skill was cast, so neither named event happened"));
    }

    // ==================================================================
    // 2. Load-time validation
    // ==================================================================

    /**
     * An unknown lifetime is rejected while the file is read, not by watching a buff never expire.
     */
    @Test
    public void anUnknownLifetimeIsRejected() {
        IllegalArgumentException unknown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableWith(rule("next_atack")));

        Assertions.assertTrue(unknown.getMessage().contains("next_atack"), unknown.getMessage());
        Assertions.assertTrue(unknown.getMessage().contains("next_attack"),
                "the message must list the vocabulary: " + unknown.getMessage());
    }

    /**
     * Every entry of a list is checked, not just the first.
     *
     * <p>Pinned because the tempting implementation - validate the first name, trust the rest - loads a rule whose
     * file claims two events while the buff only ends on one: a wrong duration with nothing to see.
     */
    @Test
    public void anUnknownLifetimeInsideAListIsRejected() {
        List<String> mistyped = new java.util.ArrayList<>(List.of("next_attack"));
        mistyped.add("next_atack");

        IllegalArgumentException unknown = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(rule(mistyped))));

        Assertions.assertTrue(unknown.getMessage().contains("next_atack"), unknown.getMessage());
    }

    /**
     * Stating a lifetime <i>and</i> a turn count is two disagreements, so it is refused.
     */
    @Test
    public void aLifetimeTogetherWithTurnsIsRejected() {
        EffectSpec both = effect("next_attack");
        TriggerSpecs.set(both, "turns", 2);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableWith(TriggerSpecs.rule("BATTLE_START", null, both)));

        Assertions.assertTrue(refused.getMessage().contains("until"), refused.getMessage());
    }

    /**
     * An op that creates no buff has nothing an {@code until} could end.
     */
    @Test
    public void aLifetimeOnAnOpWithoutABuffIsRejected() {
        EffectSpec boost = new EffectSpec();
        TriggerSpecs.set(boost, "op", "BOOST_DAMAGE");
        TriggerSpecs.set(boost, "percent", 0.1);
        TriggerSpecs.set(boost, "until", List.of("next_attack"));

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(OWNER, List.of(
                        TriggerSpecs.rule("DEALING_DAMAGE", null, boost))));

        Assertions.assertTrue(refused.getMessage().contains("BOOST_DAMAGE"), refused.getMessage());
    }

    // ==================================================================
    // 3. The shipped content: relic set 305
    // ==================================================================

    /**
     * The shipped 2-piece of relic set 305 (星体差分机): what the authored rule says, what it grants above the
     * threshold, and what it refuses below it - one scenario of the one fixture ({@code wearer(double)}
     * wearing set 305 in a seeded battle).
     *
     * <p>End-to-end through the relic loader, the real battle start and a real attack. Measured as a delta
     * against the same character bare, never as an absolute - her sheet has a CRIT Rate of its own.
     */
    @Test
    public void relic305TwoPieceGrantsCritRateUntilTheFirstAttack() {
        // What the authored rule is: filed at the 2-piece tier, it states a lifetime, not a turn count.
        Character reader = wearer(1.3);
        Battle readerBattle = new Battle(List.of(reader), List.of(monster()), BattleFixture.seeded());
        readerBattle.startBattle();
        List<TriggerTable.CompiledRule> rules = RelicTriggerTables.of(STELLAR_DIFFERENTIATOR).at(2)
                .matching(TriggerEvent.BATTLE_START,
                        new TriggerTable.TriggerContext(reader, reader, null, 0, 0, null, readerBattle));
        // Kept null-safe so a missing rule is still reported by the size assertion, not by a getFirst() throw.
        EffectSpec effect = rules.isEmpty() ? null : rules.getFirst().effects().getFirst();

        // Above 120% CRIT DMG the 2-piece grants +60% CRIT Rate, and the first attack takes it away.
        Character wearer = wearer(1.3);
        Battle battle = new Battle(List.of(wearer), List.of(monster()), BattleFixture.seeded());
        double bare = CharacterFactory.create(OWNER, LEVEL).getAttribute(AttributeType.CRIT_CHANCE).get();
        battle.startBattle();
        double granted = critRateOf(wearer);
        attack(battle, wearer, 1);
        double afterFirstAttack = critRateOf(wearer);

        // Below the threshold nothing is granted at all.
        Character belowWearer = wearer(1.0);            // 100% CRIT DMG, below the text's 120%
        Battle belowBattle = new Battle(List.of(belowWearer), List.of(monster()), BattleFixture.seeded());
        double bareBelow = CharacterFactory.create(OWNER, LEVEL).getAttribute(AttributeType.CRIT_CHANCE).get();
        belowBattle.startBattle();
        double belowThreshold = critRateOf(belowWearer);

        Assertions.assertAll(
                () -> Assertions.assertEquals(1, rules.size(),
                        "the 2-piece tier compiles exactly one BATTLE_START rule"),
                () -> Assertions.assertEquals("CRIT_CHANCE", effect.getAttribute(),
                        "the rule modifies CRIT_CHANCE"),
                () -> Assertions.assertEquals(0.6, effect.getPercent(), EPS, "param #3 is 0.6"),
                () -> Assertions.assertEquals(List.of("next_attack"), effect.getUntil(),
                        "a lifetime, not a turn count: turns would expire on the wrong boundary"),
                () -> Assertions.assertNull(effect.getTurns(), "and the two are mutually exclusive"),
                () -> Assertions.assertEquals(bare + 0.6, granted, EPS,
                        "the rule fired: param #3 is 0.6 (60%), read from the parameters"),
                () -> Assertions.assertEquals(bare, afterFirstAttack, EPS,
                        "「持续到施放首次攻击后结束」 (lasts until the first attack is cast, then ends) -- and the first attack has happened"),
                () -> Assertions.assertEquals(bareBelow, belowThreshold, EPS,
                        "`self_attr:CRIT_ATTACK >= 1.2` is a fraction, and 1.0 is below it"));
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /**
     * A battle whose owner carries one battle-start rule granting +50% CRIT Rate with the given lifetime.
     */
    private static Battle battleWithBuff(String until) {
        return battleWithBuff(until, 1);
    }

    /**
     * The same, for a duration that names several events ("普攻<b>或</b>战技").
     */
    private static Battle battleWithBuff(List<String> until) {
        return battleWithBuff(until, 1);
    }

    private static Battle battleWithBuff(String until, int teamSize) {
        return battleWithBuff(List.of(until), teamSize);
    }

    private static Battle battleWithBuff(List<String> until, int teamSize) {
        List<Character> team = new java.util.ArrayList<>();
        Character owner = CharacterFactory.create(OWNER, LEVEL);
        owner.setTriggerTable(new TriggerTable(OWNER, List.of(rule(until))));
        team.add(owner);
        for (int i = 1; i < teamSize; i++) {
            team.add(CharacterFactory.create(1210, LEVEL));   // Guinaifen (桂乃芬): no rule file of its own
        }
        Battle battle = new Battle(team, List.of(monster()), BattleFixture.seeded());
        battle.startBattle();
        return battle;
    }

    private static TriggerSpec rule(List<String> until) {
        return TriggerSpecs.rule("BATTLE_START", null, effect(until));
    }

    private static TriggerSpec rule(String until) {
        return TriggerSpecs.rule("BATTLE_START", null, effect(until));
    }

    private static TriggerSpec tableWith(TriggerSpec spec) {
        new TriggerTable(OWNER, List.of(spec));       // the construction is the assertion
        return spec;
    }

    private static EffectSpec effect(String until) {
        return effect(List.of(until));
    }

    private static EffectSpec effect(List<String> until) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "MODIFY_ATTR");
        TriggerSpecs.set(effect, "attribute", "CRIT_CHANCE");
        TriggerSpecs.set(effect, "percent", 0.5);
        TriggerSpecs.set(effect, "until", until);
        return effect;
    }

    /**
     * The owner casts its slot-{@code slotNo} skill at the monster - a real cast through the executor.
     */
    private static void attack(Battle battle, Character actor, int slotNo) {
        battle.castImmediate(new DefaultSkill(OWNER, slotNo, 1), actor, List.of(monster()));
    }

    /**
     * The same cast, but aimed at a <b>given</b> unit - the caller's list is the main target, so aiming at the
     * battlefield's own (dead) enemy is how a swing can hit nothing at all.
     */
    private static void attack(Battle battle, Character actor, int slotNo, CanHit target) {
        battle.castImmediate(new DefaultSkill(OWNER, slotNo, 1), actor, List.of(target));
    }

    private static Character wearer(double critDamage) {
        Character wearer = CharacterFactory.create(OWNER, LEVEL, true, null,
                RelicFactory.suit(STELLAR_DIFFERENTIATOR, 5, 15));
        wearer.setAttribute(AttributeType.CRIT_ATTACK, new DoubleValue(critDamage));
        return wearer;
    }

    private static double critRateOf(Character character) {
        return character.getAttribute(AttributeType.CRIT_CHANCE).get();
    }

    /**
     * How much CRIT Rate the battle-start rule added.
     */
    private static double critRateBonus(Character owner) {
        double bare = CharacterFactory.create(OWNER, LEVEL).getAttribute(AttributeType.CRIT_CHANCE).get();
        return critRateOf(owner) - bare;
    }

    private static Enemy monster() {
        return BattleFixture.monster();
    }
}
