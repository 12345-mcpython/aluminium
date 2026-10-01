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
 * 1506 \u94f6\u72fcLV.999 (2026-09-30): the two clauses its own text states completely -- \u300c\u884c\u52a8\u63d0\u524d 100%\u300d and the declared
 * two-tier \u3010\u9690\u85cf\u5206\u3011 (\u300c\u8fbe\u5230 60 \u70b9\u540e\u53ef\u6fc0\u6d3b\u7ec8\u7ed3\u6280\uff0c\u8fbe\u5230\u4e0a\u9650\u540e\u8fd8\u53ef\u6ea2\u51fa 240 \u70b9\u300d).
 *
 * <p>\u2b50 The two tiers are read on the resource itself: {@code gain} may run into the declared overflow, {@code gainClamped} stops at the
 * normal cap. \u26a0 Until this round a declaration could only state ONE number, so the sentence above had no spelling even though the
 * engine underneath already had both tiers.
 */
public class Character1506Test {
    private static final int WEARER = 1506;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String HIDDEN = "\u9690\u85cf\u5206";

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

    /** \u2605 The shipped declarations, read off the compiled character (discipline 232). */
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
        Assertions.assertEquals(1, rules.size(), "the ultimate\u2019s advance is one rule");
        var effect = rules.getFirst().effects().getFirst();
        Assertions.assertEquals(1.0, effect.getPercent(), 1e-9, "\u884c\u52a8\u63d0\u524d 100%");
    }
}
