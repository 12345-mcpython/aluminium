package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1505 \u7eef\u82f1\u2019s energy accumulator (2026-09-30): \u300c\u7d2f\u8ba1\u83b7\u5f97 240 \u70b9\u80fd\u91cf\u65f6\u2026\u5355\u6b21\u83b7\u5f97\u80fd\u91cf\u65f6\u6700\u591a\u83b7\u5f97 240 \u70b9\u7d2f\u8ba1\u503c\u300d.
 *
 * <p>\u2b50 Two-sided on the same resource: a 300-energy gain adds only 240 (the per-conversion ceiling), a 100-energy gain adds all 100,
 * and two gains in a row accumulate -- which is what \u300c\u7d2f\u8ba1\u300d means.
 */
public class ElationAccumulatorTest {
    private static final int WEARER = 1505;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String ACC = "\u7d2f\u8ba1\u80fd\u91cf";

    private Character elation;
    private Battle battle;

    private void build() {
        elation = CharacterFactory.create(WEARER, LEVEL);
        battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
    }

    private int gain(double energy) {
        int before = elation.getResources().value(ACC);
        battle.fireTriggers(TriggerEvent.ENERGY_GAINED, elation, elation, 1, energy);
        return elation.getResources().value(ACC) - before;
    }

    @Test
    public void oneConversionIsCappedAtTwoFortyAndGainsAccumulate() {
        build();
        int big = gain(300);
        int small = gain(100);
        int first = elation.getResources().value(ACC);
        System.out.println("[acc] a 300-energy gain added " + big + " ; a 100 one added " + small
                + " ; the counter now reads " + first + " (240 + 100)");
        Assertions.assertEquals(240, big, "\u5355\u6b21\u83b7\u5f97\u80fd\u91cf\u65f6\u6700\u591a\u83b7\u5f97 240 \u70b9\u7d2f\u8ba1\u503c");
        Assertions.assertEquals(100, small, "below the ceiling a gain is taken whole");
        Assertions.assertEquals(340, first, "and the counter accumulates rather than resetting");
    }

    /** \u2605 The shipped rule, read off the compiled table (discipline 232). */
    @Test
    public void theShippedRuleCarriesTheCeiling() {
        build();
        var rules = elation.getTriggerTable().rulesFor(TriggerEvent.ENERGY_GAINED).stream()
                .filter(rule -> rule.id().endsWith("accumulator")).toList();
        Assertions.assertEquals(1, rules.size(), "the accumulator rule");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[acc] spec " + rules.getFirst().id() + " resource=" + effect.getResource()
                + " amountCap=" + effect.getAmountCap() + " amountFromEvent=" + effect.getAmountFromEvent());
        Assertions.assertEquals(240.0, effect.getAmountCap(), 1e-9, "the per-conversion ceiling her text states");
        Assertions.assertEquals(ACC, effect.getResource(), "into the accumulator");
    }
}
