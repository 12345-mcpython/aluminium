package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * 1505 \u7eef\u82f1, the two clauses her own text states completely (2026-09-30):
 * \u300c\u7eef\u82f1\u83b7\u5f97\u7b49\u540c\u4e8e\u66b4\u51fb\u4f24\u5bb9 50% \u7684\u6b22\u6109\u5ea6\u300d and the technique\u2019s 20 \u3010\u597d\u6d3b\u5f53\u8d4f\u3011.
 */
public class Character1505Test {
    private static final int WEARER = 1505;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private static EffectSpec derive() {
        EffectSpec spec = new EffectSpec();
        TriggerSpecs.set(spec, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(spec, "resource", "\u6b22\u6109\u5ea6");
        TriggerSpecs.set(spec, "amountFromAttr", "CRIT_ATTACK");
        TriggerSpecs.set(spec, "amountPercent", 0.5);
        return spec;
    }

    private static EffectSpec raiseCritDamage() {
        EffectSpec spec = new EffectSpec();
        TriggerSpecs.set(spec, "op", "MODIFY_ATTR");
        TriggerSpecs.set(spec, "attribute", "CRIT_ATTACK");
        TriggerSpecs.set(spec, "percent", 1.0);
        TriggerSpecs.set(spec, "permanent", true);
        TriggerSpecs.set(spec, "target", "self");
        return spec;
    }

    @Test
    public void theTalentDerivesTheElationValueAndTheTechniqueGivesTwenty() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        Battle battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        double critDamage = elation.getAttribute(AttributeType.CRIT_ATTACK).get();
        battle.startBattle();
        int elationValue = elation.getResources().value("\u6b22\u6109\u5ea6");
        int gifts = elation.getResources().value("\u597d\u6d3b\u5f53\u8d4f");
        System.out.println("[1505] crit damage=" + critDamage + " -> \u6b22\u6109\u5ea6=" + elationValue
                + " (half is " + Math.round(critDamage * 0.5) + ") ; \u597d\u6d3b\u5f53\u8d4f=" + gifts);
        Assertions.assertEquals((int) Math.round(critDamage * 0.5), elationValue,
                "the talent sets the Elation value to half the crit-damage panel");
        Assertions.assertEquals(20, gifts, "and the technique grants 20 gifts at battle start");
    }

    @Test
    public void raisedCritDamagePanel() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        elation.setTriggerTable(new TriggerTable(WEARER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), raiseCritDamage()),
                TriggerSpecs.rule("BATTLE_START", List.of(), derive()))));
        Battle battle = new Battle(List.of(elation, CharacterFactory.create(ALLY, LEVEL)),
                List.of(EnemyFactory.create(MONSTER, 90, 1)), new Random(0));
        battle.startBattle();
        double critDamage = elation.getAttribute(AttributeType.CRIT_ATTACK).get();
        int elationValue = elation.getResources().value("\u6b22\u6109\u5ea6");
        System.out.println("[1505] with a raised panel: crit damage=" + critDamage + " -> \u6b22\u6109\u5ea6="
                + elationValue + " (half is " + Math.round(critDamage * 0.5) + ")");
        Assertions.assertTrue(critDamage >= 1.4, "the panel really moved (it reads " + critDamage + ")");
        Assertions.assertEquals((int) Math.round(critDamage * 0.5), elationValue,
                "the talent still tracks the panel, and now the half is not zero");
    }

    /**
     * \u2605 The shipped numbers themselves (2026-09-30). The behavioural tests above build their own effect in Java, so a
     * json-only change to `amountPercent` slipped past them -- measured: `0.5 -> 0.25` came back with 0 red. This reads the
     * character\u2019s OWN compiled rules, which is exactly what the content file says.
     */
    @Test
    public void theShippedRulesCarryTheStatedNumbers() {
        Character elation = CharacterFactory.create(WEARER, LEVEL);
        int seen = 0;
        for (var rule : elation.getTriggerTable().rulesFor(
                com.laosun.aluminium.enums.TriggerEvent.BATTLE_START)) {
            if (!rule.id().startsWith("p1505_")) {
                continue;
            }
            for (var effect : rule.effects()) {
                seen++;
                System.out.println("[1505] spec " + rule.id() + " resource=" + effect.getResource()
                        + " amount=" + effect.getAmount() + " fromAttr=" + effect.getAmountFromAttr()
                        + " percent=" + effect.getAmountPercent());
                if (effect.getAmountFromAttr() != null) {
                    Assertions.assertEquals("CRIT_ATTACK", effect.getAmountFromAttr(),
                            "the talent derives the value from the crit-damage panel");
                    Assertions.assertEquals(0.5, effect.getAmountPercent(), 1e-9, "and takes half of it");
                } else if (Boolean.TRUE.equals(effect.getAmountFromEvent())) {
                    // \u2705 the third source (2026-09-30): \u300c\u83b7\u5f97\u80fd\u91cf\u65f6\u540c\u6b65\u83b7\u5f97\u7b49\u503c\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u300d -- the amount is the event\u2019s own
                    Assertions.assertEquals("\u597d\u6d3b\u5f53\u8d4f", effect.getResource(),
                            "the mirrored resource is \u3010\u597d\u6d3b\u5f53\u8d4f\u3011");
                } else {
                    Assertions.assertEquals(20.0, effect.getAmount(), 1e-9, "the technique grants twenty");
                }
            }
        }
        Assertions.assertEquals(2, seen, "BATTLE_START carries the derive and the technique");

        // \u2705 The third clause lives on a DIFFERENT event (2026-09-30): \u300c\u83b7\u5f97\u80fd\u91cf\u65f6\u540c\u6b65\u83b7\u5f97\u7b49\u503c\u7684\u3010\u597d\u6d3b\u5f53\u8d4f\u3011\u300d
        // is triggered by ENERGY_GAINED, so it is pinned by reading that event\u2019s rules -- the first version of this test counted
        // BATTLE_START only and read 2, which is exactly what made it obvious.
        var theSyncRule = elation.getTriggerTable().rulesFor(
                com.laosun.aluminium.enums.TriggerEvent.ENERGY_GAINED).stream()
                .filter(rule -> rule.id().startsWith("p1505_")).toList();
        Assertions.assertEquals(1, theSyncRule.size(), "the energy sync is wired to ENERGY_GAINED");
        var syncEffect = theSyncRule.getFirst().effects().getFirst();
        System.out.println("[1505] spec " + theSyncRule.getFirst().id() + " resource=" + syncEffect.getResource()
                + " amountFromEvent=" + syncEffect.getAmountFromEvent());
        Assertions.assertEquals(Boolean.TRUE, syncEffect.getAmountFromEvent(),
                "and its amount is the event\u2019s own magnitude");
    }
}
