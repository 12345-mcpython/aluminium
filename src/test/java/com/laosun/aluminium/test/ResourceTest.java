package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.ResourceScope;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.Enemy;
import com.laosun.aluminium.models.EnemyFactory;
import com.laosun.aluminium.models.Resource;
import com.laosun.aluminium.models.ResourceManager;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.energy.EnergyGain;
import com.laosun.aluminium.models.energy.EnergyProvider;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Stack resources (P8-8): the abstraction that lets "stack instead of an energy bar" characters work
 * without a class of their own.
 *
 * <p>The abstraction itself is {@link Resource} (extracted during P8-4, where skill points became its
 * first user); this task adds the parts that make it usable by characters: a
 * {@link ResourceManager} each combatant owns, the "became full" signal, the two trigger ops
 * ({@code GAIN_RESOURCE} / {@code SPEND_RESOURCE}), and — the piece that actually unlocks the
 * characters — an energy-provider hook that can gate an ultimate on a resource instead of on energy.
 */
public class ResourceTest {

    private static final int TRIBBIE = 1403;
    private static final int NO_TRIGGERS = 1003;
    private static final int ACHERON = 1308;
    private static final int ICE_EDGE = 1002011;

    /** Acheron's 【残梦】 cap, from her character doc. */
    private static final int ACHERON_STACKS = 9;

    // ==================================================================
    // 1. The manager: registration, scope, gain/spend
    // ==================================================================

    /** A registered resource can be gained and spent through the manager. */
    @Test
    public void managerGainsAndSpends() {
        Character hero = CharacterFactory.create(NO_TRIGGERS, 80);
        ResourceManager manager = hero.getResources();

        manager.register("stacks", 5, 0);
        Assertions.assertEquals(0, manager.value("stacks"));

        Assertions.assertEquals(2, manager.gain("stacks", 2));
        Assertions.assertEquals(2, manager.value("stacks"));

        Assertions.assertEquals(1, manager.spend("stacks", 1));
        Assertions.assertEquals(1, manager.value("stacks"));

        // spending more than available drains to 0 but reports only what was taken
        Assertions.assertEquals(1, manager.spend("stacks", 10));
        Assertions.assertEquals(0, manager.value("stacks"));
    }

    /** Asking about a resource that was never registered is a no-op, not a crash. */
    @Test
    public void unknownResourceIsInert() {
        Character hero = CharacterFactory.create(NO_TRIGGERS, 80);
        Assertions.assertEquals(0, hero.getResources().gain("nope", 3));
        Assertions.assertEquals(0, hero.getResources().spend("nope", 3));
        Assertions.assertEquals(0, hero.getResources().value("nope"));
        Assertions.assertFalse(hero.getResources().isFull("nope"));
        Assertions.assertNull(hero.getResources().get("nope"));
        Assertions.assertFalse(hero.getResources().has("nope"));
    }

    /** Registering the same id twice is a bug in the content, and is reported. */
    @Test
    public void duplicateRegistrationIsRejected() {
        Character hero = CharacterFactory.create(NO_TRIGGERS, 80);
        hero.getResources().register("stacks", 3, 0);

        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> hero.getResources().register("stacks", 3, 0));
        Assertions.assertTrue(e.getMessage().contains("already registered"), e.getMessage());
    }

    /**
     * ⚠ A {@code PARTY}-scoped resource is <b>refused</b> rather than silently treated as personal.
     *
     * <p>Hanging a shared pool on each character would give the team as many independent counters as
     * it has members — and nothing at runtime would report it.
     */
    @Test
    public void partyScopedResourceIsRefusedUntilItHasARealOwner() {
        Character hero = CharacterFactory.create(NO_TRIGGERS, 80);
        Resource party = new Resource("charge", ResourceScope.PARTY, 5, 0);

        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> hero.getResources().register(party));
        Assertions.assertTrue(e.getMessage().contains("does not support"), e.getMessage());
        Assertions.assertFalse(ResourceScope.PARTY.isWired());
        Assertions.assertTrue(ResourceScope.SELF.isWired());
    }

    /** The "became full" signal fires on the rising edge, once. */
    @Test
    public void becameFullFiresOnceOnArrival() {
        Character hero = CharacterFactory.create(NO_TRIGGERS, 80);
        Resource stacks = hero.getResources().register("stacks", 3, 0);

        int[] fired = {0};
        hero.getResources().onBecameFull("stacks", r -> fired[0]++);

        hero.getResources().gain("stacks", 1);
        Assertions.assertEquals(0, fired[0], "not full yet");
        hero.getResources().gain("stacks", 2);
        Assertions.assertEquals(1, fired[0], "arrived at the cap");
        Assertions.assertTrue(stacks.isFull());
    }

    /**
     * ⚠ Sitting at the cap does <b>not</b> keep re-firing.
     *
     * <p>This is why the signal is a rising edge rather than a level: Cyrene can keep collecting into
     * overflow above her pool (24 → 27), so a level signal would fire on every one of those gains even
     * though she arrived at the cap only once.
     */
    @Test
    public void stayingAtTheCapDoesNotRefire() {
        Character hero = CharacterFactory.create(NO_TRIGGERS, 80);
        Resource stacks = hero.getResources().register("stacks", 3, 0);
        stacks.setMaxOverflow(3);                       // allows storing up to 6

        int[] fired = {0};
        hero.getResources().onBecameFull("stacks", r -> fired[0]++);

        hero.getResources().gain("stacks", 3);          // 3 = cap, arrival
        hero.getResources().gain("stacks", 1);          // 4, into overflow
        hero.getResources().gain("stacks", 2);          // 6, absolute cap
        Assertions.assertEquals(1, fired[0], "the edge fires once per arrival, not per gain");

        // A gain that is entirely clamped away is not an arrival either.
        hero.getResources().gain("stacks", 5);
        Assertions.assertEquals(1, fired[0]);
    }

    /** Spending below the cap and coming back re-arms the edge. */
    @Test
    public void refillsAfterSpendingFireAgain() {
        Character hero = CharacterFactory.create(NO_TRIGGERS, 80);
        hero.getResources().register("stacks", 2, 0);

        int[] fired = {0};
        hero.getResources().onBecameFull("stacks", r -> fired[0]++);

        hero.getResources().gain("stacks", 2);
        hero.getResources().spend("stacks", 2);
        hero.getResources().gain("stacks", 2);
        Assertions.assertEquals(2, fired[0], "each arrival counts");
    }

    /** Cyrene's shape: pool 24 that may overflow to 27. */
    @Test
    public void overflowAllowsStoringAboveThePool() {
        Character cyrene = CharacterFactory.create(1415, 80);
        Resource memories = cyrene.getResources().register("memories", 24, 0);
        memories.setMaxOverflow(3);                     // pool 24, may store up to 27

        Assertions.assertEquals(24, cyrene.getResources().gain("memories", 24));
        Assertions.assertTrue(memories.isFull());
        Assertions.assertFalse(memories.isCapped());

        Assertions.assertEquals(3, cyrene.getResources().gain("memories", 3));
        Assertions.assertEquals(27, memories.getValue());
        Assertions.assertTrue(memories.isCapped());

        // 24 is enough to activate even while below the overflow ceiling.
        Assertions.assertTrue(memories.isFull());
    }

    // ==================================================================
    // 2. The piece that unlocks the characters: gating the ultimate on a resource
    // ==================================================================

    /**
     * A stack character can cast once the <b>resource</b> is full, and cannot before.
     *
     * <p>This is the acceptance criterion from the roadmap. The test uses a stand-in provider because
     * the engine's job is the hook; wiring a specific character's numbers belongs with that
     * character's data.
     */
    @Test
    public void resourceFullMakesTheUltimateAvailable() {
        Character acheron = CharacterFactory.create(ACHERON, 80);
        acheron.getResources().register("residue", ACHERON_STACKS, 0);
        // The stand-in provider is what a stack character gets: energy is irrelevant, the resource gates.
        acheron.setEnergyProvider(new StackGatedEnergyProvider("residue"));

        Battle battle = newBattle(acheron);

        Assertions.assertFalse(battle.isUltraReady(acheron), "empty stacks -> not ready");
        acheron.getResources().gain("residue", ACHERON_STACKS - 1);
        Assertions.assertFalse(battle.isUltraReady(acheron), "one short -> still not ready");

        acheron.getResources().gain("residue", 1);
        Assertions.assertTrue(battle.isUltraReady(acheron), "full stacks -> ready");

        Assertions.assertTrue(battle.castUltra(acheron, List.of(firstEnemy(battle))),
                "and the ultimate can actually be cast");
    }

    /** The stand-in provider gains no conventional energy, so her bar stays empty the whole time. */
    @Test
    public void stackCharacterGainsNoConventionalEnergy() {
        Character acheron = CharacterFactory.create(ACHERON, 80);
        acheron.getResources().register("residue", ACHERON_STACKS, 0);
        acheron.setEnergyProvider(new StackGatedEnergyProvider("residue"));

        Battle battle = newBattle(acheron);
        battle.castImmediate(acheron.getSkills().get(SkillType.COMMON), acheron,
                List.of(firstEnemy(battle)));

        Assertions.assertEquals(0, acheron.getCurrentEnergy(), 1e-9,
                "a stack character's energy must never move");
        Assertions.assertFalse(battle.isUltraReady(acheron), "and that alone cannot unlock the ultimate");
    }

    /** Conventional characters are untouched: the default rule still requires energy. */
    @Test
    public void conventionalGateIsUnchanged() {
        Character hero = CharacterFactory.create(NO_TRIGGERS, 80);
        Battle battle = newBattle(hero);

        Assertions.assertFalse(battle.isUltraReady(hero), "0 energy -> not ready");
        hero.setCurrentEnergy(hero.getMaxEnergy());
        Assertions.assertTrue(battle.isUltraReady(hero), "full energy -> ready");
    }

    // ==================================================================
    // 3. Sources: the trigger table fills the resource
    // ==================================================================

    /**
     * {@code HP_LOST} really reaches the owner's table: the wiring is byte-for-byte (real enemy turn →
     * {@code Battle.applyDamage} → the event → the rule → the manager).
     *
     * <p>Driven through a **real enemy turn** rather than firing the event by hand, so the test covers
     * the whole chain.
     *
     * <p>⚠ Scope note: the rule below grants a <b>fixed 1</b> stack per HP-loss event. That is the
     * strongest rule the current vocabulary can express — the effect's amount is a literal, so
     * "grant as many stacks as HP lost" needs amount arithmetic it does not have yet. The 1:1
     * conversion itself is a manager-level property and is pinned by
     * {@link #oneStackPerPointOfLossIsAManagerCall()}.
     */
    @Test
    public void hpLossReachesTheOwnersTriggerTable() {
        Character hero = CharacterFactory.create(TRIBBIE, 80);
        hero.getResources().register("grief", 999, 0);
        hero.setTriggerTable(new TriggerTable(TRIBBIE, List.of(
                rule("HP_LOST", null, gainResource("grief", 1.0, false)))));

        Battle battle = newBattle(hero);
        Enemy enemy = firstEnemy(battle);

        double hpBefore = hero.getCurrentHp();
        letEnemyAttack(battle, enemy, hero);

        Assertions.assertTrue(hero.getCurrentHp() < hpBefore, "the enemy attack should have hurt");
        Assertions.assertEquals(1, hero.getResources().value("grief"),
                "one HP-loss event -> one stack, through the real damage path");
    }

    /**
     * The "1 stack per 1 HP lost" arithmetic itself, at the level that can actually express it.
     *
     * <p>Castorice's 【新蕊】 converts loss at 1:1, and that is a property of the manager
     * ({@code gain(id, lost)}); what the trigger vocabulary supplies is the *event*. Splitting the two
     * keeps each half testable instead of pretending a literal-amount rule scales.
     */
    @Test
    public void oneStackPerPointOfLossIsAManagerCall() {
        Character hero = CharacterFactory.create(TRIBBIE, 80);
        hero.getResources().register("grief", 999, 0);

        int lost = 398;
        Assertions.assertEquals(lost, hero.getResources().gain("grief", lost));
        Assertions.assertEquals(398, hero.getResources().value("grief"));

        // Clamped to the pool, never past it.
        Assertions.assertEquals(601, hero.getResources().gain("grief", 9999));
        Assertions.assertEquals(999, hero.getResources().value("grief"));
    }

    /**
     * {@code HP_LOST} is a **team** event: it reaches the victim *and* every ally, which is what lets a
     * healer or an off-field stack character react to a teammate's wounds.
     *
     * <p>⚠ This is the opposite of what I first assumed (I wrote a test asserting a bystander stays at
     * 0 stacks, and it failed). The engine's broadcast rule for this event is documented in
     * {@code engine.md} §4.1 as "related parties + our whole side", so an ally *does* see it.
     * A character that only cares about its own wounds therefore needs a subject filter
     * ({@code target == self}) — see {@link #subjectFilterIsNotExpressibleYet()} for where that stands.
     */
    @Test
    public void hpLossReachesEveryAllyNotJustTheVictim() {
        Character victim = CharacterFactory.create(TRIBBIE, 80);
        Character watcher = CharacterFactory.create(NO_TRIGGERS, 80);
        watcher.getResources().register("grief", 999, 0);
        watcher.setTriggerTable(new TriggerTable(NO_TRIGGERS, List.of(
                rule("HP_LOST", null, gainResource("grief", 1.0, false)))));

        Battle battle = newBattle(List.of(victim, watcher));
        Enemy enemy = firstEnemy(battle);

        letEnemyAttack(battle, enemy, victim);

        Assertions.assertTrue(victim.getCurrentHp() < victim.getMaxHp(), "the victim was hurt");
        Assertions.assertEquals(1, watcher.getResources().value("grief"),
                "an ally does see a teammate's HP loss — that is the point of a team event");
    }

    /**
     * ⚠ Known limit: the condition DSL cannot yet say "only my own wounds".
     *
     * <p>{@code self} compares the **actor** (who caused the event) with the table's owner, and the
     * numeric variables only cover {@code hit_count}. A rule watching {@code HP_LOST} therefore cannot
     * distinguish "I was hit" from "an ally was hit", so it fires for both. That is fine for Castorice
     * (who wants the whole team's losses) but wrong for a personal-stacks character.
     *
     * <p>This test documents the gap rather than hiding it: when a {@code target} variable lands, move
     * this test to asserting the filtered behaviour.
     */
    @Test
    public void subjectFilterIsNotExpressibleYet() {
        Character victim = CharacterFactory.create(TRIBBIE, 80);
        Character watcher = CharacterFactory.create(NO_TRIGGERS, 80);
        watcher.getResources().register("grief", 999, 0);
        // `self` here means "the actor is me"; for HP_LOST the actor is whoever caused the loss, so
        // this does NOT mean "the loss happened to me".
        watcher.setTriggerTable(new TriggerTable(NO_TRIGGERS, List.of(
                rule("HP_LOST", List.of("self"), gainResource("grief", 1.0, false)))));

        Battle battle = newBattle(List.of(victim, watcher));
        Enemy enemy = firstEnemy(battle);

        letEnemyAttack(battle, enemy, victim);

        Assertions.assertEquals(0, watcher.getResources().value("grief"),
                "`self` excludes it because the enemy is the actor — but that is an accident of wording, "
                        + "not a subject filter: a teammate-caused loss would still fire it");
    }

    /** {@code GAIN_RESOURCE} and {@code SPEND_RESOURCE} work through the trigger pathway. */
    @Test
    public void triggerCanGainAndSpendResource() {
        Character hero = CharacterFactory.create(TRIBBIE, 80);
        hero.getResources().register("charge", 5, 0);

        hero.setTriggerTable(new TriggerTable(TRIBBIE, List.of(
                rule("ALLY_ATTACK", List.of("actor != self"), gainResource("charge", 3, false)))));

        Battle battle = newBattle(hero);
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, battle.enemies.getFirst(), null, 1, 0);
        Assertions.assertEquals(3, hero.getResources().value("charge"));

        // A second rule spends it: a spend that fits succeeds.
        hero.setTriggerTable(new TriggerTable(TRIBBIE, List.of(
                rule("ALLY_ATTACK", List.of("actor != self"), spendResource("charge", 3)))));
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, battle.enemies.getFirst(), null, 1, 0);
        Assertions.assertEquals(0, hero.getResources().value("charge"), "spent back to 0");
    }

    /**
     * A spend that does <b>not</b> fit is reported, not silently swallowed.
     *
     * <p>Skill points legitimately mean "you cannot press the button" when short; a trigger rule
     * spending a resource was written believing the stacks would be there, so failing quietly would
     * hide a content bug.
     */
    @Test
    public void spendingMoreThanAvailableIsReported() {
        Character hero = CharacterFactory.create(TRIBBIE, 80);
        hero.getResources().register("charge", 5, 0);
        hero.setTriggerTable(new TriggerTable(TRIBBIE, List.of(
                rule("ALLY_ATTACK", null, spendResource("charge", 5)))));

        Battle battle = newBattle(hero);
        IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fireTriggers(TriggerEvent.ALLY_ATTACK, battle.enemies.getFirst(), null, 1, 0));
        Assertions.assertTrue(e.getMessage().contains("needs 5"), e.getMessage());
    }

    /** Spending a resource that was never registered is reported too. */
    @Test
    public void spendingAnUnknownResourceIsReported() {
        Character hero = CharacterFactory.create(TRIBBIE, 80);
        hero.setTriggerTable(new TriggerTable(TRIBBIE, List.of(
                rule("ALLY_ATTACK", null, spendResource("ghost", 1)))));

        Battle battle = newBattle(hero);
        IllegalStateException e = Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fireTriggers(TriggerEvent.ALLY_ATTACK, battle.enemies.getFirst(), null, 1, 0));
        Assertions.assertTrue(e.getMessage().contains("no such resource"), e.getMessage());
    }

    /** A resource op without a {@code resource} name is rejected at load time. */
    @Test
    public void resourceOpWithoutANameIsRejected() {
        EffectSpec incomplete = op("GAIN_RESOURCE", 1.0);
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(1, List.of(rule("ALLY_ATTACK", null, incomplete))));
        Assertions.assertTrue(e.getMessage().contains("resource"), e.getMessage());
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * A stand-in for "this character's ultimate is gated by a stack resource, not by energy".
     *
     * <p>Deliberately a test double: the engine's contribution is the {@code canCastUltra} hook, and
     * the real providers belong with the characters whose numbers they encode.
     */
    private static final class StackGatedEnergyProvider implements EnergyProvider {
        private final String resourceId;

        StackGatedEnergyProvider(String resourceId) {
            this.resourceId = resourceId;
        }

        @Override
        public boolean canCastUltra(CanHit user, double energyCost) {
            return user != null && user.getResources().isFull(resourceId);
        }

        // No conventional energy from any source.
        @Override
        public EnergyGain onSkillCast(CanHit user, com.laosun.aluminium.models.Skill skill,
                                     java.util.Set<? extends CanHit> hitTargets) {
            return null;
        }

        @Override
        public EnergyGain onUltCast(CanHit user, com.laosun.aluminium.models.Skill skill) {
            return null;
        }

        @Override
        public EnergyGain onTakingHit(CanHit target, Damage damage) {
            return null;
        }

        @Override
        public EnergyGain onKill(CanHit attacker, CanHit target) {
            return null;
        }

        @Override
        public EnergyGain onBreak(CanHit attacker, CanHit target) {
            return null;
        }
    }

    private static Battle newBattle(Character hero) {
        return newBattle(List.of(hero));
    }

    private static Battle newBattle(List<Character> team) {
        Enemy enemy = EnemyFactory.create(ICE_EDGE, 90, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(1_000_000));
        enemy.heal(1_000_000);
        List<Enemy> enemies = new ArrayList<>();
        enemies.add(enemy);
        Battle battle = new Battle(team, enemies, new Random(0));
        battle.startBattle();
        return battle;
    }

    /**
     * Advances the battle until {@code attacker} takes a turn and hits {@code victim}.
     *
     * <p>Used instead of firing {@code HP_LOST} by hand so the test exercises the real damage path.
     * The enemy has the higher speed in these setups, so its turn usually comes first.
     */
    private static void letEnemyAttack(Battle battle, Enemy attacker, CanHit victim) {
        for (int i = 0; i < 30; i++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            CanHit actor = battle.currentMove.getCanHit();
            battle.beforeMove();
            if (actor == attacker) {
                battle.performAction(attacker.getSkills().get(SkillType.COMMON), List.of(victim));
                battle.afterMove();
                return;
            }
            // Someone else's turn: skip it without acting.
            battle.afterMove();
        }
        Assertions.fail("the enemy never got a turn");
    }

    private static Enemy firstEnemy(Battle battle) {
        return battle.enemyUnits().getFirst();
    }

    // --- tiny builders so a rule can be written readably in one line -------------------------

    private static TriggerSpec rule(String on, List<String> when, EffectSpec effect) {
        TriggerSpec spec = new TriggerSpec();
        set(spec, "on", on);
        if (when != null) {
            set(spec, "when", when);
        }
        set(spec, "doEffects", List.of(effect));
        set(spec, "source", "test");
        return spec;
    }

    private static EffectSpec gainResource(String id, double amount, boolean perTarget) {
        EffectSpec effect = op("GAIN_RESOURCE", amount);
        set(effect, "resource", id);
        if (perTarget) {
            set(effect, "perTarget", Boolean.TRUE);
        }
        return effect;
    }

    private static EffectSpec spendResource(String id, double amount) {
        EffectSpec effect = op("SPEND_RESOURCE", amount);
        set(effect, "resource", id);
        return effect;
    }

    private static EffectSpec op(String name, Double amount) {
        EffectSpec effect = new EffectSpec();
        set(effect, "op", name);
        set(effect, "amount", amount);
        return effect;
    }

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
