package com.laosun.aluminium.test.content.characters.evanescia;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.test.support.BattleFixture;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * Resource-change forwarding: 1506 Silver Wolf LV.999 (银狼LV.999)'s "when gaining a laugh, gains an equal amount of [隐藏分]".
 *
 * <p>The source is 1505's skill, which grants ten SHARED laughs: the battle's laugh counter moves, the change carries that
 * resource's name and its delta, and 1506 turns it into the same number of [隐藏分]. The false case is the same cast with
 * an ally that has no such clause (1002), so the forwarding is what moves the number.
 */
public class SilverWolfLV999ForwardingTest {
    private static final int SOURCE = 1505;
    private static final int LEVEL = 80;
    private static final String LAUGH = "笑点";
    private static final String HIDDEN = "隐藏分";

    private Character source;
    private Character other;
    private Battle battle;

    private void build(int ally) {
        source = CharacterFactory.create(SOURCE, LEVEL);
        other = CharacterFactory.create(ally, LEVEL);
        battle = new Battle(List.of(source, other), List.of(BattleFixture.monster()), BattleFixture.seeded());
        battle.startBattle();
    }

    @Test
    public void aSharedLaughBecomesHiddenPointsOneForOne() {
        build(1506);
        int laughsBefore = battle.partyResourceValue(LAUGH);
        int hiddenBefore = other.getResources().value(HIDDEN);
        battle.castImmediate(source.getSkills().get(SkillType.SKILL), source, List.of());
        int laughsAfter = battle.partyResourceValue(LAUGH);
        int hiddenAfter = other.getResources().value(HIDDEN);
        System.out.println("[forward] laughs " + laughsBefore + " -> " + laughsAfter + " ; " + 1506
                + "’s hidden points " + hiddenBefore + " -> " + hiddenAfter);
        Assertions.assertEquals(laughsBefore + 10, laughsAfter, "the skill granted ten shared laughs");
        Assertions.assertEquals(hiddenBefore + 10, hiddenAfter,
                "and every one of them became a hidden point -- the forwarding clause");
    }

    /**
     * The false case: the same cast, an ally with no forwarding clause.
     */
    @Test
    public void anAllyWithoutTheClauseGainsNothing() {
        build(1002);
        int laughsBefore = battle.partyResourceValue(LAUGH);
        int hiddenBefore = other.getResources().value(HIDDEN);
        battle.castImmediate(source.getSkills().get(SkillType.SKILL), source, List.of());
        System.out.println("[forward] with an ally that has no clause: laughs " + laughsBefore + " -> "
                + battle.partyResourceValue(LAUGH) + " ; its hidden points " + hiddenBefore + " -> "
                + other.getResources().value(HIDDEN));
        Assertions.assertEquals(10, battle.partyResourceValue(LAUGH) - laughsBefore, "the laughs still happen");
        Assertions.assertFalse(other.getResources().has(HIDDEN), "and nothing is forwarded to that ally");
    }

    /**
     * The shipped rule and its scope, read off the compiled table (discipline 232).
     */
    @Test
    public void theShippedRuleWatchesTheResourceItForwards() {
        build(1506);
        var rules = other.getTriggerTable().rulesFor(TriggerEvent.RESOURCE_CHANGED).stream()
                .filter(rule -> rule.id().startsWith("p1506_")).toList();
        // ⚠ Her table has THREE rules on this event, and each one belongs there: the forwarding clause, her talent,
        // which recomputes the CRIT conversion whenever 【隐藏分】 moves, and her Eidolon 2's milestone ledger, which
        // counts what the hidden score did while 【无敌玩家】 is up. Pinning the exact set keeps a fourth from slipping in.
        Assertions.assertEquals(
                java.util.Set.of("p1506_laughs_to_hidden", "p1506_talent_hidden_becomes_crit",
                        "p1506_e2_milestones_while_in_godmode"),
                rules.stream().map(rule -> rule.id()).collect(java.util.stream.Collectors.toSet()),
                "the forwarding rule, the talent conversion and the Eidolon 2 ledger, and nothing else");
        // ⚠ The ORDER is pinned as a snapshot of this event's table, so a new rule arrives here first. Note: it is NOT
        // a constraint any more - the ledger's first effect moves 里程碑配额, and that used to overwrite the battle's
        // `lastChangedResource` for the rules after it (the ledger therefore had to be last). That is fixed:
        // `TriggerInterpreter.fireResourceChanged` scopes the name to its own firing, pinned by ResourceChangeScopeTest.
        Assertions.assertEquals("p1506_e2_milestones_while_in_godmode",
                rules.getLast().id(), "the census reads this event's order; it is the last of the three today");
        var forwarding = rules.stream().filter(rule -> rule.id().equals("p1506_laughs_to_hidden")).findFirst()
                .orElseThrow();
        var effect = forwarding.effects().getFirst();
        System.out.println("[forward] spec " + forwarding.id() + " when=" + forwarding.conditions()
                + " resource=" + effect.getResource() + " amountFromEvent=" + effect.getAmountFromEvent());
        Assertions.assertEquals(HIDDEN, effect.getResource(), "it grants the hidden points");
        Assertions.assertEquals(Boolean.TRUE, effect.getAmountFromEvent(), "by the event’s own amount");
    }
}
