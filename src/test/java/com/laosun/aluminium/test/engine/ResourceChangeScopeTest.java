package com.laosun.aluminium.test.engine;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.beans.TriggerSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.trigger.TriggerTable;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.test.support.TriggerSpecs;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code resource_changed:} is scoped to the firing that raised it - a nested resource move cannot silence the rest of
 * the event, and the name does not linger past it.
 *
 * <p><b>The defect this pins, both ways round.</b> {@code fireResourceChanged} set the battle's
 * {@code lastChangedResource} and delivered the event. A rule delivered for that event may itself move ANOTHER
 * resource - 1506's forwarding clause grants 隐藏分 for a 笑点 move, which is ordinary content - and that nested move
 * overwrote the fact:
 * <ul>
 *   <li><b>under-firing:</b> the rules that came after it in the same firing read the wrong name, so their
 *       {@code resource_changed:<the outer one>} condition was false and they silently did not fire. It cost this
 *       session a judge fixture (the first version of {@code ResourceChangeHolderTest} put three rules on one event and
 *       the second and third never fired) and forced 1506's milestone ledger to accept a "this rule has to stay last"
 *       constraint;</li>
 *   <li><b>over-firing:</b> the name also stayed on the battle after the event, so an unrelated later firing could match
 *       a resource that did not move at all then.</li>
 * </ul>
 *
 * <p><b>What it is now.</b> Save and restore around the delivery, in a {@code finally} - the shape
 * {@code Battle.fireStateEnded} already uses for {@code lastStateEndedName}.
 *
 * <p>⚠ Reader count first: a scan of every shipped file reports 3 tables with a {@code resource_changed:} watcher and
 * 0 clobber risks, so this changes no shipped number - which the full suite confirms.
 */
public class ResourceChangeScopeTest {
    private static final String OUTER = "测试外层";
    private static final String INNER = "测试内层";
    private static final String INNER_SEEN = "内层看到了";
    private static final String OUTER_SEEN = "外层看到了";
    private static final String LATE = "后来那次看到了";

    /**
     * The outer event's later rules still fire, and the nested event's own rules fire too.
     */
    @Test
    public void aNestedMoveDoesNotSilenceTheRestOfTheEvent() {
        Character receiver = receiver();
        List<TriggerSpec> rules = new ArrayList<>();
        // 1: the outer move's own rule grants a DIFFERENT resource, which raises a nested event.
        rules.add(TriggerSpecs.rule(TriggerEvent.RESOURCE_CHANGED.name(), List.of("resource_changed:" + OUTER),
                gain(INNER, null)));
        // 2: the nested resource's watcher - delivered inside rule 1's own application.
        rules.add(TriggerSpecs.rule(TriggerEvent.RESOURCE_CHANGED.name(), List.of("resource_changed:" + INNER),
                oneInto(INNER_SEEN)));
        // 3: the OUTER resource's watcher, after the mover - the one the defect silenced.
        rules.add(TriggerSpecs.rule(TriggerEvent.RESOURCE_CHANGED.name(), List.of("resource_changed:" + OUTER),
                oneInto(OUTER_SEEN)));
        receiver.setTriggerTable(new TriggerTable(1, rules));

        Battle battle = battleWith(receiver, grantOuterAtTurnStart());
        battle.fireTriggers(TriggerEvent.TURN_START, battle.characters().get(1), battle.characters().get(1), 0, 0);
        battle.processRequests();

        System.out.println("[scope] outer " + value(receiver, OUTER) + " -> inner " + value(receiver, INNER)
                + " ; nested watcher " + value(receiver, INNER_SEEN) + " ; outer watcher "
                + value(receiver, OUTER_SEEN));
        Assertions.assertEquals(3, value(receiver, OUTER), "precondition: the outer counter moved");
        Assertions.assertEquals(3, value(receiver, INNER), "the mover booked the outer event's own amount");
        Assertions.assertEquals(1, value(receiver, INNER_SEEN),
                "the nested event's watcher fires: the nested resource is the one being announced");
        Assertions.assertEquals(1, value(receiver, OUTER_SEEN),
                "and the outer event's later watcher still fires: the name was handed back");
    }

    /**
     * The other half: the name does not survive the firing, so an unrelated later event cannot match a resource that
     * did not move then - neither the nested one nor the outer one.
     */
    @Test
    public void theNameDoesNotSurviveTheFiringThatRaisedIt() {
        Character afterNested = staleWatcher(INNER);
        Character afterOuter = staleWatcher(OUTER);
        System.out.println("[scope] an unrelated later event: watching the nested name = "
                + value(afterNested, LATE) + " ; watching the outer one = " + value(afterOuter, LATE));
        Assertions.assertEquals(0, value(afterNested, LATE),
                "the nested name is scoped to its own firing, not left on the battle");
        Assertions.assertEquals(0, value(afterOuter, LATE), "and so is the outer one");
    }

    /**
     * Drives one outer move, then an unrelated event whose only rule watches {@code watched}.
     *
     * <p>⚠ One observation per battle, on purpose: the outer move's own rule moves another resource, and that rewrites
     * the name for the rules after it in the SAME firing - so two stale watchers on one event would mask each other.
     */
    private static Character staleWatcher(String watched) {
        Character receiver = receiver();
        receiver.setTriggerTable(new TriggerTable(1, List.of(
                TriggerSpecs.rule(TriggerEvent.RESOURCE_CHANGED.name(), List.of("resource_changed:" + OUTER),
                        gain(INNER, null)),
                TriggerSpecs.rule(TriggerEvent.DAMAGE_SETTLED.name(), List.of("resource_changed:" + watched),
                        oneInto(LATE)))));
        Battle battle = battleWith(receiver, grantOuterAtTurnStart());
        Character giver = battle.characters().get(1);
        battle.fireTriggers(TriggerEvent.TURN_START, giver, giver, 0, 0);
        battle.processRequests();
        Assertions.assertEquals(3, value(receiver, INNER), "precondition: the nested move happened");
        battle.fireTriggers(TriggerEvent.DAMAGE_SETTLED, giver, giver, 0, 0);
        battle.processRequests();
        return receiver;
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    private static Character receiver() {
        Character unit = Character.fromAttributes("receiver", 1000, 100, 100, 100);
        for (String resource : List.of(OUTER, INNER, INNER_SEEN, OUTER_SEEN, LATE)) {
            unit.getResources().register(resource, 1000, 0);
        }
        return unit;
    }

    /**
     * The giver moves the party's FIRST member's counter - the receiver, so the readings are about its store.
     */
    private static TriggerSpec grantOuterAtTurnStart() {
        return TriggerSpecs.rule(TriggerEvent.TURN_START.name(), List.of(), gainFor(OUTER, 3.0, "party_first"));
    }

    private static EffectSpec gainFor(String resource, Double amount, String target) {
        EffectSpec effect = gain(resource, amount);
        TriggerSpecs.set(effect, "target", target);
        return effect;
    }

    private static Battle battleWith(Character receiver, TriggerSpec giverRule) {
        Character giver = Character.fromAttributes("giver", 1000, 100, 100, 100);
        giver.setTriggerTable(new TriggerTable(1, List.of(giverRule)));
        Battle battle = new Battle(List.of(receiver, giver), List.of(BattleFixture.monster()),
                BattleFixture.seeded());
        battle.startBattle();
        battle.processRequests();
        return battle;
    }

    /**
     * Books the event's own magnitude when {@code amount} is null, or a flat amount otherwise.
     */
    private static EffectSpec gain(String resource, Double amount) {
        EffectSpec effect = new EffectSpec();
        TriggerSpecs.set(effect, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(effect, "resource", resource);
        TriggerSpecs.set(effect, "target", "self");
        if (amount == null) {
            TriggerSpecs.set(effect, "amountFromEvent", Boolean.TRUE);
        } else {
            TriggerSpecs.set(effect, "amount", amount);
        }
        return effect;
    }

    private static EffectSpec oneInto(String resource) {
        return gain(resource, 1.0);
    }

    private static int value(Character unit, String resource) {
        return unit.getResources().has(resource) ? unit.getResources().value(resource) : 0;
    }
}
