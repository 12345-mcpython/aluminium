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
 * 1505 Evanescia (绯英), the two clauses her own text states completely (2026-09-30):
 * "绯英获得等同于暴击伤容 50% 的欢愉度" (Evanescia gains Elation equal to 50% of her CRIT DMG) and the technique's 20 [好活当赏].
 */
public class Character1505Test {
    private static final int WEARER = 1505;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;

    private static EffectSpec derive() {
        EffectSpec spec = new EffectSpec();
        TriggerSpecs.set(spec, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(spec, "resource", "欢愉度");
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
        int elationValue = elation.getResources().value("欢愉度");
        int gifts = elation.getResources().value("好活当赏");
        System.out.println("[1505] crit damage=" + critDamage + " -> 欢愉度 (Elation)=" + elationValue
                + " (half is " + Math.round(critDamage * 0.5) + ") ; 好活当赏 (gifts)=" + gifts);
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
        int elationValue = elation.getResources().value("欢愉度");
        System.out.println("[1505] with a raised panel: crit damage=" + critDamage + " -> 欢愉度 (Elation)="
                + elationValue + " (half is " + Math.round(critDamage * 0.5) + ")");
        Assertions.assertTrue(critDamage >= 1.4, "the panel really moved (it reads " + critDamage + ")");
        Assertions.assertEquals((int) Math.round(critDamage * 0.5), elationValue,
                "the talent still tracks the panel, and now the half is not zero");
    }

    /**
     * The shipped numbers themselves (2026-09-30). The behavioural tests above build their own effect in Java, so a
     * json-only change to `amountPercent` slipped past them -- measured: `0.5 -> 0.25` came back with 0 red. This reads the
     * character's OWN compiled rules, which is exactly what the content file says.
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
                    // the third source (2026-09-30): "获得能量时同步获得等值[好活当赏]" (gaining energy also gains an equal amount of [好活当赏]) -- the amount is the event's own
                    Assertions.assertEquals("好活当赏", effect.getResource(),
                            "the mirrored resource is 【好活当赏】 (gifts)");
                } else if ("DAMAGE".equals(effect.getOp())) {
                    // the technique's own damage (2026-09-30): a rule-driven ORDINARY instance, so it has no `amount` at all
                    Assertions.assertEquals(1.0, effect.getPercent(), 1e-9, "100% of her attack");
                    Assertions.assertEquals(Boolean.TRUE, effect.getOrdinary(), "as an ordinary hit");
                } else {
                    Assertions.assertEquals(20.0, effect.getAmount(), 1e-9, "the technique grants twenty");
                }
            }
        }
        Assertions.assertEquals(3, seen, "BATTLE_START carries the derive, the technique’s twenty and its damage");

        // The third clause lives on a DIFFERENT event (2026-09-30): "获得能量时同步获得等值的[好活当赏]" (gaining energy also gains an equal amount of [好活当赏])
        // is triggered by ENERGY_GAINED, so it is pinned by reading that event's rules -- the first version of this test counted
        // BATTLE_START only and read 2, which is exactly what made it obvious.
        var theSyncRule = elation.getTriggerTable().rulesFor(
                com.laosun.aluminium.enums.TriggerEvent.ENERGY_GAINED).stream()
                .filter(rule -> rule.id().endsWith("energy_sync")).toList();
        Assertions.assertEquals(1, theSyncRule.size(), "the energy sync is wired to ENERGY_GAINED");
        var syncEffect = theSyncRule.getFirst().effects().getFirst();
        System.out.println("[1505] spec " + theSyncRule.getFirst().id() + " resource=" + syncEffect.getResource()
                + " amountFromEvent=" + syncEffect.getAmountFromEvent());
        Assertions.assertEquals(Boolean.TRUE, syncEffect.getAmountFromEvent(),
                "and its amount is the event’s own magnitude");
    }
}
