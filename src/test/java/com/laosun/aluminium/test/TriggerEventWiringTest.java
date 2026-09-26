package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.DamageElement;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.CanHit;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.Damage;
import com.laosun.aluminium.models.DoubleValue;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The two trigger-table events that were declared but not emitted ({@code TURN_START},
 * {@code TAKING_HIT}) and the one condition variable the relic texts needed
 * ({@code hp_percent}).
 *
 * <h2>The three things this class exists to pin</h2>
 * <ol>
 *   <li><b>{@code TURN_START} is a data subscription, not a second turn abstraction.</b> Turn
 *       boundaries stay {@code MoveEvent.beforeMove/afterMove} for buffs
 *       ({@code EventBusTest.turnBoundariesAreStillMoveEvent}), and this event adds no buff interface.
 *       It fires once per turn, for the character whose turn it is.</li>
 *   <li><b>{@code TAKING_HIT} is not {@code HP_LOST}.</b> A hit a shield absorbed entirely must fire
 *       {@code TAKING_HIT} and must not fire {@code HP_LOST} — that distinction is the whole reason the
 *       second event exists, and it is asserted directly rather than described in a comment.</li>
 *   <li><b>{@code hp_percent} is a closed-set variable.</b> It reads the owner's own HP fraction, and
 *       an unknown variable still fails at load time with the complete list of known names.</li>
 * </ol>
 */
public class TriggerEventWiringTest {

    /** The owner of the rule needs to be distinguishable from everyone else in the assertions. */
    private static final int HIMEKO = 1003;

    /** Enough HP that one basic attack never kills it. */
    private static final double ENEMY_HP = 1_000_000;

    private static final double TOLERANCE = 1e-9;

    // ==================================================================
    // 1. TURN_START
    // ==================================================================

    /** The rule fires on the wearer's own turn and gives the wearer the modifier. */
    @Test
    public void turnStartFiresWhenTheWearersTurnBegins() {
        Character hero = character(heroTable(TriggerSpecs.rule("TURN_START",
                List.of("actor == self"), TriggerSpecs.modifyAttr("ATTACK", 0.5, 2))));
        Battle battle = battle(hero, plain(HIMEKO));

        Assertions.assertEquals(0, buffsOn(hero, AttributeType.ATTACK).size(),
                "precondition: nothing has fired yet");

        reachTurnOf(battle, hero);

        Assertions.assertEquals(1, buffsOn(hero, AttributeType.ATTACK).size(),
                "TURN_START must fire for the character whose turn began");
        Assertions.assertEquals(0.5, buffsOn(hero, AttributeType.ATTACK).getFirst().getValue(), TOLERANCE);
    }

    /**
     * Another character's table does not fire on my turn.
     *
     * <p>The event is delivered to <b>every</b> table (each evaluates the context with itself as
     * {@code self}), so a table that forgot its condition would fire for the whole team — which is
     * exactly what a naive emitter gets wrong. The observable is energy rather than a stat modifier: a
     * buff would still be sitting there from an earlier turn, while a credit lands only when the rule
     * actually fires.
     */
    @Test
    public void turnStartDoesNotFireForEveryoneAtOnce() {
        Character watched = plain(HIMEKO);
        Character actor = plain(HIMEKO);
        actor.setTriggerTable(heroTable(TriggerSpecs.rule("TURN_START", List.of("actor == self"),
                TriggerSpecs.gainEnergy(30))));
        Battle battle = battle(watched, actor);

        int actorTurns = 0;
        for (int step = 0; step < 10; step++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            CanHit current = battle.currentMove.getCanHit();
            double energyBefore = actor.getCurrentEnergy();
            battle.beforeMove();
            if (current == actor) {
                actorTurns++;
                Assertions.assertEquals(energyBefore + 30, actor.getCurrentEnergy(), TOLERANCE,
                        "the rule must fire on the wearer's own turn (turn " + actorTurns + ")");
            } else {
                Assertions.assertEquals(energyBefore, actor.getCurrentEnergy(), TOLERANCE,
                        "the rule belongs to " + actor.getName() + " and must not fire on "
                                + current.getName() + "'s turn");
            }
            Assertions.assertEquals(0, watched.getCurrentEnergy(), TOLERANCE,
                    "a character with no table must never react");
            battle.afterMove();
        }
        Assertions.assertTrue(actorTurns >= 2, "precondition: the wearer acted at least twice");
    }

    /**
     * The same table evaluated with {@code target == self} fires too.
     *
     * <p>The emitter hands the actor over as <b>both</b> {@code actor} and {@code target}, so the two
     * natural ways to spell "the wearer's turn" cannot diverge — a rule that silently never fires is the
     * worst outcome for a data rule, and the action bar makes that failure mode very hard to notice.
     */
    @Test
    public void turnStartWorksWithEitherSpellingOfSelf() {
        Character hero = character(heroTable(TriggerSpecs.rule("TURN_START",
                List.of("target == self"), TriggerSpecs.modifyAttr("ATTACK", 0.5, 2))));
        Battle battle = battle(hero, plain(HIMEKO));

        reachTurnOf(battle, hero);

        Assertions.assertEquals(1, buffsOn(hero, AttributeType.ATTACK).size(),
                "'target == self' must mean the same thing as 'actor == self' on a turn start");
    }

    // ==================================================================
    // 2. TAKING_HIT vs HP_LOST
    // ==================================================================

    /**
     * <b>The distinction.</b> A hit that a shield absorbs entirely fires {@code TAKING_HIT} and not
     * {@code HP_LOST}.
     *
     * <p>Two identical rules on the same target, one per event: after the shielded hit, only the
     * TAKING_HIT one has installed its modifier. That is precisely what an ability like
     * "after the wearer attacks <em>or is hit</em>" needs — behind a shielder, HP loss never happens, so
     * keying off {@code HP_LOST} would make the relic silently do nothing.
     */
    @Test
    public void takingHitFiresEvenWhenTheShieldAbsorbsEverything() {
        Character hero = plain(HIMEKO);
        Character shielded = character(watchTable());
        Battle battle = battle(hero, shielded);

        battle.grantShield(shielded, 10_000_000);
        double hpBefore = shielded.getCurrentHp();

        battle.applyDamage(shielded, damage(hero, shielded, 5_000));

        Assertions.assertEquals(hpBefore, shielded.getCurrentHp(), TOLERANCE,
                "precondition: the shield ate the whole hit");
        Assertions.assertEquals(1, buffsOn(shielded, AttributeType.ATTACK).size(),
                "TAKING_HIT must fire for a hit a shield absorbed");
        Assertions.assertEquals(0, buffsOn(shielded, AttributeType.DEFENCE).size(),
                "HP_LOST must NOT fire when no HP was lost -- the two events are different facts");
    }

    /** A hit that really costs HP fires both events. */
    @Test
    public void takingHitAndHpLostBothFireOnRealDamage() {
        Character hero = plain(HIMEKO);
        Character victim = survivor(HIMEKO, watchTable());
        Battle battle = battle(hero, victim);

        battle.applyDamage(victim, damage(hero, victim, 5_000));

        Assertions.assertEquals(1, buffsOn(victim, AttributeType.ATTACK).size(),
                "TAKING_HIT fires on an unshielded hit");
        Assertions.assertEquals(1, buffsOn(victim, AttributeType.DEFENCE).size(),
                "and so does HP_LOST, because HP really was lost");
    }

    /** The event carries who was hit, so {@code target == self} is the wearer. */
    @Test
    public void takingHitTargetsTheOneWhoWasHit() {
        Character hero = plain(HIMEKO);
        Character actor = character(heroTable(TriggerSpecs.rule("TAKING_HIT",
                List.of("target == self"), TriggerSpecs.modifyAttr("ATTACK", 0.5, 2))));
        Character bystander = character(heroTable(TriggerSpecs.rule("TAKING_HIT",
                List.of("target == self"), TriggerSpecs.modifyAttr("ATTACK", 0.5, 2))));
        Battle battle = battle(hero, actor, bystander);

        battle.applyDamage(actor, damage(hero, actor, 1_000));

        Assertions.assertEquals(1, buffsOn(actor, AttributeType.ATTACK).size(),
                "the one who was hit receives the rule");
        Assertions.assertEquals(0, buffsOn(bystander, AttributeType.ATTACK).size(),
                "a teammate who was not hit must not receive it");
    }

    /** An invulnerable target is not hit at all, so no event fires (nothing landed). */
    @Test
    public void anInvulnerableTargetTakesNoHitEvent() {
        Character hero = plain(HIMEKO);
        Character boss = survivor(HIMEKO, watchTable());
        Battle battle = battle(hero, boss);

        boss.setInvulnerable(true);
        battle.applyDamage(boss, damage(hero, boss, 5_000));

        Assertions.assertEquals(0, buffsOn(boss, AttributeType.ATTACK).size(),
                "an invulnerable target is not hit, so TAKING_HIT must not fire");
        Assertions.assertEquals(0, buffsOn(boss, AttributeType.DEFENCE).size());
    }

    /**
     * The trigger only fires for our own side, matching {@code HP_LOST}'s broadcast policy.
     *
     * <p>An enemy being hit is not our content (P9 owns monsters), and — more importantly for the
     * rule's arithmetic — a relic rule must not accumulate stacks when the wearer is the one
     * attacking. {@code fireTriggersForAlly} is the camp check, and this test is what keeps it in
     * place.
     */
    @Test
    public void anEnemyBeingHitDoesNotFireOurRules() {
        Character hero = character(watchTable());
        Character teammate = plain(HIMEKO);
        Battle battle = battle(hero, teammate);
        Enemy enemy = battle.enemyUnits().getFirst();

        battle.applyDamage(enemy, damage(hero, enemy, 5_000));

        Assertions.assertEquals(0, buffsOn(hero, AttributeType.ATTACK).size(),
                "the wearer attacked; it was not hit, so no stack may arrive");
        Assertions.assertEquals(0, buffsOn(hero, AttributeType.DEFENCE).size());
    }

    /**
     * The event is emitted once per damage instance, not once per hit-target within a multi-hit skill.
     *
     * <p>Pinned because "one application per attack" is what the stack cap arithmetic assumes: a
     * 3-hit skill that fired the event three times would reach a 5-stack cap in two casts.
     */
    @Test
    public void takingHitFiresOncePerDamageInstance() {
        Character hero = survivor(HIMEKO, watchTable(TriggerSpecs.rule("TAKING_HIT", List.of(),
                TriggerSpecs.modifyAttr("ATTACK", 0.01, null, true, 99, null, null))));
        Character attacker = plain(HIMEKO);
        Battle battle = battle(attacker, hero);

        battle.applyDamage(hero, damage(attacker, hero, 100));
        battle.applyDamage(hero, damage(attacker, hero, 100));

        Assertions.assertEquals(2, buffsOn(hero, AttributeType.ATTACK).size(),
                "two settled instances, two applications");
    }

    // ==================================================================
    // 3. The hp_percent condition
    // ==================================================================

    /** {@code hp_percent <= 0.5} holds at half HP (the game says "equal to or less than"). */
    @Test
    public void hpPercentMatchesAtAndBelowTheThreshold() {
        Character hero = character(heroTable(TriggerSpecs.rule("ALLY_ATTACK",
                List.of("hp_percent <= 0.5"), TriggerSpecs.modifyAttr("ATTACK", 0.5, 2))));
        Battle battle = battle(hero, plain(HIMEKO));

        setHpFraction(hero, 0.6);
        fireAllyAttack(battle, hero);
        Assertions.assertEquals(0, buffsOn(hero, AttributeType.ATTACK).size(),
                "60% HP is above the threshold");

        setHpFraction(hero, 0.5);
        fireAllyAttack(battle, hero);
        Assertions.assertEquals(1, buffsOn(hero, AttributeType.ATTACK).size(),
                "'equal to or less than 50%' must include exactly 50%");
    }

    /** The literal may sit on either side, like every other numeric condition. */
    @Test
    public void hpPercentAcceptsTheLiteralOnEitherSide() {
        Character hero = character(heroTable(TriggerSpecs.rule("ALLY_ATTACK",
                List.of("0.5 >= hp_percent"), TriggerSpecs.modifyAttr("ATTACK", 0.5, 2))));
        Battle battle = battle(hero, plain(HIMEKO));

        setHpFraction(hero, 0.25);
        fireAllyAttack(battle, hero);

        Assertions.assertEquals(1, buffsOn(hero, AttributeType.ATTACK).size(),
                "'0.5 >= hp_percent' is the same condition written the other way round");
    }

    /** A character at full HP does not satisfy it (the boundary is below, not "always"). */
    @Test
    public void hpPercentIsFalseAtFullHealth() {
        Character hero = character(heroTable(TriggerSpecs.rule("ALLY_ATTACK",
                List.of("hp_percent <= 0.5"), TriggerSpecs.modifyAttr("ATTACK", 0.5, 2))));
        Battle battle = battle(hero, plain(HIMEKO));

        fireAllyAttack(battle, hero);

        Assertions.assertEquals(0, buffsOn(hero, AttributeType.ATTACK).size(),
                "a character at 100% HP must not satisfy 'HP <= 50%'");
    }

    /**
     * A typo is still rejected at load time, and the message lists <b>every</b> known variable.
     *
     * <p>The closed-set property is an explicit, tested one; extending the set must extend the message
     * with it, or the next author learns about {@code hp_percent} only by reading the parser.
     */
    @Test
    public void unknownNumericVariablesAreStillRejectedAndTheListIsComplete() {
        IllegalArgumentException e = Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(0, List.of(TriggerSpecs.rule("ALLY_ATTACK",
                        List.of("hp_percentage <= 50"), TriggerSpecs.modifyAttr("ATTACK", 0.5, 2)))));
        Assertions.assertTrue(e.getMessage().contains("hp_percentage"), e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("hit_count"),
                "the message must list all known numeric variables: " + e.getMessage());
        Assertions.assertTrue(e.getMessage().contains("hp_percent"),
                "…including the new one: " + e.getMessage());
    }

    /** {@code 50} without a variable is still not a condition. */
    @Test
    public void aBareLiteralIsNotACondition() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new TriggerTable(0, List.of(TriggerSpecs.rule("ALLY_ATTACK",
                        List.of("50"), TriggerSpecs.modifyAttr("ATTACK", 0.5, 2)))));
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * A rule table watching both hit events, so one battle can show which of them fired.
     *
     * <p>The two effects land on different attributes ({@code ATTACK} for TAKING_HIT,
     * {@code DEFENCE} for HP_LOST) precisely so that "did this event fire?" is observable without
     * inspecting internals.
     */
    private static TriggerTable watchTable(TriggerSpec... extra) {
        TriggerSpec takingHit = TriggerSpecs.rule("TAKING_HIT", List.of(),
                TriggerSpecs.modifyAttr("ATTACK", 0.5, 2));
        TriggerSpec hpLost = TriggerSpecs.rule("HP_LOST", List.of(),
                TriggerSpecs.modifyAttr("DEFENCE", 0.5, 2));
        List<TriggerSpec> specs = new ArrayList<>(List.of(takingHit, hpLost));
        specs.addAll(List.of(extra));
        return new TriggerTable(0, specs);
    }

    /** A table of turn-start rules. */
    private static TriggerTable heroTable(TriggerSpec... specs) {
        return new TriggerTable(0, List.of(specs));
    }

    /** A character with an explicit trigger table. */
    private static Character character(TriggerTable table) {
        Character character = plain(HIMEKO);
        character.setTriggerTable(table);
        return character;
    }

    /**
     * A character with an explicit trigger table and enough HP to survive the test's hit.
     *
     * <p>The damage entry point settles through the full zone system, so a "raw" 5000 is far less than
     * 5000 against a level-80 character's HP pool, and a killed target must not be used to observe an
     * on-hit event (a corpse is no longer hit). Raising the pool removes that trap from the test rather
     * than tuning the damage until it happens to fit.
     */
    private static Character survivor(int cid, TriggerTable table) {
        Character character = plain(cid);
        character.setTriggerTable(table);
        character.setAttribute(AttributeType.HEALTH, new DoubleValue(1_000_000));
        character.heal(1_000_000);
        return character;
    }

    /** A real character (level 80, no relics), so the HP ratio maths is the game's own. */
    private static Character plain(int cid) {
        return CharacterFactory.create(cid, 80);
    }

    private static Battle battle(Character... team) {
        Enemy enemy = EnemyFactory.create(1002011, 90, 1);
        enemy.setAttribute(AttributeType.HEALTH, new DoubleValue(ENEMY_HP));
        enemy.heal(ENEMY_HP);
        Battle battle = new Battle(List.of(team), List.of(enemy), new Random(0));
        battle.startBattle();
        return battle;
    }

    /** Steps the action bar until {@code unit}'s turn is the current one, then starts it. */
    private static void reachTurnOf(Battle battle, CanHit unit) {
        for (int step = 0; step < 30; step++) {
            battle.stepForward();
            if (battle.currentMove == null) {
                break;
            }
            if (battle.currentMove.getCanHit() == unit) {
                battle.beforeMove();
                return;
            }
            battle.afterMove();
        }
        Assertions.fail("did not reach " + unit.getName() + "'s turn within 30 steps");
    }

    private static void fireAllyAttack(Battle battle, CanHit actor) {
        battle.fireTriggers(TriggerEvent.ALLY_ATTACK, actor, null, 1, 0);
    }

    private static Damage damage(CanHit attacker, CanHit target, double amount) {
        return new Damage(attacker, target, DamageElement.PHYSICAL, amount);
    }

    /**
     * Drives an ally's HP down to a fraction of its maximum.
     *
     * <p>Goes through {@link CanHit#takeDamage(double)} rather than poking the HP field, because the
     * field is private to {@code CanHit} and has no setter — the engine's only way to change HP is the
     * damage entry point, which is also what a real battle uses.
     */
    private static void setHpFraction(Character character, double fraction) {
        double target = character.getMaxHp() * fraction;
        character.takeDamage(character.getCurrentHp() - target);
    }

    /**
     * The buff-sourced modifiers on one attribute, in application order.
     *
     * <p>Returns the list rather than a count on purpose: every call site then says which it wants
     * ({@code buffsOn(...).size()} vs {@code buffsOn(...).getFirst()}), which is what stops the
     * {@code assertEquals(0, list)} mistake that would make a size assertion vacuous.
     */
    private static List<DoubleValue.Modifier> buffsOn(Character character, AttributeType type) {
        return character.getAttribute(type).filterBySource(DoubleValue.Modifier.ModifierSource.BUFF);
    }
}
