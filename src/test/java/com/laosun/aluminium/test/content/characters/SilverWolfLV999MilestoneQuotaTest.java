package com.laosun.aluminium.test.content.characters;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.test.support.TriggerSpecs;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1506 Silver Wolf LV.999's Eidolon 2, second half: 「本次【无敌玩家】状态下每使【隐藏分】提高 120 点（含初始隐藏分），
 * 获得 1 个额外回合并恢复 1 次强化普攻的可施放次数」 - the accounting, and the charge it restores.
 *
 * <p><b>What this file pins.</b> The ledger is the shadow of 【隐藏分】 taken when the state is entered (「含初始隐藏分」)
 * and moved by each gain's delta, and every whole 120 points of it pays one milestone: the charge is restored once per
 * milestone and the milestone is consumed once, by {@code SPEND_STEPS}. The three readings that carry it are the
 * arithmetic (250 in, two milestones, 30 left), the multiplicity (a single 280-point gain is TWO milestones, so two
 * charges come back) and the negative control (outside the state, the ledger does not move at all).
 *
 * <p>⚠ <b>The extra turn is NOT part of this file, and that is deliberate.</b> {@code Queue.grantExtraTurn} keeps ONE
 * pending extra turn - a second grant overwrites the first, which {@code ExtraTurnTest} pins - so a {@code per_steps}
 * repeat of it would pay one extra turn where the document counts two. Registered rather than approximated; the rule's
 * own {@code source} says where the effect goes when the queue can hold more than one.
 *
 * <p><b>The fixture moves her hidden score the way the documents do</b>: a second unit grants a lump of SHARED 笑点, and
 * her own talent (「获得笑点时，获得等量【隐藏分】」) forwards it one for one. ⚠ Deliberately NOT a direct
 * {@code GAIN_RESOURCE 隐藏分} pointed at her from another unit: the delta a change ANNOUNCES is measured on the rule
 * owner's own store, so a self-scoped counter granted to somebody else is announced as 0 (measured while writing this
 * case; registered in GAPS). The party-scoped laugh pool has no such blind spot, and the forwarding clause that turns it
 * into hidden points is her own rule - the shipped premise.
 */
public class SilverWolfLV999MilestoneQuotaTest {
    private static final int WOLF = 1506;
    private static final int MONSTER = 1002011;
    private static final String HIDDEN = "隐藏分";
    private static final String LAUGH = "笑点";
    private static final String CHARGES = "强化普攻次数";
    private static final String QUOTA = "里程碑配额";
    private static final String RULE = "p1506_e2_milestones_while_in_godmode";
    private static final int STEP = 120;

    private Character wolf;
    private Character feeder;
    private Enemy enemy;
    private Battle battle;

    /**
     * 「含初始隐藏分」: she enters the state holding 250, the Ultimate's own trace adds 20, and the ledger counts all 270
     * - two milestones paid, 30 left. Without the initial 250 the ledger would read 20 and nothing would have been paid.
     */
    @Test
    public void theInitialHiddenScoreCountsTowardTheMilestones() {
        build(2, 0);
        wolf.getResources().gain(HIDDEN, 250);
        enterGodmode();
        System.out.println("[milestone] entered with an initial 250: hidden " + hidden() + ", ledger " + quota()
                + ", charges " + charges());
        Assertions.assertEquals(270, hidden(), "the Ultimate's own trace granted 20 more");
        Assertions.assertEquals(30, quota(),
                "270 counted as two milestones (2 x 120 paid, 30 left); the initial 250 counted, or nothing would be paid");
    }

    /**
     * ⚠ The case the whole primitive exists for: ONE gain of 280 is two milestones, so two charges come back - a
     * per-event "over 120, pay one" check would restore one.
     */
    @Test
    public void oneGainOfTwoHundredAndEightyRestoresTwoCharges() {
        build(2, 280);
        enterGodmode();
        Assertions.assertEquals(20, hidden(), "precondition: the Ultimate's own trace, and no milestone yet");
        Assertions.assertEquals(20, quota(), "precondition: the ledger holds the 20 with nothing paid");
        wolf.getResources().spend(CHARGES, 2);
        Assertions.assertEquals(1, charges(), "precondition: two charges spent, so the restoration is visible");

        feederLaughs();
        System.out.println("[milestone] one 280-point laugh gain: hidden " + hidden() + ", ledger " + quota()
                + ", charges " + charges());
        Assertions.assertEquals(300, hidden(), "forwarded one for one, up to the counter's own ceiling (60 + 240)");
        Assertions.assertEquals(3, charges(), "1 + 2 milestones: a single 'over 120' check would leave 2");
        Assertions.assertEquals(60, quota(), "300 counted as two milestones, 60 left for the next one");
    }

    /** The negative control: outside 【无敌玩家】 the ledger does not move, whatever her hidden score does. */
    @Test
    public void nothingIsCountedWhileSheIsNotInGodmode() {
        build(2, 280);
        feederLaughs();
        System.out.println("[milestone] the same gain with no 【无敌玩家】: hidden " + hidden() + ", ledger " + quota()
                + ", charges " + charges());
        Assertions.assertEquals(280, hidden(), "the hidden score still moves");
        Assertions.assertEquals(0, quota(), "but the ledger only exists inside the state");
        Assertions.assertEquals(0, charges(), "and nothing was restored");
    }

    /** The ledger is per state: leaving 【无敌玩家】 ends the accounting with it. */
    @Test
    public void theLedgerIsResetWhenTheStateEnds() {
        build(2, 0);
        enterGodmode();
        Assertions.assertEquals(20, quota(), "precondition: the ledger was seeded on entry");
        battle.fireStateEnded(wolf, "无敌玩家");
        battle.processRequests();
        System.out.println("[milestone] after the state ended: hidden " + hidden() + ", ledger " + quota());
        Assertions.assertEquals(0, quota(), "the next state starts from its own initial hidden score");
        Assertions.assertEquals(4, hidden(), "and her Eidolon 1 keeps a fifth of what the talent cleared");
    }

    /**
     * The shipped rule, read off the compiled table: what it spends, and that it stays LAST on her resource event.
     *
     * <p>⚠ The order is load-bearing and is why this case exists: its first effect moves the shadow counter, which
     * overwrites the battle's {@code lastChangedResource} - so any further {@code resource_changed:隐藏分} rule placed
     * after it would never fire.
     */
    @Test
    public void theShippedRuleSpendsTheMilestonesAndStaysLast() {
        Character unit = CharacterFactory.create(WOLF, 80, false, null, null, 2);
        var rules = unit.getTriggerTable().rulesFor(TriggerEvent.RESOURCE_CHANGED).stream()
                .filter(rule -> rule.id().startsWith("p1506_")).toList();
        List<String> ids = rules.stream().map(rule -> rule.id()).toList();
        System.out.println("[milestone] her RESOURCE_CHANGED rules, in order: " + ids);
        Assertions.assertEquals(RULE, ids.getLast(),
                "it overwrites lastChangedResource, so anything after it on 隐藏分 would never fire");
        var rule = rules.getLast();
        var effects = rule.effects();
        Assertions.assertEquals(2, rule.minEidolon(), "the clause is her Eidolon 2");
        Assertions.assertEquals(List.of("self has_state 无敌玩家", "resource_changed:隐藏分"),
                rule.conditions().stream().map(condition -> condition.source()).toList(),
                "the state is the scope, and the hidden score is what moved");
        Assertions.assertEquals(3, effects.size(), "accumulate, restore, spend - in that order");
        Assertions.assertEquals(Boolean.TRUE, effects.get(0).getAmountFromEvent(), "the ledger grows by the event's delta");
        Assertions.assertEquals(QUOTA, effects.get(0).getResource());
        Assertions.assertEquals(CHARGES, effects.get(1).getResource());
        Assertions.assertEquals(QUOTA + ":" + STEP, effects.get(1).getPerSteps(),
                "one charge per milestone, counted - not spent");
        Assertions.assertEquals("SPEND_STEPS", effects.get(2).getOp());
        Assertions.assertEquals(QUOTA + ":" + STEP, effects.get(2).getSteps(),
                "and the milestones are consumed once, after both readers");
    }

    // ==================================================================
    // Helpers
    // ==================================================================

    /** A party whose second unit can move the SHARED laugh pool, which her talent forwards into hidden points. */
    private void build(int eidolon, int laughs) {
        wolf = CharacterFactory.create(WOLF, 80, false, null, null, eidolon);
        feeder = Character.fromAttributes("feeder", 1000, 100, 100, 100);
        EffectSpec grant = new EffectSpec();
        TriggerSpecs.set(grant, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(grant, "resource", LAUGH);
        TriggerSpecs.set(grant, "amount", (double) laughs);
        feeder.setTriggerTable(new TriggerTable(1, List.of(TriggerSpecs.rule("TURN_START", List.of(), grant))));
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wolf, feeder), List.of(enemy), new Random(0));
        battle.startBattle();
        battle.processRequests();
    }

    private void enterGodmode() {
        wolf.setCurrentEnergy(wolf.getMaxEnergy());
        battle.castUltra(wolf, List.of(enemy));
        battle.processRequests();
    }

    /** One party-scoped laugh gain, which 「获得笑点时，获得等量【隐藏分】」 turns into hidden points. */
    private void feederLaughs() {
        battle.fireTriggers(TriggerEvent.TURN_START, feeder, feeder, 0, 0);
        battle.processRequests();
    }

    private int hidden() {
        return wolf.getResources().value(HIDDEN);
    }

    private int charges() {
        return wolf.getResources().value(CHARGES);
    }

    private int quota() {
        return wolf.getResources().value(QUOTA);
    }
}
