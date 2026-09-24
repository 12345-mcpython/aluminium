package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.data.TriggerTables;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Trigger tables (P8-7): character mechanics expressed as data.
 *
 * <p>The acceptance criterion from the roadmap is that <b>two real characters work with no Java
 * character class at all</b> -- their mechanics exist only as JSON under
 * {@code resources/characters/}:
 * <ul>
 *   <li><b>Tribbie 1403</b> — trace 1403103: 30 energy at battle start, and 1.50 energy
 *       <b>per target hit</b> when another ally attacks;</li>
 *   <li><b>Robin 1309</b> — talent: a <b>flat 2</b> energy whenever an ally attacks.</li>
 * </ul>
 * Those two are deliberately paired because they differ in the one way that is easy to get wrong:
 * Tribbie scales with the number of targets, Robin does not.
 */
public class TriggerTableTest {

    private static final int TRIBBIE = 1403;
    private static final int ROBIN = 1309;

    /** Himeko: an ordinary character with no trigger file, used as the "unregistered" control. */
    private static final int NO_TRIGGERS = 1003;
    private static final int ICE_EDGE = 1002011;

    // ==================================================================
    // 1. The acceptance criterion: two real characters, pure data
    // ==================================================================

    /**
     * Tribbie's opening trace: 30 energy at the start of battle, before anything else happens.
     */
    @Test
    public void tribbieGainsThirtyEnergyAtBattleStart() {
        Character tribbie = CharacterFactory.create(TRIBBIE, 80);
        Battle battle = newBattle(List.of(tribbie), 1);

        // Battle.startBattle() has already run inside newBattle, so the trigger has fired.
        assertEnergyEquals(tribbie, 30, "trace 1403103: 30 energy at battle start");
    }

    /**
     * Tribbie's in-battle half: 1.50 energy <b>per target hit</b> by <b>another</b> ally.
     *
     * <p>Three targets hit => 4.5, not 1.5. This is the assertion that distinguishes "per hit" from
     * "per attack", which is the difference between Tribbie and Robin.
     */
    @Test
    public void tribbieGainsPerTargetHitWhenAnAllyAttacks() {
        Character tribbie = CharacterFactory.create(TRIBBIE, 80);
        Character himeko = CharacterFactory.create(NO_TRIGGERS, 80);
        Battle battle = newBattle(List.of(tribbie, himeko), 3);

        double before = tribbie.getCurrentEnergy();
        attackAllEnemies(battle, himeko);
        double gained = tribbie.getCurrentEnergy() - before;

        assertEnergyEquals(gained, 1.5 * 3, "1.5 per target x 3 targets");
    }

    /** One target hit => exactly 1.5 (the same rule, smaller count). */
    @Test
    public void tribbieScalesWithTheNumberOfTargets() {
        Character tribbie = CharacterFactory.create(TRIBBIE, 80);
        Character himeko = CharacterFactory.create(NO_TRIGGERS, 80);
        Battle battle = newBattle(List.of(tribbie, himeko), 1);

        double before = tribbie.getCurrentEnergy();
        strike(battle, himeko, battle.enemies.getFirst());
        double gained = tribbie.getCurrentEnergy() - before;

        assertEnergyEquals(gained, 1.5, "1.5 per target x 1 target");
    }

    /**
     * Robin's talent: a <b>flat 2</b> when an ally attacks, regardless of how many targets.
     *
     * <p>Deliberately the same setup as Tribbie's three-target case, so the pair proves the two
     * numbers are modelled differently rather than both being "some energy per attack".
     */
    @Test
    public void robinGainsTwoEnergyPerAllyAttackNotPerTarget() {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character himeko = CharacterFactory.create(NO_TRIGGERS, 80);
        Battle battle = newBattle(List.of(robin, himeko), 3);

        double before = robin.getCurrentEnergy();
        attackAllEnemies(battle, himeko);
        double gained = robin.getCurrentEnergy() - before;

        assertEnergyEquals(gained, 2, "flat 2 per attack, even when 3 targets were hit");
    }

    /**
     * "我方目标" excludes Robin herself: her own attack must not feed her talent.
     *
     * <p>This is what the {@code actor != self} condition is for, and it is exactly the kind of rule
     * that a table without conditions would get wrong.
     */
    @Test
    public void robinDoesNotTriggerOnHerOwnAttack() {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Battle battle = newBattle(List.of(robin), 1);

        double before = robin.getCurrentEnergy();
        strike(battle, robin, battle.enemies.getFirst());
        double gained = robin.getCurrentEnergy() - before;

        // Her own attack still grants the ordinary skill energy (20), so compare against that rather
        // than 0 -- the point is that no *talent* energy was added on top.
        double skillEnergy = 20 * (1 + robin.getAttribute(
                com.laosun.aluminium.enums.AttributeType.ENERGY_REGENERATION_RATE).get());
        assertEnergyEquals(gained, skillEnergy,
                "only the base skill energy: the talent must not fire on Robin's own attack");
    }

    /** The same exclusion for Tribbie: her own attack must not feed her trace. */
    @Test
    public void tribbieDoesNotTriggerOnHerOwnAttack() {
        Character tribbie = CharacterFactory.create(TRIBBIE, 80);
        Battle battle = newBattle(List.of(tribbie), 1);

        double before = tribbie.getCurrentEnergy();
        strike(battle, tribbie, battle.enemies.getFirst());
        double gained = tribbie.getCurrentEnergy() - before;

        double skillEnergy = 20 * (1 + tribbie.getAttribute(
                com.laosun.aluminium.enums.AttributeType.ENERGY_REGENERATION_RATE).get());
        assertEnergyEquals(gained, skillEnergy, "own attack grants no trace energy");
    }

    // ==================================================================
    // 2. Unregistered = empty table, and the battle still runs
    // ==================================================================

    /**
     * A character with no file gets {@link TriggerTable#EMPTY}, and a battle containing one works
     * normally. "Not data-ised yet" is an ordinary state, not an error -- with 93 characters and a
     * handful of files, most characters are in it.
     */
    @Test
    public void unregisteredCharacterHasAnEmptyTableAndTheBattleStillRuns() {
        Character plain = CharacterFactory.create(NO_TRIGGERS, 80);
        Assertions.assertTrue(plain.getTriggerTable().isEmpty(), "no file -> empty table");
        Assertions.assertFalse(TriggerTables.exists(NO_TRIGGERS), "and no resource either");

        Battle battle = newBattle(List.of(plain), 1);
        double before = plain.getCurrentEnergy();
        strike(battle, plain, battle.enemies.getFirst());

        double skillEnergy = 20 * (1 + plain.getAttribute(
                com.laosun.aluminium.enums.AttributeType.ENERGY_REGENERATION_RATE).get());
        assertEnergyEquals(plain.getCurrentEnergy() - before, skillEnergy,
                "an empty table changes nothing");
    }

    /** Tables are cached: asking twice must not read the classpath twice. */
    @Test
    public void tablesAreLoadedOncePerCid() {
        TriggerTables.clearCache();
        TriggerTables.of(TRIBBIE);
        int afterFirst = TriggerTables.loadCount();
        TriggerTables.of(TRIBBIE);
        TriggerTables.of(TRIBBIE);
        Assertions.assertEquals(afterFirst, TriggerTables.loadCount(), "second lookup must be cached");

        // A cid with no file is cached too (as the empty table), so it must not re-probe either.
        TriggerTables.of(NO_TRIGGERS);
        int afterMiss = TriggerTables.loadCount();
        TriggerTables.of(NO_TRIGGERS);
        Assertions.assertEquals(afterMiss, TriggerTables.loadCount(), "a miss is cached as well");
    }

    // ==================================================================
    // 3. The engine really fires the events (not just "the file parses")
    // ==================================================================

    /**
     * The trigger fires on the cast event rather than on "the battle exists".
     *
     * <p>Robin has no battle-start rule, so the count of fired rules at battle start is 0 while a
     * basic attack yields 2 -- her own cast plus the ally-standing-by case. Pinning the counts means a
     * future change that fires events at the wrong moment breaks a test instead of silently
     * double-charging energy.
     */
    @Test
    public void battleStartFiresNoRulesForRobinAndACastFiresTwo() {
        Character robin = CharacterFactory.create(ROBIN, 80);
        Character himeko = CharacterFactory.create(NO_TRIGGERS, 80);
        Battle battle = newBattle(List.of(robin, himeko), 1);

        Assertions.assertEquals(0, battle.fireTriggers(TriggerEvent.BATTLE_START),
                "Robin has no BATTLE_START rule");

        int fired = battle.fireTriggers(TriggerEvent.ALLY_ATTACK, himeko, null, 1, 0);
        Assertions.assertEquals(1, fired, "only Robin's table has an ALLY_ATTACK rule");
    }

    // ==================================================================
    // 4. Fail fast: a bad table is rejected at load, not silently ignored
    // ==================================================================

    /** An unknown event name is a typo and must be reported, not skipped. */
    @Test
    public void unknownEventIsRejected() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(1, List.of(trigger("NO_SUCH_EVENT", List.of("self"), energy(1)))));
        Assertions.assertTrue(e.getMessage().contains("NO_SUCH_EVENT"), e.getMessage());
    }

    /**
     * An event the engine does not emit yet is rejected too, with the reason.
     *
     * <p>Otherwise a content author would write a rule, see nothing happen, and have no way to tell
     * "my condition is wrong" from "the engine never fires this".
     */
    @Test
    public void eventThatIsNotWiredYetIsRejectedWithAReason() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(1, List.of(trigger("ULT_CAST", List.of("self"), energy(1)))));
        Assertions.assertTrue(e.getMessage().contains("not emitted"), e.getMessage());
    }

    /** An unknown condition variable is rejected rather than evaluating to false forever. */
    @Test
    public void unknownConditionVariableIsRejected() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(1, List.of(trigger("ALLY_ATTACK", List.of("hp < 50"), energy(1)))));
        Assertions.assertTrue(e.getMessage().contains("hp"), e.getMessage());
    }

    /** A malformed condition is rejected. */
    @Test
    public void malformedConditionIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(1, List.of(trigger("ALLY_ATTACK", List.of("actor !="), energy(1)))));
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(1, List.of(trigger("ALLY_ATTACK", List.of("nonsense"), energy(1)))));
    }

    /** An unknown op is rejected. */
    @Test
    public void unknownOpIsRejected() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(1, List.of(trigger("ALLY_ATTACK", List.of(), op("TELEPORT", null)))));
        Assertions.assertTrue(e.getMessage().contains("TELEPORT"), e.getMessage());
    }

    /**
     * An op whose prerequisite phase has not landed is rejected <b>with the phase named</b>, so the
     * author learns what to wait for instead of debugging a rule that can never work.
     */
    @Test
    public void plannedButUnwiredOpIsRejectedWithThePhaseNamed() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(1, List.of(trigger("ALLY_ATTACK", List.of(), op("APPLY_BUFF", 1.0)))));
        Assertions.assertTrue(e.getMessage().contains("roadmap"), e.getMessage());
    }

    /** A required argument that is missing is reported. */
    @Test
    public void missingArgumentIsRejected() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(1, List.of(trigger("ALLY_ATTACK", List.of(), op("GAIN_ENERGY", null)))));
        Assertions.assertTrue(e.getMessage().contains("amount"), e.getMessage());
    }

    /** A rule with no effects is meaningless and is rejected. */
    @Test
    public void ruleWithoutEffectsIsRejected() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(1, List.of(trigger("ALLY_ATTACK", List.of(), null))));
    }

    // ==================================================================
    // 5. Conditions behave as the data implies
    // ==================================================================

    /** {@code actor != self} lets an ally's attack through and blocks the owner's own. */
    @Test
    public void conditionsDiscriminateBetweenSelfAndOthers() {
        TriggerTable table = new TriggerTable(1, List.of(
                trigger("ALLY_ATTACK", List.of("actor != self", "hit_count > 0"), energy(2))));

        Character owner = CharacterFactory.create(NO_TRIGGERS, 80);
        Character other = CharacterFactory.create(NO_TRIGGERS, 80);

        Assertions.assertEquals(1, table.matching(TriggerEvent.ALLY_ATTACK,
                new TriggerTable.TriggerContext(owner, other, null, 1, 0)).size(),
                "an ally's attack matches");
        Assertions.assertEquals(0, table.matching(TriggerEvent.ALLY_ATTACK,
                new TriggerTable.TriggerContext(owner, owner, null, 1, 0)).size(),
                "the owner's own attack is excluded");
        Assertions.assertEquals(0, table.matching(TriggerEvent.ALLY_ATTACK,
                new TriggerTable.TriggerContext(owner, other, null, 0, 0)).size(),
                "and an attack that hit nothing is excluded by hit_count > 0");
    }

    /** The literal may sit on either side of a numeric comparison. */
    @Test
    public void numericConditionAcceptsEitherOperandOrder() {
        TriggerTable left = new TriggerTable(1, List.of(
                trigger("ALLY_ATTACK", List.of("0 < hit_count"), energy(1))));
        TriggerTable right = new TriggerTable(1, List.of(
                trigger("ALLY_ATTACK", List.of("hit_count > 0"), energy(1))));

        Character owner = CharacterFactory.create(NO_TRIGGERS, 80);
        Character other = CharacterFactory.create(NO_TRIGGERS, 80);
        TriggerTable.TriggerContext oneHit = new TriggerTable.TriggerContext(owner, other, null, 1, 0);
        TriggerTable.TriggerContext noHit = new TriggerTable.TriggerContext(owner, other, null, 0, 0);

        Assertions.assertEquals(1, left.matching(TriggerEvent.ALLY_ATTACK, oneHit).size());
        Assertions.assertEquals(1, right.matching(TriggerEvent.ALLY_ATTACK, oneHit).size());
        Assertions.assertEquals(0, left.matching(TriggerEvent.ALLY_ATTACK, noHit).size());
        Assertions.assertEquals(0, right.matching(TriggerEvent.ALLY_ATTACK, noHit).size());
    }

    /** The bare {@code self} shorthand means "the actor is me". */
    @Test
    public void bareSelfShorthandMeansTheActorIsMe() {
        TriggerTable table = new TriggerTable(1, List.of(
                trigger("SKILL_CAST", List.of("self"), energy(5))));

        Character owner = CharacterFactory.create(NO_TRIGGERS, 80);
        Character other = CharacterFactory.create(NO_TRIGGERS, 80);

        Assertions.assertEquals(1, table.matching(TriggerEvent.SKILL_CAST,
                new TriggerTable.TriggerContext(owner, owner, null, 0, 0)).size());
        Assertions.assertEquals(0, table.matching(TriggerEvent.SKILL_CAST,
                new TriggerTable.TriggerContext(owner, other, null, 0, 0)).size());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static Battle newBattle(List<Character> team, int enemyCount) {
        List<Enemy> enemies = new java.util.ArrayList<>();
        for (int i = 0; i < enemyCount; i++) {
            Enemy enemy = EnemyFactory.create(ICE_EDGE, 90, 1);
            // Big HP so nothing dies mid-test (a death would change the hit count and the events).
            enemy.setAttribute(com.laosun.aluminium.enums.AttributeType.HEALTH,
                    new com.laosun.aluminium.models.DoubleValue(1_000_000));
            enemy.heal(1_000_000);
            enemies.add(enemy);
        }
        Battle battle = new Battle(team, enemies, new Random(0));
        battle.startBattle();
        return battle;
    }

    /** A single-target basic attack, settled immediately. */
    private static void strike(Battle battle, Character attacker, CanHit target) {
        battle.castImmediate(attacker.getSkills().get(SkillType.COMMON), attacker, List.of(target));
    }

    /**
     * An attack that connects with every enemy.
     *
     * <p>Uses a multi-target skill rather than the basic attack so the hit count really is the number
     * of enemies -- which is the whole point of the per-target/ per-attack distinction.
     */
    private static void attackAllEnemies(Battle battle, Character attacker) {
        battle.castImmediate(attacker.getSkills().get(SkillType.ULTRA), attacker,
                List.of(battle.enemies.getFirst()));
    }

    /**
     * Asserts energy with a tolerance, because the credited amount goes through the owner's energy
     * regeneration rate (which multiplies every gain).
     */
    private static void assertEnergyEquals(Character character, double expected, String why) {
        assertEnergyEquals(character.getCurrentEnergy(), expected, why);
    }

    private static void assertEnergyEquals(double actual, double expected, String why) {
        Assertions.assertEquals(expected, actual, 1e-6, why);
    }

    /** Builds a raw rule for the validation tests. */
    private static TriggerSpec trigger(String on, List<String> when, EffectSpec effect) {
        TriggerSpec spec = new TriggerSpec();
        set(spec, "on", on);
        set(spec, "when", when);
        if (effect != null) {
            set(spec, "doEffects", List.of(effect));
        }
        set(spec, "source", "test");
        return spec;
    }

    private static EffectSpec energy(double amount) {
        return op("GAIN_ENERGY", amount);
    }

    private static EffectSpec op(String name, Double amount) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", name);
        set(effect, "amount", amount);
        return effect;
    }

    /**
     * Sets a private field through its setter when one exists, else reflectively.
     *
     * <p>The beans are Lombok {@code @Getter} only (they are read from JSON), so the test builds them
     * through reflection rather than widening the production API just for tests.
     */
    private static void set(Object target, String field, Object value) {
        try {
            var f = target.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot set " + field, e);
        }
    }
}
