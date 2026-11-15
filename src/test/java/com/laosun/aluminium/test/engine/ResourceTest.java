package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.ResourceScope;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.*;
import com.laosun.aluminium.entity.Character;
import com.laosun.aluminium.enemy.Enemy;
import com.laosun.aluminium.enemy.EnemyFactory;
import com.laosun.aluminium.energy.EnergyGain;
import com.laosun.aluminium.energy.EnergyProvider;
import com.laosun.aluminium.skill.Skill;
import com.laosun.aluminium.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import com.laosun.aluminium.entity.CanHit;
import com.laosun.aluminium.damage.Damage;
import com.laosun.aluminium.state.Resource;
import com.laosun.aluminium.state.ResourceManager;
import com.laosun.aluminium.DoubleValue;

/**
 * Stack resources: the abstraction that lets "stack instead of an energy bar" characters work
 * without a class of their own.
 *
 * <p>The abstraction itself is {@link Resource} (skill points became its
 * first user); this task adds the parts that make it usable by characters: a
 * {@link ResourceManager} each combatant owns, the "became full" signal, the two trigger ops
 * ({@code GAIN_RESOURCE} / {@code SPEND_RESOURCE}), and - the piece that actually unlocks the
 * characters - an energy-provider hook that can gate an ultimate on a resource instead of on energy.
 *
 * <p><b>L3:</b> the single-clause cases are grouped into the nine scenarios below, one
 * {@code assertAll} per clause group so a failing clause still names itself. The rejection paths
 * stay their own tests: an {@code assertThrows} is not a positive clause and must not hide behind
 * one.
 */
public class ResourceTest {

    private static final int TRIBBIE = 1403;
    private static final int NO_TRIGGERS = 1003;
    private static final int ACHERON = 1308;
    private static final int ICE_EDGE = 1002011;

    /**
     * Acheron's [残梦] cap, from her character doc.
     */
    private static final int ACHERON_STACKS = 9;

    // ==================================================================
    // 1. The manager: gain/spend, the became-full signal, registration
    // ==================================================================

    /**
     * The manager's positive contract: a registered resource is gained and spent with the pool cap
     * doing the clamping; an unregistered id is inert rather than a crash; the "became full" signal
     * fires once on arrival, not while sitting at the cap; and a spend re-arms it.
     */
    @Test
    public void resourceManagerGainsSpendsAndSignalsFull() {
        Character hero = CharacterFactory.create(NO_TRIGGERS, 80);
        ResourceManager manager = hero.getResources();

        manager.register("stacks", 5, 0);
        int beforeAnyGain = manager.value("stacks");
        int gained = manager.gain("stacks", 2);
        int afterGain = manager.value("stacks");
        int spent = manager.spend("stacks", 1);
        int afterSpend = manager.value("stacks");
        int overSpend = manager.spend("stacks", 10);
        int afterOverSpend = manager.value("stacks");

        int unknownGain = manager.gain("nope", 3);
        int unknownSpend = manager.spend("nope", 3);
        int unknownValue = manager.value("nope");
        boolean unknownFull = manager.isFull("nope");
        Resource unknownResource = manager.get("nope");
        boolean unknownHas = manager.has("nope");

        Resource edge = manager.register("edge", 3, 0);
        edge.setMaxOverflow(3);                         // allows storing up to 6
        int[] fired = {0};
        manager.onBecameFull("edge", r -> fired[0]++);
        manager.gain("edge", 1);
        int firedBeforeFull = fired[0];
        manager.gain("edge", 2);
        int firedOnArrival = fired[0];
        boolean edgeFull = edge.isFull();
        manager.gain("edge", 1);                        // 4, into overflow
        manager.gain("edge", 2);                        // 6, absolute cap
        int firedInOverflow = fired[0];
        manager.gain("edge", 5);                        // entirely clamped away
        int firedAfterClampedGain = fired[0];

        manager.register("refill", 2, 0);
        int[] refillFired = {0};
        manager.onBecameFull("refill", r -> refillFired[0]++);
        manager.gain("refill", 2);
        manager.spend("refill", 2);
        manager.gain("refill", 2);
        int arrivals = refillFired[0];

        Assertions.assertAll(
                () -> Assertions.assertEquals(0, beforeAnyGain,
                        "a freshly registered resource starts at 0"),
                () -> Assertions.assertEquals(2, gained, "gain credits the full amount"),
                () -> Assertions.assertEquals(2, afterGain, "the value follows the gain"),
                () -> Assertions.assertEquals(1, spent, "spend returns what it took"),
                () -> Assertions.assertEquals(1, afterSpend, "the value follows the spend"),
                () -> Assertions.assertEquals(1, overSpend,
                        "spending more than available drains to 0 but reports only what was taken"),
                () -> Assertions.assertEquals(0, afterOverSpend, "the drained value never goes negative"));
        Assertions.assertAll(
                () -> Assertions.assertEquals(0, unknownGain, "gaining an unregistered id is a no-op"),
                () -> Assertions.assertEquals(0, unknownSpend, "spending an unregistered id is a no-op"),
                () -> Assertions.assertEquals(0, unknownValue, "an unregistered id reads as 0"),
                () -> Assertions.assertFalse(unknownFull, "an unregistered id is never full"),
                () -> Assertions.assertNull(unknownResource, "an unregistered id has no Resource"),
                () -> Assertions.assertFalse(unknownHas, "an unregistered id is not registered"));
        Assertions.assertAll(
                () -> Assertions.assertEquals(0, firedBeforeFull, "not full yet"),
                () -> Assertions.assertEquals(1, firedOnArrival, "arrived at the cap"),
                () -> Assertions.assertTrue(edgeFull, "reaching the cap is full, even with overflow allowed"),
                () -> Assertions.assertEquals(1, firedInOverflow,
                        "the edge fires once per arrival, not per gain"),
                () -> Assertions.assertEquals(1, firedAfterClampedGain,
                        "a gain that is entirely clamped away is not an arrival"));
        Assertions.assertAll(
                () -> Assertions.assertEquals(2, arrivals, "each arrival counts"));
    }

    /**
     * Registering the same id twice, or declaring a scope the engine cannot own, is a content bug
     * and is reported.
     */
    @Test
    public void registrationIsRejected() {
        Character hero = CharacterFactory.create(NO_TRIGGERS, 80);
        hero.getResources().register("stacks", 3, 0);

        Resource party = new Resource("charge", ResourceScope.PARTY, 5, 0);

        IllegalArgumentException duplicate = Assertions.assertThrows(IllegalArgumentException.class,
                () -> hero.getResources().register("stacks", 3, 0),
                "registering the same id twice is reported");
        // Note: A PARTY-scoped resource is refused rather than silently treated as personal:
        // hanging a shared pool on each character would give the team as many independent counters
        // as it has members - and nothing at runtime would report it.
        IllegalArgumentException partyScoped = Assertions.assertThrows(IllegalArgumentException.class,
                () -> hero.getResources().register(party),
                "a PARTY-scoped resource is refused");

        Assertions.assertAll(
                () -> Assertions.assertTrue(duplicate.getMessage().contains("already registered"),
                        duplicate.getMessage()),
                () -> Assertions.assertTrue(partyScoped.getMessage().contains("does not support"),
                        partyScoped.getMessage()),
                () -> Assertions.assertFalse(ResourceScope.PARTY.isWired(),
                        "PARTY scope has no real owner yet"),
                () -> Assertions.assertTrue(ResourceScope.SELF.isWired(), "SELF scope is wired"));
    }

    /**
     * Cyrene's shape: a pool of 24 that may overflow by 3.
     */
    @Test
    public void overflowAllowsStoringAboveThePool() {
        Character cyrene = CharacterFactory.create(1415, 80);
        Resource memories = cyrene.getResources().register("memories", 24, 0);
        memories.setMaxOverflow(3);                     // pool 24, may store up to 27

        int gainedToCap = cyrene.getResources().gain("memories", 24);
        boolean fullAtCap = memories.isFull();
        boolean cappedAtCap = memories.isCapped();

        int gainedToOverflow = cyrene.getResources().gain("memories", 3);
        int stored = memories.getValue();
        boolean cappedAtOverflow = memories.isCapped();
        boolean fullAtOverflow = memories.isFull();

        Assertions.assertAll(
                () -> Assertions.assertEquals(24, gainedToCap, "a full pool takes all 24"),
                () -> Assertions.assertTrue(fullAtCap, "24 is the full pool"),
                () -> Assertions.assertFalse(cappedAtCap, "full is not capped while overflow is allowed"),
                () -> Assertions.assertEquals(3, gainedToOverflow, "the overflow allowance is credited"),
                () -> Assertions.assertEquals(27, stored, "pool 24 plus overflow 3 = 27 stored"),
                () -> Assertions.assertTrue(cappedAtOverflow, "27 is the absolute cap"),
                () -> Assertions.assertTrue(fullAtOverflow,
                        "24 is enough to activate even while below the overflow ceiling"));
    }

    // ==================================================================
    // 2. The piece that unlocks the characters: gating the ultimate on a resource
    // ==================================================================

    /**
     * A stack character can cast once the <b>resource</b> is full, and cannot before. The stand-in
     * provider gains no conventional energy, so her bar stays empty the whole time: the resource,
     * not the bar, is the gate.
     *
     * <p>This is the acceptance criterion. The test uses a stand-in provider because the engine's
     * job is the hook; wiring a specific character's numbers belongs with that character's data.
     */
    @Test
    public void resourceFullGatesTheUltimate() {
        Character acheron = CharacterFactory.create(ACHERON, 80);
        acheron.getResources().register("residue", ACHERON_STACKS, 0);
        // The stand-in provider is what a stack character gets: energy is irrelevant, the resource gates.
        acheron.setEnergyProvider(new StackGatedEnergyProvider("residue"));

        Battle battle = newBattle(acheron);
        Assertions.assertFalse(battle.isUltraReady(acheron), "empty stacks -> not ready");

        battle.castImmediate(acheron.getSkills().get(SkillType.COMMON), acheron,
                List.of(firstEnemy(battle)));
        Assertions.assertAll(
                () -> Assertions.assertEquals(0, acheron.getCurrentEnergy(), 1e-9,
                        "a stack character's energy must never move"),
                () -> Assertions.assertFalse(battle.isUltraReady(acheron),
                        "and that alone cannot unlock the ultimate"));

        acheron.getResources().gain("residue", ACHERON_STACKS - 1);
        Assertions.assertFalse(battle.isUltraReady(acheron), "one short -> still not ready");

        acheron.getResources().gain("residue", 1);
        Assertions.assertAll(
                () -> Assertions.assertTrue(battle.isUltraReady(acheron), "full stacks -> ready"),
                () -> Assertions.assertTrue(battle.castUltra(acheron, List.of(firstEnemy(battle))),
                        "and the ultimate can actually be cast"));
    }

    /**
     * Conventional characters are untouched: the default rule still requires energy.
     */
    @Test
    public void conventionalGateIsUnchanged() {
        Character hero = CharacterFactory.create(NO_TRIGGERS, 80);
        Battle battle = newBattle(hero);

        Assertions.assertFalse(battle.isUltraReady(hero), "0 energy -> not ready");
        hero.setCurrentEnergy(hero.getMaxEnergy());
        Assertions.assertTrue(battle.isUltraReady(hero), "full energy -> ready");
    }

    // ==================================================================
    // 3. Sources: the trigger table fills (and spends) the resource
    // ==================================================================

    /**
     * The trigger table is the resource source, for the whole team: {@code HP_LOST} reaches the
     * victim's own table through the real damage path (a real enemy turn, not a hand-fired event)
     * and every ally's table, the condition DSL cannot yet single out "only my own wounds", and
     * {@code GAIN_RESOURCE} / {@code SPEND_RESOURCE} work through the same pathway.
     */
    @Test
    public void triggerTableIsTheResourceSource() {
        Character victim = CharacterFactory.create(TRIBBIE, 80);
        victim.getResources().register("grief", 999, 0);
        victim.getResources().register("charge", 5, 0);
        victim.setTriggerTable(new TriggerTable(TRIBBIE, List.of(
                rule("HP_LOST", null, gainResource("grief", 1.0, false)))));

        Character watcher = CharacterFactory.create(NO_TRIGGERS, 80);
        watcher.getResources().register("grief", 999, 0);
        watcher.setTriggerTable(new TriggerTable(NO_TRIGGERS, List.of(
                rule("HP_LOST", null, gainResource("grief", 1.0, false)))));

        Character selfWatcher = CharacterFactory.create(NO_TRIGGERS, 80);
        selfWatcher.getResources().register("grief", 999, 0);
        // `self` here means "the actor is me"; for HP_LOST the actor is whoever caused the loss, so
        // this does NOT mean "the loss happened to me".
        selfWatcher.setTriggerTable(new TriggerTable(NO_TRIGGERS, List.of(
                rule("HP_LOST", List.of("self"), gainResource("grief", 1.0, false)))));

        Battle battle = newBattle(List.of(victim, watcher, selfWatcher));
        Enemy enemy = firstEnemy(battle);

        double hpBefore = victim.getCurrentHp();
        letEnemyAttack(battle, enemy, victim);
        double hpAfter = victim.getCurrentHp();
        int victimGrief = victim.getResources().value("grief");
        int watcherGrief = watcher.getResources().value("grief");
        int selfWatcherGrief = selfWatcher.getResources().value("grief");

        Assertions.assertAll(
                () -> Assertions.assertTrue(hpAfter < hpBefore, "the enemy attack should have hurt"),
                () -> Assertions.assertTrue(hpAfter < victim.getMaxHp(), "the victim was hurt"),
                () -> Assertions.assertEquals(1, victimGrief,
                        "one HP-loss event -> one stack, through the real damage path"),
                () -> Assertions.assertEquals(1, watcherGrief,
                        "an ally does see a teammate's HP loss — that is the point of a team event"),
                () -> Assertions.assertEquals(0, selfWatcherGrief,
                        "`self` excludes it because the enemy is the actor — but that is an accident of wording, "
                                + "not a subject filter: a teammate-caused loss would still fire it"));

        // GAIN_RESOURCE / SPEND_RESOURCE through the trigger pathway, on the same table.
        victim.setTriggerTable(new TriggerTable(TRIBBIE, List.of(
                rule("ALLY_ATTACK", List.of("actor != self"), gainResource("charge", 3, false)))));
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, battle.enemies().getFirst(), null, 1, 0));
        int chargeGained = victim.getResources().value("charge");

        // A second rule spends it: a spend that fits succeeds.
        victim.setTriggerTable(new TriggerTable(TRIBBIE, List.of(
                rule("ALLY_ATTACK", List.of("actor != self"), spendResource("charge", 3)))));
        battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, battle.enemies().getFirst(), null, 1, 0));
        int chargeSpent = victim.getResources().value("charge");

        Assertions.assertAll(
                () -> Assertions.assertEquals(3, chargeGained,
                        "GAIN_RESOURCE credits the rule's amount"),
                () -> Assertions.assertEquals(0, chargeSpent, "spent back to 0"));
    }

    /**
     * The "1 stack per 1 HP lost" arithmetic itself, at the level that can actually express it: a
     * manager call, clamped to the pool.
     *
     * <p>Castorice's [新蕊] converts loss at 1:1, and that is a property of the manager
     * ({@code gain(id, lost)}); what the trigger vocabulary supplies is the *event*. Splitting the two
     * keeps each half testable instead of pretending a literal-amount rule scales.
     */
    @Test
    public void oneStackPerPointOfLossIsAManagerCall() {
        Character hero = CharacterFactory.create(TRIBBIE, 80);
        hero.getResources().register("grief", 999, 0);

        int lost = 398;
        int gainedByLoss = hero.getResources().gain("grief", lost);
        int afterLoss = hero.getResources().value("grief");
        int gainedBeyondCap = hero.getResources().gain("grief", 9999);
        int afterCap = hero.getResources().value("grief");

        Assertions.assertAll(
                () -> Assertions.assertEquals(lost, gainedByLoss, "1 point of loss credits 1 stack"),
                () -> Assertions.assertEquals(398, afterLoss, "the value follows the 1:1 credit"),
                () -> Assertions.assertEquals(601, gainedBeyondCap, "the last gain is clamped to the pool"),
                () -> Assertions.assertEquals(999, afterCap, "the pool never passes its cap"));
    }

    /**
     * A spend that does <b>not</b> fit, or that names a resource nobody registered, is reported
     * rather than silently swallowed.
     *
     * <p>Skill points legitimately mean "you cannot press the button" when short; a trigger rule
     * spending a resource was written believing the stacks would be there, so failing quietly would
     * hide a content bug.
     */
    @Test
    public void spendFailuresAreReported() {
        Character hero = CharacterFactory.create(TRIBBIE, 80);
        hero.getResources().register("charge", 5, 0);
        hero.setTriggerTable(new TriggerTable(TRIBBIE, List.of(
                rule("ALLY_ATTACK", null, spendResource("charge", 5)))));

        Battle battle = newBattle(hero);
        IllegalStateException tooShort = Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, battle.enemies().getFirst(), null, 1, 0)),
                "a spend of more than is available is reported");

        hero.setTriggerTable(new TriggerTable(TRIBBIE, List.of(
                rule("ALLY_ATTACK", null, spendResource("ghost", 1)))));
        IllegalStateException unknownResource = Assertions.assertThrows(IllegalStateException.class,
                () -> battle.fire(com.laosun.aluminium.trigger.TriggerPayload.Simple.of(TriggerEvent.ALLY_ATTACK, battle.enemies().getFirst(), null, 1, 0)),
                "a spend on an unregistered resource is reported");

        Assertions.assertAll(
                () -> Assertions.assertTrue(tooShort.getMessage().contains("needs 5"),
                        tooShort.getMessage()),
                () -> Assertions.assertTrue(unknownResource.getMessage().contains("no such resource"),
                        unknownResource.getMessage()));
    }

    /**
     * A resource op without a {@code resource} name is rejected at load time.
     */
    @Test
    public void resourceOpWithoutANameIsRejected() {
        EffectSpec incomplete = op("GAIN_RESOURCE", 1.0);
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(1, List.of(rule("ALLY_ATTACK", null, incomplete))),
                "an op without a resource name is rejected");
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
        public EnergyGain onSkillCast(CanHit user, Skill skill,
                                      java.util.Set<? extends CanHit> hitTargets) {
            return null;
        }

        @Override
        public EnergyGain onUltCast(CanHit user, Skill skill) {
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
        Battle battle = BattleFixture.battle(team, enemies);
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
            if (battle.currentMove() == null) {
                break;
            }
            CanHit actor = battle.currentMove().getCanHit();
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
        com.laosun.aluminium.test.support.TriggerSpecs.set(target, field, value);
    }
}
