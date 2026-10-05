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
 * 1506 Silver Wolf LV.999 (银狼LV.999) (2026-09-30): the two clauses its own text states completely -- "行动提前 100%" and the declared
 * two-tier [隐藏分] ("达到 60 点后可激活终结技，达到上限后还可溢出 240 点").
 *
 * <p>The two tiers are read on the resource itself: {@code gain} may run into the declared overflow, {@code gainClamped} stops at the
 * normal cap. Note: Until this round a declaration could only state ONE number, so the sentence above had no spelling even though the
 * engine underneath already had both tiers.
 */
public class Character1506Test {
    private static final int WEARER = 1506;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String HIDDEN = "隐藏分";

    private Character wolf;
    private Battle battle;

    private void build() {
        wolf = CharacterFactory.create(WEARER, LEVEL);
        battle = new Battle(List.of(wolf, CharacterFactory.create(ALLY, LEVEL)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
    }

    @Test
    public void theDeclaredOverflowIsReal() {
        build();
        int overflowGain = wolf.getResources().gain(HIDDEN, 100);
        int afterOverflow = wolf.getResources().value(HIDDEN);
        int clampedGain = wolf.getResources().get(HIDDEN).gainClamped(100);
        int afterClamped = wolf.getResources().value(HIDDEN);
        System.out.println("[1506] a plain gain of 100 took the resource to " + afterOverflow + " (it added "
                + overflowGain + ") ; a clamped gain of 100 added " + clampedGain + " and left it at "
                + afterClamped + " ; the panel reads " + wolf.getResources().get(HIDDEN));
        Assertions.assertEquals(100, afterOverflow, "the normal cap of 60 does not stop a plain gain");
        Assertions.assertEquals(60, afterClamped, "but a clamped gain stops exactly at it");
    }

    /** The shipped declarations, read off the compiled character (discipline 232). */
    @Test
    public void theShippedDeclarationsCarryTheirNumbers() {
        build();
        var spec = wolf.getTriggerTable().resources().stream()
                .filter(resource -> resource.id().equals(HIDDEN)).findFirst().orElseThrow();
        System.out.println("[1506] declared " + HIDDEN + ": max=" + spec.max() + " overflow=" + spec.overflow());
        Assertions.assertEquals(60, spec.max(), "the activation threshold is the normal cap");
        Assertions.assertEquals(240, spec.overflow(), "and the text allows 240 more");
        var rules = wolf.getTriggerTable().rulesFor(TriggerEvent.CAST_SETUP).stream()
                .filter(rule -> rule.id().startsWith("p1506_")).toList();
        Assertions.assertEquals(1, rules.size(), "the ultimate’s advance is one rule");
        var effect = rules.getFirst().effects().getFirst();
        Assertions.assertEquals(1.0, effect.getPercent(), 1e-9, "行动提前 100%");
    }
}
