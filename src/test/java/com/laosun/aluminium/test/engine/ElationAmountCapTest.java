package com.laosun.aluminium.test.engine;


import com.laosun.aluminium.test.content.characters.EvanesciaEnergySyncTest;
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
 * "单次通过此方式计算的[好活当赏]不超过 100 点": a single conversion of 150 energy mirrors only
 * 100, while 60 mirrors all 60.
 *
 * <p>The energy is delivered as the EVENT's own magnitude through `fireTriggers`, which is exactly the quantity the mirror rule reads --
 * the real crediting path was already exercised by EvanesciaEnergySyncTest, and this test is about the ceiling.
 */
public class ElationAmountCapTest {
    private static final int WEARER = 1505;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String GIFTS = "好活当赏";

    private int mirrored(double energy) {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        Battle battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        int before = elation.getResources().value(GIFTS);
        battle.fireTriggers(TriggerEvent.ENERGY_GAINED, elation, elation, 1, energy);
        return elation.getResources().value(GIFTS) - before;
    }

    @Test
    public void oneConversionIsCappedAtAHundred() {
        int big = mirrored(150);
        int small = mirrored(60);
        System.out.println("[cap] a 150-energy conversion mirrored " + big + " and a 60-energy one mirrored " + small);
        Assertions.assertEquals(100, big, "单次不超过 100 点");
        Assertions.assertEquals(60, small, "a conversion below the ceiling is untouched (false case)");
    }

    /** The shipped ceiling, read off the compiled rule (discipline 232). */
    @Test
    public void theShippedRuleCarriesTheCeiling() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        var rules = elation.getTriggerTable().rulesFor(TriggerEvent.ENERGY_GAINED).stream()
                .filter(rule -> rule.id().endsWith("energy_sync")).toList();
        Assertions.assertEquals(1, rules.size(), "the mirror rule");
        var effect = rules.getFirst().effects().getFirst();
        System.out.println("[cap] spec " + rules.getFirst().id() + " amountCap=" + effect.getAmountCap()
                + " amountFromEvent=" + effect.getAmountFromEvent());
        Assertions.assertEquals(100.0, effect.getAmountCap(), 1e-9, "the ceiling her text states");
        Assertions.assertEquals(Boolean.TRUE, effect.getAmountFromEvent(), "on the mirrored magnitude");
    }
}
