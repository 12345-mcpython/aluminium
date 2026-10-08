package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.TriggerSpecs;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * {@code RESOURCE_CHANGED} is about the store that MOVED - the holder, not the rule's owner.
 *
 * <p><b>The bug this pins.</b> The three moving arms of {@code applyOne} announced a delta measured on
 * {@code ctx.owner()}, while the ops credit the RESOLVED target. For an effect that moves somebody else's counter those
 * are two different units, and the announcement was about a store that did not move: measured, a teammate granted her
 * 280 hidden points, her store went 20 to 300, and the event announced 0.
 *
 * <p><b>Why it could be fixed at all.</b> The reader count came first, as always: the only counters any
 * {@code resource_changed:} rule watches are 天赋充能 (1404), 热意 (1513), 笑点 (1506) and 隐藏分 (1506), and every shipped
 * move of all four states {@code target: self} or leaves it unstated - owner and holder are the same unit in all shipped
 * content, so this changes no number anywhere. What it changes is that a cross-unit grant becomes readable at all.
 *
 * <p>⚠ <b>One observation per firing, on purpose.</b> Each case below gives the receiver's table a SINGLE rule: a
 * resource-moving effect overwrites the battle's {@code lastChangedResource} for the rules that come after it in the
 * same firing, so three rules on one event would measure each other's counter (found while writing this case).
 */
public class ResourceChangeHolderTest {
    private static final int MONSTER = 1002011;
    private static final String COUNTER = "测试计数";
    private static final String RECORDED = "测试记录";
    private static final String FROM_SELF = "测试主体是自己";
    private static final String FROM_OTHER = "测试主体是他人";

    /** The announced delta is the GRANTEE's, and the event names the grantee. */
    @Test
    public void theAnnouncementIsAboutTheStoreThatMoved() {
        Character recorded = granted(record());
        System.out.println("[holder] granted 7 to the first party member: counter " + value(recorded, COUNTER)
                + ", announced " + value(recorded, RECORDED));
        Assertions.assertEquals(7, value(recorded, COUNTER), "precondition: the grant landed on the grantee");
        Assertions.assertEquals(7, value(recorded, RECORDED),
                "the event announces the GRANTEE's credited delta, not the rule owner's reading of its own store (0)");

        Character asSelf = granted(oneInto(FROM_SELF), "actor == self");
        Character asOther = granted(oneInto(FROM_OTHER), "actor is_other_ally");
        System.out.println("[holder] subject was self: " + value(asSelf, FROM_SELF) + " ; subject was other ally: "
                + value(asOther, FROM_OTHER));
        Assertions.assertEquals(1, value(asSelf, FROM_SELF), "its subject is the unit whose store moved");
        Assertions.assertEquals(0, value(asOther, FROM_OTHER),
                "not the unit that ran the effect - that is the reading this case replaces");
    }

    /** A move the owner makes on its own counter is announced exactly as before (this is the shipped shape). */
    @Test
    public void aSelfMoveIsUnchanged() {
        Character unit = Character.fromAttributes("unit", 1000, 100, 100, 100);
        declare(unit);
        unit.setTriggerTable(new TriggerTable(1, List.of(
                TriggerSpecs.rule(TriggerEvent.TURN_START.name(), List.of(), gain(COUNTER, 5, null)),
                TriggerSpecs.rule(TriggerEvent.RESOURCE_CHANGED.name(), List.of("resource_changed:" + COUNTER),
                        record()))));

        Battle battle = new Battle(List.of(unit, Character.fromAttributes("other", 1000, 100, 100, 100)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        battle.processRequests();
        battle.fireTriggers(TriggerEvent.TURN_START, unit, unit, 0, 0);
        battle.processRequests();

        System.out.println("[holder] a self-move of 5: counter " + value(unit, COUNTER) + ", announced "
                + value(unit, RECORDED));
        Assertions.assertEquals(5, value(unit, COUNTER));
        Assertions.assertEquals(5, value(unit, RECORDED), "owner and holder are the same unit, so nothing moved");
    }

    /**
     * The other two arms: a spend and a removal that reach somebody else's counter name that store as well.
     *
     * <p>⚠ Observed through the event's SUBJECT rather than its delta: an announcement of a spend is negative, and
     * {@code amount_from_event} books a gain (a negative one credits nothing), so the number is not readable through
     * that op. The subject is decided by the same line, so the reading is about the same fix.
     */
    @Test
    public void aSpendAndARemovalAlsoNameTheStoreThatPaid() {
        Character spent = moved("SPEND_RESOURCE", 3, oneInto(FROM_SELF), "actor == self");
        Character removed = moved("REMOVE_RESOURCE", 2, oneInto(FROM_SELF), "actor == self");
        System.out.println("[holder] a cross-unit spend: counter " + value(spent, COUNTER) + ", subject-was-self "
                + value(spent, FROM_SELF) + " ; a cross-unit removal: counter " + value(removed, COUNTER)
                + ", subject-was-self " + value(removed, FROM_SELF));
        Assertions.assertEquals(4, value(spent, COUNTER), "precondition: 7 - 3 was taken from HER store");
        Assertions.assertEquals(1, value(spent, FROM_SELF), "and the event names the store that paid");
        Assertions.assertEquals(5, value(removed, COUNTER), "precondition: 7 - 2 was taken back off her store");
        Assertions.assertEquals(1, value(removed, FROM_SELF), "and the event names that store too");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /**
     * Runs one grant of 7 from a second unit to the party's FIRST member (the receiver), with the receiver's table
     * holding the rules handed in, and answers with the receiver.
     */
    private static Character granted(EffectSpec receiverEffect, String... extraConditions) {
        Character receiver = Character.fromAttributes("receiver", 1000, 100, 100, 100);
        declare(receiver);
        java.util.List<String> conditions = new java.util.ArrayList<>();
        conditions.add("resource_changed:" + COUNTER);
        conditions.addAll(List.of(extraConditions));
        receiver.setTriggerTable(new TriggerTable(1, List.of(
                TriggerSpecs.rule(TriggerEvent.RESOURCE_CHANGED.name(), conditions, receiverEffect))));

        Character giver = Character.fromAttributes("giver", 1000, 100, 100, 100);
        giver.setTriggerTable(new TriggerTable(1, List.of(TriggerSpecs.rule(TriggerEvent.TURN_START.name(),
                List.of(), gain(COUNTER, 7, "party_first")))));

        Battle battle = new Battle(List.of(receiver, giver), List.of(EnemyFactory.create(MONSTER, 90, 1)),
                new Random(0));
        battle.startBattle();
        battle.processRequests();
        battle.fireTriggers(TriggerEvent.TURN_START, giver, giver, 0, 0);
        battle.processRequests();
        return receiver;
    }

    /**
     * The same fixture, with the giver SPENDING or REMOVING from the party's first member instead of granting.
     *
     * <p>Her store starts at 7 either way, so the readings are about the move and not about how the counter was built.
     */
    private static Character moved(String op, int amount, EffectSpec receiverEffect, String... extraConditions) {
        Character receiver = Character.fromAttributes("receiver", 1000, 100, 100, 100);
        declare(receiver);
        receiver.getResources().gain(COUNTER, 7);
        java.util.List<String> conditions = new java.util.ArrayList<>();
        conditions.add("resource_changed:" + COUNTER);
        conditions.addAll(List.of(extraConditions));
        receiver.setTriggerTable(new TriggerTable(1, List.of(
                TriggerSpecs.rule(TriggerEvent.RESOURCE_CHANGED.name(), conditions, receiverEffect))));

        EffectSpec move = new EffectSpec();
        TriggerSpecs.set(move, "op", op);
        TriggerSpecs.set(move, "resource", COUNTER);
        TriggerSpecs.set(move, "amount", (double) amount);
        TriggerSpecs.set(move, "target", "party_first");
        Character giver = Character.fromAttributes("giver", 1000, 100, 100, 100);
        giver.setTriggerTable(new TriggerTable(1, List.of(
                TriggerSpecs.rule(TriggerEvent.TURN_START.name(), List.of(), move))));

        Battle battle = new Battle(List.of(receiver, giver), List.of(EnemyFactory.create(MONSTER, 90, 1)),
                new Random(0));
        battle.startBattle();
        battle.processRequests();
        battle.fireTriggers(TriggerEvent.TURN_START, giver, giver, 0, 0);
        battle.processRequests();
        return receiver;
    }

    /** The four counters the cases read. */
    private static void declare(Character unit) {
        for (String resource : List.of(COUNTER, RECORDED, FROM_SELF, FROM_OTHER)) {
            if (!unit.getResources().has(resource)) {
                unit.getResources().register(resource, 1000, 0);
            }
        }
    }

    /** Books the event's OWN magnitude, which is the number under test. */
    private static EffectSpec record() {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(effect, "resource", RECORDED);
        TriggerSpecs.set(effect, "amountFromEvent", Boolean.TRUE);
        TriggerSpecs.set(effect, "target", "self");
        return effect;
    }

    private static EffectSpec oneInto(String resource) {
        return gain(resource, 1, "self");
    }

    private static EffectSpec gain(String resource, int amount, String target) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(effect, "resource", resource);
        TriggerSpecs.set(effect, "amount", (double) amount);
        TriggerSpecs.set(effect, "target", target);
        return effect;
    }

    private static int value(Character unit, String resource) {
        return unit.getResources().has(resource) ? unit.getResources().value(resource) : 0;
    }
}
