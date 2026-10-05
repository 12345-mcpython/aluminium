package com.laosun.aluminium.test;

import com.laosun.aluminium.Battle;
import com.laosun.aluminium.beans.EffectSpec;
import com.laosun.aluminium.enums.AttributeType;
import com.laosun.aluminium.enums.TriggerEvent;
import com.laosun.aluminium.models.Character;
import com.laosun.aluminium.models.TriggerTable;
import com.laosun.aluminium.models.enemy.Enemy;
import com.laosun.aluminium.models.enemy.EnemyFactory;
import com.laosun.aluminium.utils.CharacterFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

/**
 * A resource gain whose amount is a SHARE of an attribute (2026-09-30).
 *
 * <p>Reader: 1505 绯英's talent, verbatim "绯英获得等同于暴击伤容 50% 的欢愉度". A `GAIN_RESOURCE` effect could
 * only add a literal before this, so that sentence had no spelling at all.
 */
public class AttributeShareResourceTest {
    private static final int WEARER = 1003;
    private static final int ALLY = 1002;
    private static final int LEVEL = 80;
    private static final int MONSTER = 1002011;
    private static final String RESOURCE = "充能";

    private Character wearer;
    private Enemy enemy;
    private Battle battle;

    private void build(EffectSpec... effects) {
        wearer = CharacterFactory.create(WEARER, LEVEL);
        enemy = EnemyFactory.create(MONSTER, 90, 1);
        battle = new Battle(List.of(wearer, CharacterFactory.create(ALLY, LEVEL)), List.of(enemy), new Random(0));
        battle.startBattle();
        if (effects.length > 0) {
            wearer.setTriggerTable(new TriggerTable(WEARER, List.of(
                    TriggerSpecs.rule("BATTLE_START", List.of(), effects))));
            battle.fireTriggers(TriggerEvent.BATTLE_START, wearer, null, 0, 0);
        }
    }

    private static EffectSpec shareOf(String attribute, Double percent) {
        EffectSpec spec = new EffectSpec();
        TriggerSpecs.set(spec, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(spec, "resource", RESOURCE);
        TriggerSpecs.set(spec, "amountFromAttr", attribute);
        if (percent != null) {
            TriggerSpecs.set(spec, "amountPercent", percent);
        }
        return spec;
    }

    /** The resource has a declared `initial` value, so every claim here is about the DELTA the event produced. */
    private int gained(Runnable action) {
        int before = wearer.getResources().value(RESOURCE);
        action.run();
        return wearer.getResources().value(RESOURCE) - before;
    }

    @Test
    public void theGainIsTheShareOfThePanel() {
        build();
        wearer.setTriggerTable(new TriggerTable(WEARER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), shareOf("CRIT_ATTACK", 0.5)))));
        double critDamage = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        int added = gained(() -> battle.fireTriggers(TriggerEvent.BATTLE_START, wearer, null, 0, 0));
        System.out.println("[share] crit damage=" + critDamage + " -> the gain was " + added
                + " (half of the panel is " + Math.round(critDamage * 0.5) + ")");
        Assertions.assertEquals((int) Math.round(critDamage * 0.5), added, "the gain is 50% of the panel value");
    }

    @Test
    public void aLiteralStillWorksAndAWholeValueIsTheDefault() {
        build();
        wearer.setTriggerTable(new TriggerTable(WEARER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), shareOf("CRIT_ATTACK", null)))));
        double critDamage = wearer.getAttribute(AttributeType.CRIT_ATTACK).get();
        int whole = gained(() -> battle.fireTriggers(TriggerEvent.BATTLE_START, wearer, null, 0, 0));
        System.out.println("[share] with no percent the gain is the whole value: " + whole + " (panel " + critDamage + ")");
        Assertions.assertEquals((int) Math.round(critDamage), whole, "no percent means the whole value");

        EffectSpec literal = new EffectSpec();
        TriggerSpecs.set(literal, "op", "GAIN_RESOURCE");
        TriggerSpecs.set(literal, "resource", RESOURCE);
        TriggerSpecs.set(literal, "amount", 1.0);
        build();
        wearer.setTriggerTable(new TriggerTable(WEARER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), literal))));
        int added = gained(() -> battle.fireTriggers(TriggerEvent.BATTLE_START, wearer, null, 0, 0));
        System.out.println("[share] a literal amount of 1 still adds 1 (the resource has a max, so a big literal would be capped): " + added);
        Assertions.assertEquals(1, added, "literals are untouched (false case)");
    }

    @Test
    public void anUnknownAttributeIsRefusedLoudly() {
        build();
        EffectSpec bad = shareOf("NOT_AN_ATTRIBUTE", 0.5);
        wearer.setTriggerTable(new TriggerTable(WEARER, List.of(
                TriggerSpecs.rule("BATTLE_START", List.of(), bad))));
        int added = gained(() -> {
            try {
                battle.fireTriggers(TriggerEvent.BATTLE_START, wearer, null, 0, 0);
            } catch (IllegalArgumentException expected) {
                System.out.println("[share] refused loudly: " + expected.getMessage());
                return;
            }
        });
        Assertions.assertEquals(0, added, "an unknown attribute adds nothing instead of guessing");
    }
}
