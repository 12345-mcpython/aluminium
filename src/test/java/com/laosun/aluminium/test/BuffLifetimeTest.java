package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.RelicTriggerTables;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.TriggerTable;
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
 * {@code "until"} - a buff that ends when its <b>owner does something</b> instead of after N turns (P10-4).
 *
 * <p><b>Why a lifetime and not a turn count.</b> "持续到施放首次攻击后结束" / "for the next attack" /
 * "the next Skill" are not numbers of turns. Written as {@code turns: 1} the buff expires on the wrong turn
 * boundary and survives a turn in which nothing was attacked; written as {@code permanent: true} it never
 * goes away. Both are wrong numbers with nothing to see, which is exactly what this project refuses - so the
 * rule names the <b>event</b> that ends it, and the vocabulary is closed
 * ({@code next_attack} / {@code next_skill} / {@code next_ultimate}).
 *
 * <p><b>What the cases guard.</b>
 * <ol>
 *   <li>the event really ends it, and it is the <b>owner's</b> event - the engine broadcasts "an attack
 *       happened" to every member of our side, so without the owner test one character's attack would
 *       consume another's buff;</li>
 *   <li>it is <b>not</b> ticked away: a lifetime is not a turn count, so any number of turn boundaries must
 *       leave it alone. (This is why an event-bound buff is created with the "never ticked" flag, which is
 *       what {@code permanent} means mechanically - see {@code TriggerInterpreter.unticked}.)</li>
 *   <li>the three spellings mean three different events;</li>
 *   <li>the shipped user of it - relic set 305 - grants its 60% CRIT Rate and loses it to the first attack.</li>
 * </ol>
 */
public class BuffLifetimeTest {
    private static final double EPS = 1e-6;

    /** 姬子 - the plain character, and the one whose attacks this class drives. */
    private static final int OWNER = 1003;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    /** 星体差分机 / Celestial Differentiator - "持续到施放首次攻击后结束". */
    private static final int STELLAR_DIFFERENTIATOR = 305;

    // ==================================================================
    // 1. The event ends it
    // ==================================================================

    /** "for the next attack": the owner's attack consumes it. */
    @Test
    public void theOwnersAttackConsumesIt() {
        Battle battle = battleWithBuff("next_attack");
        Character owner = battle.characters.getFirst();
        Assertions.assertEquals(0.5, critRateBonus(owner), EPS, "precondition: the buff is up");

        attack(battle, owner, 1);        // slot 1 = the basic attack

        Assertions.assertEquals(0, critRateBonus(owner), EPS,
                "「until after the wearer's first attack」 -- the attack ended it");
    }

    /**
     * A teammate's attack does <b>not</b>.
     *
     * <p>The engine delivers "an attack happened" to every member of our side ({@code AttackEvent} is how
     * Robin's and Tribbie's third-party kits hear about it), so this is the case that pins the owner test - 
     * without it, whoever attacks first would consume everybody's "for the next attack" buff.
     */
    @Test
    public void aTeammatesAttackDoesNotConsumeIt() {
        Battle battle = battleWithBuff("next_attack", 2);
        Character owner = battle.characters.getFirst();
        Character teammate = battle.characters.get(1);

        attack(battle, teammate, 1);

        Assertions.assertEquals(0.5, critRateBonus(owner), EPS,
                "the buff belongs to its owner, not to whoever happens to attack");
    }

    /**
     * A lifetime is not a turn count: turn boundaries leave it alone.
     *
     * <p>Pinned because the tempting implementation - give the buff a placeholder duration - would expire it
     * on the first {@code afterMove}, i.e. a rule that looks like it works and lasts one turn instead of one
     * attack.
     */
    @Test
    public void turnBoundariesDoNotConsumeIt() {
        Battle battle = battleWithBuff("next_attack");
        Character owner = battle.characters.getFirst();

        for (int turn = 0; turn < 5; turn++) {
            owner.getBuffManager().beforeMove();
            owner.getBuffManager().afterMove();
        }

        Assertions.assertEquals(0.5, critRateBonus(owner), EPS,
                "it ends when its owner attacks, and nothing has attacked");
    }

    /**
     * An attack that <b>connects with nothing</b> is not an attack: the buff survives it.
     *
     * <p>"持续到施放首次攻击后结束" is about an attack that happened, and the engine says an attack happened only
     * when at least one target was hit ({@code Battle.fireAfterAttack}'s first guard). Casting at a battlefield
     * with nothing left alive is the reachable way to swing and hit nothing - and the tempting implementation,
     * broadcasting "an attack happened" whenever a skill was cast, would burn the buff on the whiff.
     */
    @Test
    public void anAttackThatHitsNothingDoesNotConsumeIt() {
        Battle battle = battleWithBuff("next_attack");
        Character owner = battle.characters.getFirst();
        battle.enemyUnits().getFirst().takeDamage(9_999_999);
        battle.processRequests();

        attack(battle, owner, 1, battle.enemyUnits().getFirst());   // slot 1 at the battlefield's dead enemy

        Assertions.assertEquals(0.5, critRateBonus(owner), EPS,
                "not one hit landed, so nothing attacked");
    }

    /** The three spellings are three different events. */
    @Test
    public void theSpellingsNameDifferentEvents() {
        // next_skill: a basic attack does not consume it, a Skill does.
        Battle skillBattle = battleWithBuff("next_skill");
        Character skillOwner = skillBattle.characters.getFirst();
        attack(skillBattle, skillOwner, 1);
        Assertions.assertEquals(0.5, critRateBonus(skillOwner), EPS,
                "a basic attack is not a Skill");
        attack(skillBattle, skillOwner, 2);
        Assertions.assertEquals(0, critRateBonus(skillOwner), EPS, "a Skill is");

        // next_ultimate: a Skill does not consume it, the Ultimate does.
        Battle ultBattle = battleWithBuff("next_ultimate");
        Character ultOwner = ultBattle.characters.getFirst();
        attack(ultBattle, ultOwner, 2);
        Assertions.assertEquals(0.5, critRateBonus(ultOwner), EPS, "a Skill is not an Ultimate");
        attack(ultBattle, ultOwner, 3);
        Assertions.assertEquals(0, critRateBonus(ultOwner), EPS, "the Ultimate is");
    }

    // ==================================================================
    // 1b. Several events at once: "普攻或战技"
    // ==================================================================

    /**
     * "持续至装备者下次施放普攻<b>或</b>战技后" - a duration that names two events ends at the <b>first</b> of them.
     *
     * <p>Relic set 12's sentence is a disjunction, and it is the case that tells a list of lifetimes apart from
     * both wrong shapes: a single {@code next_attack} would ignore a Skill that attacks nothing (the case below,
     * which the existing whiff rule pins from the other side), and one buff per event would make the two
     * <b>replace</b> each other - same kind, same target - so only the later one would ever be up.
     */
    @Test
    public void aDisjunctionEndsAtTheFirstOfItsEvents() {
        // The half a lone `next_attack` cannot express: a cast that landed nothing still ends it.
        Battle bySkill = battleWithBuff(List.of("next_attack", "next_skill"));
        Character skilled = bySkill.characters.getFirst();
        bySkill.enemyUnits().getFirst().takeDamage(9_999_999);
        bySkill.processRequests();

        attack(bySkill, skilled, 2, bySkill.enemyUnits().getFirst());   // a Skill, aimed at the dead enemy
        Assertions.assertEquals(0, critRateBonus(skilled), EPS,
                "the cast is the event, whether or not it connected");

        // The other member, on its own: a basic attack ends it just as well.
        Battle byAttack = battleWithBuff(List.of("next_attack", "next_skill"));
        Character attacker = byAttack.characters.getFirst();

        attack(byAttack, attacker, 1);
        Assertions.assertEquals(0, critRateBonus(attacker), EPS,
                "「普攻或战技」: either one is enough");
    }

    /**
     * A second lifetime does not make the buff outlive its owner's death of a whiff: an attack that hits nothing
     * is still not an attack ({@code next_attack}), and it is not a Skill cast either when it is a basic attack.
     */
    @Test
    public void aWhiffedBasicAttackStillDoesNotConsumeTheDisjunction() {
        Battle battle = battleWithBuff(List.of("next_attack", "next_skill"));
        Character owner = battle.characters.getFirst();
        battle.enemyUnits().getFirst().takeDamage(9_999_999);
        battle.processRequests();

        attack(battle, owner, 1, battle.enemyUnits().getFirst());

        Assertions.assertEquals(0.5, critRateBonus(owner), EPS,
                "no hit landed and no Skill was cast, so neither named event happened");
    }

    // ==================================================================
    // 2. Load-time validation
    // ==================================================================

    /** An unknown lifetime is rejected while the file is read, not by watching a buff never expire. */
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

    /** Stating a lifetime <i>and</i> a turn count is two disagreements, so it is refused. */
    @Test
    public void aLifetimeTogetherWithTurnsIsRejected() {
        EffectSpec both = effect("next_attack");
        TriggerSpecs.set(both, "turns", 2);

        IllegalArgumentException refused = Assertions.assertThrows(IllegalArgumentException.class,
                () -> tableWith(TriggerSpecs.rule("BATTLE_START", null, both)));

        Assertions.assertTrue(refused.getMessage().contains("until"), refused.getMessage());
    }

    /** An op that creates no buff has nothing an {@code until} could end. */
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
     * The authored 2-piece grants +60% CRIT Rate above 120% CRIT DMG, and the first attack takes it away.
     *
     * <p>End-to-end through the relic loader, the real battle start and a real attack. Measured as a delta
     * against the same character bare, never as an absolute - her sheet has a CRIT Rate of its own.
     */
    @Test
    public void theAuthoredRuleGrantsItsCritRateUntilTheFirstAttack() {
        Character wearer = wearer(1.3);
        Battle battle = new Battle(List.of(wearer), List.of(monster()), new Random(0));
        double bare = CharacterFactory.create(OWNER, LEVEL).getAttribute(AttributeType.CRIT_CHANCE).get();

        battle.startBattle();
        Assertions.assertEquals(bare + 0.6, critRateOf(wearer), EPS,
                "the rule fired: param #3 is 0.6 (60%), read from the parameters");

        attack(battle, wearer, 1);

        Assertions.assertEquals(bare, critRateOf(wearer), EPS,
                "「持续到施放首次攻击后结束」 -- and the first attack has happened");
    }

    /** Below the threshold nothing is granted at all. */
    @Test
    public void belowTheCritDamageThresholdNothingIsGranted() {
        Character wearer = wearer(1.0);            // 100% CRIT DMG, below the text's 120%
        Battle battle = new Battle(List.of(wearer), List.of(monster()), new Random(0));
        double bare = CharacterFactory.create(OWNER, LEVEL).getAttribute(AttributeType.CRIT_CHANCE).get();

        battle.startBattle();

        Assertions.assertEquals(bare, critRateOf(wearer), EPS,
                "`self_attr:CRIT_ATTACK >= 1.2` is a fraction, and 1.0 is below it");
    }

    /** The rule is filed at the 2-piece tier and states the lifetime from the text. */
    @Test
    public void theAuthoredRuleStatesItsOwnLifetime() {
        Character wearer = wearer(1.3);
        Battle battle = new Battle(List.of(wearer), List.of(monster()), new Random(0));
        battle.startBattle();

        List<TriggerTable.CompiledRule> rules = RelicTriggerTables.of(STELLAR_DIFFERENTIATOR).at(2)
                .matching(TriggerEvent.BATTLE_START,
                        new TriggerTable.TriggerContext(wearer, wearer, null, 0, 0, null, battle));

        Assertions.assertEquals(1, rules.size());
        EffectSpec effect = rules.getFirst().effects().getFirst();
        Assertions.assertEquals("CRIT_CHANCE", effect.getAttribute());
        Assertions.assertEquals(0.6, effect.getPercent(), EPS, "param #3 is 0.6");
        Assertions.assertEquals(List.of("next_attack"), effect.getUntil(),
                "a lifetime, not a turn count: turns would expire on the wrong boundary");
        Assertions.assertNull(effect.getTurns(), "and the two are mutually exclusive");
    }

    // ==================================================================
    // Fixture
    // ==================================================================

    /** A battle whose owner carries one battle-start rule granting +50% CRIT Rate with the given lifetime. */
    private static Battle battleWithBuff(String until) {
        return battleWithBuff(until, 1);
    }

    /** The same, for a duration that names several events ("普攻<b>或</b>战技"). */
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
            team.add(CharacterFactory.create(1210, LEVEL));   // 桂乃芬: no rule file of its own
        }
        Battle battle = new Battle(team, List.of(monster()), new Random(0));
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

    /** The owner casts its slot-{@code slotNo} skill at the monster - a real cast through the executor. */
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

    /** How much CRIT Rate the battle-start rule added. */
    private static double critRateBonus(Character owner) {
        double bare = CharacterFactory.create(OWNER, LEVEL).getAttribute(AttributeType.CRIT_CHANCE).get();
        return critRateOf(owner) - bare;
    }

    private static Enemy monster() {
        return EnemyFactory.create(MONSTER, 90, 1);
    }
}
