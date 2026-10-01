package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.SkillType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * Resource-change forwarding (2026-09-30): 1506 \u94f6\u72fcLV.999\u2019s \u300c\u83b7\u5f97\u7b11\u70b9\u65f6\uff0c\u83b7\u5f97\u7b49\u91cf\u3010\u9690\u85cf\u5206\u3011\u300d.
 *
 * <p>\u2b50 The source is 1505\u2019s skill, which grants ten SHARED laughs: the battle\u2019s laugh counter moves, the change carries that
 * resource\u2019s name and its delta, and 1506 turns it into the same number of \u3010\u9690\u85cf\u5206\u3011. The false case is the same cast with
 * an ally that has no such clause (1002), so the forwarding is what moves the number.
 */
public class Character1506ForwardingTest {
    private static final int SOURCE = 1505;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String LAUGH = "\u7b11\u70b9";
    private static final String HIDDEN = "\u9690\u85cf\u5206";

    private Character source;
    private Character other;
    private Battle battle;

    private void build(int ally) {
        source = CharacterFactory.create(SOURCE, LEVEL);
        other = CharacterFactory.create(ally, LEVEL);
        battle = new Battle(List.of(source, other), List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
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
                + "\u2019s hidden points " + hiddenBefore + " -> " + hiddenAfter);
        Assertions.assertEquals(laughsBefore + 10, laughsAfter, "the skill granted ten shared laughs");
        Assertions.assertEquals(hiddenBefore + 10, hiddenAfter,
                "and every one of them became a hidden point -- the forwarding clause");
    }

    /** \u2605 The false case: the same cast, an ally with no forwarding clause. */
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

    /** \u2605 The shipped rule and its scope, read off the compiled table (discipline 232). */
    @Test
    public void theShippedRuleWatchesTheResourceItForwards() {
        build(1506);
        var rules = other.getTriggerTable().rulesFor(TriggerEvent.RESOURCE_CHANGED).stream()
                .filter(rule -> rule.id().startsWith("p1506_")).toList();
        Assertions.assertEquals(1, rules.size(), "one forwarding rule");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[forward] spec " + rules.getFirst().id() + " when=" + rules.getFirst().conditions()
                + " resource=" + effect.getResource() + " amountFromEvent=" + effect.getAmountFromEvent());
        Assertions.assertEquals(HIDDEN, effect.getResource(), "it grants the hidden points");
        Assertions.assertEquals(Boolean.TRUE, effect.getAmountFromEvent(), "by the event\u2019s own amount");
    }
}
